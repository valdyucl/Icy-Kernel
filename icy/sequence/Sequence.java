/*
 * Copyright 2010, 2011 Institut Pasteur.
 * 
 * This file is part of ICY.
 * 
 * ICY is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ICY is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ICY. If not, see <http://www.gnu.org/licenses/>.
 */
package icy.sequence;

import icy.common.EventHierarchicalChecker;
import icy.common.IcyChangedListener;
import icy.common.UpdateEventHandler;
import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImage;
import icy.image.IcyBufferedImageEvent;
import icy.image.IcyBufferedImageListener;
import icy.image.colormodel.IcyColorModel;
import icy.image.colormodel.IcyColorModelEvent;
import icy.image.colormodel.IcyColorModelListener;
import icy.image.lut.LUT;
import icy.main.Icy;
import icy.math.Scaler;
import icy.painter.Painter;
import icy.preferences.GeneralPreferences;
import icy.roi.ROI;
import icy.roi.ROI2D;
import icy.roi.ROI3D;
import icy.roi.ROIEvent;
import icy.roi.ROIListener;
import icy.sequence.SequenceEdit.ROIAdd;
import icy.sequence.SequenceEdit.ROIRemove;
import icy.sequence.SequenceEdit.ROIRemoveAll;
import icy.sequence.SequenceEvent.SequenceEventSourceType;
import icy.sequence.SequenceEvent.SequenceEventType;
import icy.system.thread.ThreadUtil;
import icy.type.DataType;
import icy.type.TypeUtil;
import icy.type.collection.array.Array1DUtil;
import icy.undo.IcyUndoManager;
import icy.util.StringUtil;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.swing.event.EventListenerList;

import org.w3c.dom.Node;

/**
 * @author Fabrice de Chaumont
 */
public class Sequence implements IcyColorModelListener, IcyBufferedImageListener, IcyChangedListener, ROIListener
{
    private static final String DEFAULT_NAME = "no name";

    /**
     * @deprecated
     */
    @Deprecated
    public static final int TYPE_BYTE = TypeUtil.TYPE_BYTE;
    /**
     * @deprecated
     */
    @Deprecated
    public static final int TYPE_DOUBLE = TypeUtil.TYPE_DOUBLE;
    /**
     * @deprecated
     */
    @Deprecated
    public static final int TYPE_FLOAT = TypeUtil.TYPE_FLOAT;
    /**
     * @deprecated
     */
    @Deprecated
    public static final int TYPE_INT = TypeUtil.TYPE_INT;
    /**
     * @deprecated
     */
    @Deprecated
    public static final int TYPE_SHORT = TypeUtil.TYPE_SHORT;
    /**
     * @deprecated
     */
    @Deprecated
    public static final int TYPE_UNDEFINED = TypeUtil.TYPE_UNDEFINED;

    public static final String ID_PIXEL_SIZE_X = "pixelSizeX";
    public static final String ID_PIXEL_SIZE_Y = "pixelSizeY";
    public static final String ID_PIXEL_SIZE_Z = "pixelSizeZ";
    public static final String ID_TIME_INTERVAL = "timeInterval";

    public static final String ID_CHANNEL_NAME = "channelName";

    public static final String DEFAULT_CHANNEL_NAME = "ch ";

    /**
     * id generator
     */
    private static int id_gen = 1;

    /**
     * volumetric images (4D [XYCZ])
     */
    private final TreeMap<Integer, VolumetricImage> volumetricImages;
    /**
     * painters
     */
    private final ArrayList<Painter> painters;
    /**
     * ROIs
     */
    private final ArrayList<ROI> ROIs;

    /**
     * id of sequence (uniq during an ICY session)
     */
    private final int id;
    /**
     * colorModel of sequence
     */
    private IcyColorModel colorModel;
    /**
     * name of sequence
     */
    private String name;
    /**
     * origin filename (from/to which the sequence has been loaded/saved)
     * null --> no file attachment
     * directory or metadata file --> multiples files attachment
     * image file --> single file attachment
     */
    private String filename;
    /**
     * X, Y, Z resolution (in mm)
     */
    private double pixelSizeX;
    private double pixelSizeY;
    private double pixelSizeZ;
    /**
     * T resolution (in ms)
     */
    private double timeInterval;
    /**
     * channels name
     */
    private String channelsName[];

    /**
     * automatic update of component absolute bounds
     */
    private boolean componentAbsBoundsAutoUpdate;
    /**
     * automatic update of component user bounds
     */
    private boolean componentUserBoundsAutoUpdate;
    /**
     * persistent object to load/save data (XML format)
     */
    private final SequencePersistent persistent;
    /**
     * undo manager
     */
    private final IcyUndoManager undoManager;

    /**
     * internal updater
     */
    private final UpdateEventHandler updater;
    /**
     * listeners
     */
    private final EventListenerList listeners;

    /**
     * internals
     */
    private boolean componentBoundsInvalid;

    /**
     * Create a new empty sequence
     */
    public Sequence()
    {
        super();

        // set id
        synchronized (Sequence.class)
        {
            id = id_gen;
            id_gen++;
        }

        name = DEFAULT_NAME;
        filename = null;
        pixelSizeX = 1d;
        pixelSizeY = 1d;
        pixelSizeZ = 1d;
        timeInterval = 1d;

        volumetricImages = new TreeMap<Integer, VolumetricImage>();
        painters = new ArrayList<Painter>();
        ROIs = new ArrayList<ROI>();
        persistent = new SequencePersistent(this);
        undoManager = new IcyUndoManager(this);

        updater = new UpdateEventHandler(this, false);
        // safety to dispatch on AWT for sequence
        // afaik this is not safe at all as dead lock can occurs very quickly
        // updater = new UpdateEventHandler(this, true);
        listeners = new EventListenerList();

        // empty by default
        channelsName = new String[0];
        // no colorModel yet
        colorModel = null;
        componentBoundsInvalid = false;
        // automatic update of component bounds
        componentAbsBoundsAutoUpdate = true;
        componentUserBoundsAutoUpdate = true;
    }

    /**
     * Create a sequence containing the specified image
     */
    public Sequence(IcyBufferedImage image)
    {
        this();

        addImage(image);
    }

    /**
     * Create a sequence with specified name and containing the specified image
     */
    public Sequence(String name, IcyBufferedImage image)
    {
        this();

        setName(name);
        addImage(image);
    }

    @Override
    protected void finalize() throws Throwable
    {
        final boolean hadRoi = !ROIs.isEmpty();
        final boolean hadPainter = !painters.isEmpty();

        synchronized (ROIs)
        {
            synchronized (painters)
            {
                // remove all listener on ROI
                for (ROI roi : ROIs)
                    roi.removeListener(this);

                ROIs.clear();
                painters.clear();
            }
        }

        // notify some painters has been removed
        if (hadRoi || hadPainter)
            painterChanged(null, SequenceEventType.REMOVED);
        // notify some rois has been removed
        if (hadRoi)
            roiChanged(null, SequenceEventType.REMOVED);

        super.finalize();
    }

    /**
     * This method close all attached viewers
     */
    public void close()
    {
        Icy.getMainInterface().closeViewersOfSequence(this);
    }

    /**
     * called when sequence has been closed (= all viewers displaying it closed)
     */
    public void closed()
    {
        // Sequence persistence enabled ?
        if (GeneralPreferences.getSequencePersistence())
        {
            // saving XML data can take sometime, do it in background...
            ThreadUtil.bgRun(new Runnable()
            {
                @Override
                public void run()
                {
                    // save XML
                    saveXMLData();
                }
            });
        }
        // notify close
        fireCloseEvent();
    }

    private void setColorModel(IcyColorModel cm)
    {
        // remove listener
        if (colorModel != null)
            colorModel.removeListener(this);

        colorModel = cm;

        // add listener
        if (cm != null)
            cm.addListener(this);

        // sequence type changed
        typeChanged();
        // sequence component bounds changed
        componentBoundsChanged(cm, -1);
        // sequence colormap changed
        colormapChanged(cm, -1);
    }

    /**
     * Return a new sequence with specified dataType from current sequence
     * 
     * @param dataType
     *        data type wanted
     * @param rescale
     *        indicate if we want to scale data value according to data type range
     * @return converted sequence
     */
    public Sequence convertToType(DataType dataType, boolean rescale)
    {
        final Sequence output = new Sequence();

        final double boundsSrc[] = getGlobalComponentAbsBounds();
        final double boundsDst[];

        if (rescale)
            boundsDst = dataType.getDefaultBounds();
        else
            boundsDst = boundsSrc;

        // use scaler to scale data
        final Scaler scaler = new Scaler(boundsSrc[0], boundsSrc[1], boundsDst[0], boundsDst[1], false);

        output.beginUpdate();
        try
        {
            for (int t = 0; t < getSizeT(); t++)
            {
                for (int z = 0; z < getSizeZ(); z++)
                {
                    final IcyBufferedImage converted = getImage(t, z).convertToType(dataType, scaler);

                    // FIXME : why we did that ??
                    // this is not a good idea to force bounds when rescale = false

                    // set bounds manually for the converted image
                    // for (int c = 0; c < getSizeC(); c++)
                    // {
                    // converted.setComponentBounds(c, boundsDst);
                    // converted.setComponentUserBounds(c, boundsDst);
                    // }

                    output.setImage(t, z, converted);
                }
            }

            output.setName(getName() + " (" + output.getDataType_() + " data type)");
        }
        finally
        {
            output.endUpdate();
        }

        return output;
    }

    /**
     * @deprecated use {@link #convertToType(DataType, boolean)} instead
     */
    @Deprecated
    public Sequence convertToType(int dataType, boolean signed, boolean rescale)
    {
        return convertToType(DataType.getDataType(dataType, signed), rescale);
    }

    /**
     * Build a new 1 channel sequence (grey) from the specified channel number
     * 
     * @param channelNumber
     * @return Sequence
     */
    public Sequence extractChannel(int channelNumber)
    {
        final ArrayList<Integer> list = new ArrayList<Integer>();

        list.add(Integer.valueOf(channelNumber));

        return extractChannels(list);
    }

    /**
     * Build a new sequence from the specified sequence channels
     * 
     * @param channelNumbers
     * @return Sequence
     */
    public Sequence extractChannels(List<Integer> channelNumbers)
    {
        final Sequence outSequence = new Sequence();

        for (int t = 0; t < getSizeT(); t++)
            for (int z = 0; z < getSizeZ(); z++)
                outSequence.setImage(t, z, getImage(t, z).extractChannels(channelNumbers));

        return outSequence;
    }

    /**
     * Use {@link #extractChannel(int)} instead
     * 
     * @param bandNumber
     * @return Sequence
     * @deprecated
     */
    @Deprecated
    public Sequence extractBand(int bandNumber)
    {
        return extractChannel(bandNumber);
    }

    /**
     * Same as {@link #extractChannels(List)}
     * 
     * @param bandNumbers
     * @return Sequence
     * @deprecated
     */
    @Deprecated
    public Sequence extractBands(List<Integer> bandNumbers)
    {
        return extractChannels(bandNumbers);
    }

    /**
     * Return all VolumetricImage as TreeMap (contains t position)
     */
    public TreeMap<Integer, VolumetricImage> getVolumetricImages()
    {
        synchronized (volumetricImages)
        {
            return new TreeMap<Integer, VolumetricImage>(volumetricImages);
        }
    }

    /**
     * Return all VolumetricImage
     */
    public ArrayList<VolumetricImage> getAllVolumetricImage()
    {
        synchronized (volumetricImages)
        {
            return new ArrayList<VolumetricImage>(volumetricImages.values());
        }
    }

    /**
     * Return first viewer attached to this sequence
     */
    public Viewer getFirstViewer()
    {
        return Icy.getMainInterface().getFirstViewer(this);
    }

    /**
     * Return viewers attached to this sequence
     */
    public ArrayList<Viewer> getViewers()
    {
        return Icy.getMainInterface().getViewers(this);
    }

    /**
     * get sequence id (this id is unique during an ICY session)
     */
    public int getId()
    {
        return id;
    }

    /**
     * Sequence name
     */
    public void setName(String name)
    {
        if (this.name != name)
        {
            this.name = name;
            nameChanged();
        }
    }

    public String getName()
    {
        return name;
    }

    /**
     * @return the filename
     */
    public String getFilename()
    {
        return filename;
    }

    /**
     * @param filename
     *        the filename to set
     */
    public void setFilename(String filename)
    {
        if (this.filename != filename)
        {
            this.filename = filename;
        }
    }

    /**
     * Return X pixel size (in mm)
     */
    public double getPixelSizeX()
    {
        return pixelSizeX;
    }

    /**
     * Return Y pixel size (in mm)
     */
    public double getPixelSizeY()
    {
        return pixelSizeY;
    }

    /**
     * Return Z pixel size (in mm)
     */
    public double getPixelSizeZ()
    {
        return pixelSizeZ;
    }

    /**
     * Return T time size (in ms)
     */
    public double getTimeInterval()
    {
        return timeInterval;
    }

    /**
     * Set X pixel size
     */
    public void setPixelSizeX(double value)
    {
        if (pixelSizeX != value)
        {
            pixelSizeX = value;
            metaChanged(ID_PIXEL_SIZE_X);
        }
    }

    /**
     * Set Y pixel size
     */
    public void setPixelSizeY(double value)
    {
        if (pixelSizeY != value)
        {
            pixelSizeY = value;
            metaChanged(ID_PIXEL_SIZE_Y);
        }
    }

    /**
     * Set Z pixel size
     */
    public void setPixelSizeZ(double value)
    {
        if (pixelSizeZ != value)
        {
            pixelSizeZ = value;
            metaChanged(ID_PIXEL_SIZE_Z);
        }
    }

    /**
     * Set T time resolution
     */
    public void setTimeInterval(double value)
    {
        if (timeInterval != value)
        {
            timeInterval = value;
            metaChanged(ID_TIME_INTERVAL);
        }
    }

    /**
     * Get name for specified channel
     */
    public String getChannelName(int index)
    {
        return channelsName[index];
    }

    /**
     * Set name for specified channel
     */
    public void setChannelName(int index, String value)
    {
        if (!StringUtil.equals(channelsName[index], value))
        {
            channelsName[index] = value;
            metaChanged(ID_CHANNEL_NAME, index);
        }
    }

    /**
     * @return the componentAbsBoundsAutoUpdate
     */
    public boolean isComponentAbsBoundsAutoUpdate()
    {
        return componentAbsBoundsAutoUpdate;
    }

    /**
     * @param componentAbsBoundsAutoUpdate
     *        the componentAbsBoundsAutoUpdate to set
     */
    public void setComponentAbsBoundsAutoUpdate(boolean componentAbsBoundsAutoUpdate)
    {
        this.componentAbsBoundsAutoUpdate = componentAbsBoundsAutoUpdate;
    }

    /**
     * @return the componentUserBoundsAutoUpdate
     */
    public boolean isComponentUserBoundsAutoUpdate()
    {
        return componentUserBoundsAutoUpdate;
    }

    /**
     * @param componentUserBoundsAutoUpdate
     *        the componentUserBoundsAutoUpdate to set
     */
    public void setComponentUserBoundsAutoUpdate(boolean componentUserBoundsAutoUpdate)
    {
        this.componentUserBoundsAutoUpdate = componentUserBoundsAutoUpdate;
    }

    /**
     * @return the AWT dispatching property
     * @deprecated Don't use it, events should stay on current thread
     */
    @Deprecated
    public boolean isAWTDispatching()
    {
        return updater.isAwtDispatch();
    }

    /**
     * All events are dispatched on AWT when true else they are dispatched on current thread
     * 
     * @deprecated Don't use it, events should stay on current thread
     */
    @Deprecated
    public void setAWTDispatching(boolean value)
    {
        updater.setAwtDispatch(value);
    }

    /**
     * Add the specified listener to listeners list
     */
    public void addListener(SequenceListener listener)
    {
        listeners.add(SequenceListener.class, listener);
    }

    /**
     * Remove the specified listener from listeners list
     */
    public void removeListener(SequenceListener listener)
    {
        listeners.remove(SequenceListener.class, listener);
    }

    /**
     * Get listeners list
     */
    public SequenceListener[] getListeners()
    {
        return listeners.getListeners(SequenceListener.class);
    }

    /**
     * Get the Undo manager of this sequence
     */
    public IcyUndoManager getUndoManager()
    {
        return undoManager;
    }

    public boolean contains(Painter painter)
    {
        synchronized (painters)
        {
            return painters.contains(painter);
        }
    }

    public boolean contains(ROI roi)
    {
        synchronized (ROIs)
        {
            return ROIs.contains(roi);
        }
    }

    public ArrayList<Painter> getPainters()
    {
        synchronized (painters)
        {
            return new ArrayList<Painter>(painters);
        }
    }

    public ArrayList<ROI> getROIs()
    {
        synchronized (ROIs)
        {
            return new ArrayList<ROI>(ROIs);
        }
    }

    public ArrayList<ROI2D> getROI2Ds()
    {
        final ArrayList<ROI2D> result = new ArrayList<ROI2D>();

        for (ROI roi : getROIs())
            if (roi instanceof ROI2D)
                result.add((ROI2D) roi);

        return result;
    }

    public ArrayList<ROI3D> getROI3Ds()
    {
        final ArrayList<ROI3D> result = new ArrayList<ROI3D>();

        for (ROI roi : getROIs())
            if (roi instanceof ROI3D)
                result.add((ROI3D) roi);

        return result;
    }

    public int getROICount(Class<? extends ROI> roiClass)
    {
        int result = 0;

        for (ROI roi : getROIs())
            if (roi.getClass().isAssignableFrom(roiClass))
                result++;

        return result;
    }

    /**
     * Return the first selected ROI (null if no ROI selected)
     */
    public ROI getSelectedROI()
    {
        for (ROI roi : getROIs())
            if (roi.isSelected())
                return roi;

        return null;
    }

    /**
     * Return all selected ROI
     */
    public ArrayList<ROI> getSelectedROIs()
    {
        final ArrayList<ROI> result = new ArrayList<ROI>();

        for (ROI roi : getROIs())
            if (roi.isSelected())
                result.add(roi);

        return result;
    }

    /**
     * Return the current focused ROI (null if no ROI focused)
     */
    public ROI getFocusedROI()
    {
        for (ROI roi : getROIs())
            if (roi.isFocused())
                return roi;

        return null;
    }

    /**
     * Set selected ROI (unselect all other if exclusive flag is true)
     */
    public boolean setSelectedROI(ROI roi, boolean exclusive)
    {
        // special case for global unselect
        if (roi == null)
        {
            setSelectedROIs(null);
            return true;
        }

        final ArrayList<ROI> listRoi = getROIs();

        beginUpdate();
        try
        {
            if (exclusive)
            {
                for (ROI currentRoi : listRoi)
                    if (currentRoi != roi)
                        currentRoi.internalUnselect();
            }

            if (listRoi.contains(roi))
            {
                roi.internalSelect();
                return true;
            }
        }
        finally
        {
            endUpdate();
        }

        return false;
    }

    /**
     * Set selected ROI (unselected all others)
     */
    public void setSelectedROIs(ArrayList<ROI> newSelected)
    {
        final ArrayList<ROI> oldSelected = getSelectedROIs();
        final int newSelectedSize;
        final int oldSelectedSize;

        if (newSelected == null)
            newSelectedSize = 0;
        else
            newSelectedSize = newSelected.size();
        if (oldSelected == null)
            oldSelectedSize = 0;
        else
            oldSelectedSize = oldSelected.size();

        // easy optimization
        if ((newSelectedSize == 0) && (oldSelectedSize == 0))
            return;

        // same selection, don't need to udpate it
        if ((newSelectedSize == oldSelectedSize) && newSelected.containsAll(oldSelected))
            return;

        beginUpdate();
        try
        {
            if (newSelectedSize > 0)
            {
                for (ROI roi : getROIs())
                    roi.setSelected(newSelected.contains(roi), false);
            }
            else
            {
                // unselected all ROIs
                for (ROI roi : getROIs())
                    roi.internalUnselect();
            }
        }
        finally
        {
            endUpdate();
        }
    }

    /**
     * Set the focused ROI
     */
    public boolean setFocusedROI(ROI roi)
    {
        final ArrayList<ROI> listRoi = getROIs();

        beginUpdate();
        try
        {
            for (ROI currentRoi : listRoi)
                if (currentRoi != roi)
                    currentRoi.internalUnfocus();

            if (listRoi.contains(roi))
            {
                roi.internalFocus();
                return true;
            }
        }
        finally
        {
            endUpdate();
        }

        return false;
    }

    /**
     * Return true if the sequence contains ROI of specified ROI class.
     */
    public boolean hasROI(Class<? extends ROI> roiClass)
    {
        for (ROI roi : getROIs())
            if (roi.getClass().isAssignableFrom(roiClass))
                return true;

        return false;
    }

    /**
     * Add the specified ROI to the sequence.
     * 
     * @param roi
     *        ROI to attach to the sequence
     */
    public boolean addROI(ROI roi)
    {
        return addROI(roi, false);
    }

    /**
     * Add the specified ROI to the sequence.
     * 
     * @param roi
     *        ROI to attach to the sequence
     * @param canUndo
     *        If true the action can be canceled by the undo manager.
     */
    public boolean addROI(ROI roi, boolean canUndo)
    {
        if (contains(roi))
            return false;

        synchronized (ROIs)
        {
            ROIs.add(roi);
        }
        // add listener to ROI
        roi.addListener(this);
        // notify roi added
        roiChanged(roi, SequenceEventType.ADDED);
        // then add ROI painter to sequence
        addPainter(roi.getPainter());

        if (canUndo)
            undoManager.addEdit(new ROIAdd(this, roi));

        return true;

    }

    /**
     * Remove the specified ROI from the sequence.
     * 
     * @param roi
     *        ROI to detach from the sequence
     */
    public boolean removeROI(ROI roi)
    {
        return removeROI(roi, false);
    }

    /**
     * Remove the specified ROI from the sequence.
     * 
     * @param roi
     *        ROI to detach from the sequence
     * @param canUndo
     *        If true the action can be canceled by the undo manager.
     */
    public boolean removeROI(ROI roi, boolean canUndo)
    {
        if (contains(roi))
        {
            // remove ROI painter first
            removePainter(roi.getPainter());
            // remove ROI
            synchronized (ROIs)
            {
                ROIs.remove(roi);
            }
            // remove listener
            roi.removeListener(this);
            // notify roi removed
            roiChanged(roi, SequenceEventType.REMOVED);

            if (canUndo)
                undoManager.addEdit(new ROIRemove(this, roi));

            return true;
        }

        return false;
    }

    /**
     * Remove all ROI from the sequence.
     */
    public void removeAllROI()
    {
        removeAllROI(false);
    }

    /**
     * Remove all ROI from the sequence.
     * 
     * @param canUndo
     *        If true the action can be canceled by the undo manager.
     */
    public void removeAllROI(boolean canUndo)
    {
        if (!ROIs.isEmpty())
        {
            final ArrayList<ROI> allROIs = getROIs();

            synchronized (painters)
            {
                // remove associated painters
                for (ROI roi : allROIs)
                    painters.remove(roi.getPainter());
            }

            // notify painters removed
            painterChanged(null, SequenceEventType.REMOVED);

            synchronized (ROIs)
            {
                // clear list
                ROIs.clear();
            }

            // remove listeners
            for (ROI roi : allROIs)
                roi.removeListener(this);

            // notify roi removed
            roiChanged(null, SequenceEventType.REMOVED);

            if (canUndo)
                undoManager.addEdit(new ROIRemoveAll(this, allROIs));
        }
    }

    /**
     * Add a painter to the sequence.<br>
     * Note: The painter sequence will not be refreshed until
     * you call the method sequence.painterChanged(...)
     */
    public boolean addPainter(Painter painter)
    {
        if (contains(painter))
            return false;

        synchronized (painters)
        {
            painters.add(painter);
        }

        // notify painter added
        painterChanged(painter, SequenceEventType.ADDED);

        return true;
    }

    /**
     * Remove a painter from the sequence.<br>
     * Note: The painter sequence will not be refreshed until
     * you call the method sequence.painterChanged(...)
     */
    public boolean removePainter(Painter painter)
    {
        if (contains(painter))
        {
            synchronized (painters)
            {
                painters.remove(painter);
            }

            // notify painter removed
            painterChanged(painter, SequenceEventType.REMOVED);

            return true;
        }

        return false;
    }

    /**
     * Return the VolumetricImage at position t
     */
    public VolumetricImage getVolumetricImage(int t)
    {
        synchronized (volumetricImages)
        {
            return volumetricImages.get(Integer.valueOf(t));
        }
    }

    /**
     * Return the first VolumetricImage
     */
    private VolumetricImage getFirstVolumetricImage()
    {
        final Entry<Integer, VolumetricImage> entry;

        synchronized (volumetricImages)
        {
            entry = volumetricImages.firstEntry();
        }

        if (entry != null)
            return entry.getValue();

        return null;
    }

    /**
     * Return the last VolumetricImage
     */
    private VolumetricImage getLastVolumetricImage()
    {
        final Entry<Integer, VolumetricImage> entry;

        synchronized (volumetricImages)
        {
            entry = volumetricImages.lastEntry();
        }

        if (entry != null)
            return entry.getValue();

        return null;
    }

    /**
     * Add an empty volumetricImage at last index + 1
     */
    public VolumetricImage addVolumetricImage()
    {
        return setVolumetricImage(getSizeT());
    }

    /**
     * Add an empty volumetricImage at t position
     */
    private VolumetricImage setVolumetricImage(int t)
    {
        // remove old volumetric image if any
        removeVolumetricImage(t);

        final VolumetricImage volImg = new VolumetricImage(this);

        synchronized (volumetricImages)
        {
            volumetricImages.put(new Integer(t), volImg);
        }

        return volImg;
    }

    /**
     * Add a volumetricImage at t position<br>
     * It actually create a new volumetricImage and add it to the sequence<br>
     * The new created volumetricImage is returned
     */
    public VolumetricImage addVolumetricImage(int t, VolumetricImage volImg)
    {
        if (volImg != null)
        {
            final VolumetricImage result;

            beginUpdate();
            try
            {
                // get new volumetric image (remove old one if any)
                result = setVolumetricImage(t);

                for (Entry<Integer, IcyBufferedImage> entry : volImg.getImages().entrySet())
                    setImage(t, entry.getKey().intValue(), entry.getValue());
            }
            finally
            {
                endUpdate();
            }

            return result;
        }

        return null;
    }

    /**
     * Remove volumetricImage at position t
     */
    public boolean removeVolumetricImage(int t)
    {
        final VolumetricImage volImg;

        synchronized (volumetricImages)
        {
            volImg = volumetricImages.remove(Integer.valueOf(t));
        }

        // we do manual clear to dispatch events correctly
        if (volImg != null)
            volImg.clear();

        return volImg != null;
    }

    /**
     * Remove all volumetricImage
     */
    private void removeAllVolumetricImage()
    {
        beginUpdate();
        try
        {
            synchronized (volumetricImages)
            {
                while (!volumetricImages.isEmpty())
                {
                    final VolumetricImage volImg = volumetricImages.pollFirstEntry().getValue();
                    // we do manual clear to dispatch events correctly
                    if (volImg != null)
                        volImg.clear();
                }
            }
        }
        finally
        {
            endUpdate();
        }
    }

    /**
     * Return the last image of VolumetricImage[t]
     */
    public IcyBufferedImage getLastImage(int t)
    {
        final VolumetricImage volImg = getVolumetricImage(t);

        if (volImg != null)
            return volImg.getLastImage();

        return null;
    }

    /**
     * Return the first image of first VolumetricImage
     */
    public IcyBufferedImage getFirstImage()
    {
        final VolumetricImage volImg = getFirstVolumetricImage();

        if (volImg != null)
            return volImg.getFirstImage();

        return null;
    }

    /**
     * Return the first non null image if exist
     */
    public IcyBufferedImage getFirstNonNullImage()
    {
        synchronized (volumetricImages)
        {
            for (VolumetricImage volImg : volumetricImages.values())
            {
                synchronized (volImg.images)
                {
                    for (IcyBufferedImage img : volImg.images.values())
                        if (img != null)
                            return img;
                }
            }
        }

        return null;
    }

    /**
     * Return the last image of last VolumetricImage
     */
    public IcyBufferedImage getLastImage()
    {
        final VolumetricImage volImg = getLastVolumetricImage();

        if (volImg != null)
            return volImg.getLastImage();

        return null;
    }

    /**
     * Return a single component image corresponding to the component c of the image
     * at time t and depth z.<br>
     * This actually create a new image which share its data with internal image
     * so any modifications to one affect the other.<br>
     * if <code>(c == -1)</code> then this method is equivalent to {@link #getImage(int, int)}<br>
     * if <code>((c == 0) || (sizeC == 1))</code> then this method is equivalent to
     * {@link #getImage(int, int)}<br>
     * if <code>((c < 0) || (c >= sizeC))</code> then it returns <code>null</code>
     * 
     * @see icy.image.IcyBufferedImage#extractChannel(int)
     * @since version 1.0.3.3b
     */
    public IcyBufferedImage getImage(int t, int z, int c)
    {
        final IcyBufferedImage src = getImage(t, z);

        if ((src == null) || (c == -1))
            return src;

        return src.getImage(c);
    }

    /**
     * Return image at time t and depth z
     */
    public IcyBufferedImage getImage(int t, int z)
    {
        final VolumetricImage volImg = getVolumetricImage(t);

        if (volImg != null)
            return volImg.getImage(z);

        return null;
    }

    /**
     * Return all images at specified t position
     */
    public ArrayList<IcyBufferedImage> getImages(int t)
    {
        final VolumetricImage volImg = getVolumetricImage(t);

        if (volImg != null)
            return volImg.getAllImage();

        return null;
    }

    /**
     * Return all images of sequence
     */
    public ArrayList<IcyBufferedImage> getAllImage()
    {
        final ArrayList<IcyBufferedImage> result = new ArrayList<IcyBufferedImage>();

        synchronized (volumetricImages)
        {
            for (VolumetricImage volImg : volumetricImages.values())
            {
                synchronized (volImg.images)
                {
                    result.addAll(volImg.images.values());
                }
            }
        }

        return result;
    }

    /**
     * Add an image to the specified VolumetricImage at the specified z location
     */
    private void setImage(VolumetricImage volImg, int z, BufferedImage image) throws IllegalArgumentException
    {
        if (volImg != null)
        {
            // not the same image ?
            if (volImg.getImage(z) != image)
            {
                // this is different from removeImage as we don't remove empty VolumetricImage
                if (image == null)
                    volImg.removeImage(z);
                else
                {
                    final IcyBufferedImage icyImg;

                    // convert to icyImage if needed
                    if (image instanceof IcyBufferedImage)
                        icyImg = (IcyBufferedImage) image;
                    else
                        icyImg = IcyBufferedImage.createFrom(image);

                    // possible type change ?
                    final boolean typeChange = (colorModel == null) || isEmpty()
                            || ((getNumImage() == 1) && (volImg.getImage(z) != null));

                    // not changing type and not compatible
                    if (!typeChange && !isCompatible(icyImg))
                        throw new IllegalArgumentException("Sequence.setImage : image is not compatible !");

                    // set image
                    volImg.setImage(z, icyImg);
                }
            }
        }
    }

    /**
     * Set an image at the specified position
     * 
     * @param t
     * @param z
     * @param image
     */
    public void setImage(int t, int z, BufferedImage image) throws IllegalArgumentException
    {
        final boolean volImgCreated;

        VolumetricImage volImg = getVolumetricImage(t);

        if (volImg == null)
        {
            volImg = setVolumetricImage(t);
            volImgCreated = true;
        }
        else
            volImgCreated = false;

        try
        {
            // set image
            setImage(volImg, z, image);
        }
        catch (IllegalArgumentException e)
        {
            // image set failed ? remove empty image list if needed
            if (volImgCreated)
                removeVolumetricImage(t);
            // throw exception
            throw e;
        }
    }

    /**
     * Add an image to the last VolumetricImage (create it if needed)
     * 
     * @param image
     *        image to add
     */
    public void addImage(BufferedImage image) throws IllegalArgumentException
    {
        final int t = Math.max(getSizeT() - 1, 0);
        final int z = Math.max(getSizeZ(t), 0);

        setImage(t, z, image);
    }

    /**
     * Add an image to the VolumetricImage[t] (the volumetricImage must exist)
     * 
     * @param image
     *        image to add
     */
    public void addImage(int t, BufferedImage image) throws IllegalArgumentException
    {
        final int z = Math.max(getSizeZ(t), 0);

        setImage(t, z, image);
    }

    /**
     * Remove the image at the specified position
     * 
     * @param t
     * @param z
     */
    public boolean removeImage(int t, int z)
    {
        final VolumetricImage volImg = getVolumetricImage(t);

        if (volImg != null)
        {
            final boolean result;

            beginUpdate();
            try
            {
                result = volImg.removeImage(z);

                // empty ?
                if (volImg.isEmpty())
                    // remove it
                    removeVolumetricImage(t);
            }
            finally
            {
                endUpdate();
            }

            return result;
        }

        return false;
    }

    /**
     * Remove all image at position t (same as removeVolumetricImage(t))
     */
    public boolean removeAllImage(int t)
    {
        return removeVolumetricImage(t);
    }

    /**
     * Remove all image (same as removeAllVolumetricImage)
     */
    public void removeAllImage()
    {
        removeAllVolumetricImage();
    }

    /**
     * Remove empty element of image list
     */
    public void packImageList()
    {
        beginUpdate();
        try
        {
            synchronized (volumetricImages)
            {
                for (Entry<Integer, VolumetricImage> entry : volumetricImages.entrySet())
                {
                    final VolumetricImage volImg = entry.getValue();
                    final int t = entry.getKey().intValue();

                    if (volImg == null)
                        removeVolumetricImage(t);
                    else
                    {
                        // pack the list
                        volImg.pack();
                        // empty ?
                        if (volImg.isEmpty())
                            // remove it
                            removeVolumetricImage(t);
                    }
                }
            }
        }
        finally
        {
            endUpdate();
        }
    }

    /**
     * return the number of loaded image
     */
    public int getNumImage()
    {
        int result = 0;

        synchronized (volumetricImages)
        {
            for (VolumetricImage volImg : volumetricImages.values())
                if (volImg != null)
                    result += volImg.getNumImage();
        }

        return result;
    }

    /**
     * return true if no image in sequence
     */
    public boolean isEmpty()
    {
        synchronized (volumetricImages)
        {
            for (VolumetricImage volImg : volumetricImages.values())
                if ((volImg != null) && (!volImg.isEmpty()))
                    return false;
        }

        return true;
    }

    /**
     * Return the number of volumetricImage in the sequence<br>
     * Use getSizeT instead
     * 
     * @see #getSizeT
     * @deprecated
     */
    @Deprecated
    public int getLength()
    {
        return getSizeT();
    }

    /**
     * return the number of volumetricImage in the sequence
     */
    public int getSizeT()
    {
        synchronized (volumetricImages)
        {
            if (volumetricImages.isEmpty())
                return 0;

            return volumetricImages.lastKey().intValue() + 1;
        }
    }

    /**
     * Return the global number of z stack in the sequence.
     * Use getSizeZ instead
     * 
     * @see #getSizeZ
     * @deprecated
     */
    @Deprecated
    public int getDepth()
    {
        return getSizeZ();
    }

    /**
     * Return the global number of z stack in the sequence.
     */
    public int getSizeZ()
    {
        final int sizeT = getSizeT();
        int maxZ = 0;

        for (int i = 0; i < sizeT; i++)
            maxZ = Math.max(maxZ, getSizeZ(i));

        return maxZ;
    }

    /**
     * Return the number of z stack of the volumetricImage[t].
     */
    public int getSizeZ(int t)
    {
        final VolumetricImage volImg = getVolumetricImage(t);

        if (volImg != null)
            return volImg.getSize();

        return 0;
    }

    /**
     * Return the number of component/channel/band per image.<br>
     * Use getSizeC instead
     * 
     * @see #getSizeC
     * @deprecated
     */
    @Deprecated
    public int getNumComponents()
    {
        return getSizeC();
    }

    /**
     * Return the number of component/channel/band per image
     */
    public int getSizeC()
    {
        if (colorModel != null)
            return colorModel.getNumComponents();

        return 0;
    }

    /**
     * Same as {@link #getSizeY()}
     */
    public int getHeight()
    {
        return getSizeY();
    }

    /**
     * Return the height of the sequence (0 if the sequence contains no image).
     */
    public int getSizeY()
    {
        final IcyBufferedImage img = getFirstNonNullImage();

        if (img != null)
            return img.getHeight();

        return 0;
    }

    /**
     * Same as {@link #getSizeX()}
     */
    public int getWidth()
    {
        return getSizeX();
    }

    /**
     * Return the width of the sequence (0 if the sequence contains no image).
     */
    public int getSizeX()
    {
        final IcyBufferedImage img = getFirstNonNullImage();

        if (img != null)
            return img.getWidth();

        return 0;
    }

    /**
     * Return 2D dimension of sequence {sizeX, sizeY}
     */
    public Dimension getDimension()
    {
        return new Dimension(getSizeX(), getSizeY());
    }

    /**
     * Return 2D bounds of sequence {0, 0, sizeX, sizeY}
     */
    public Rectangle getBounds()
    {
        return new Rectangle(getSizeX(), getSizeY());
    }

    /**
     * Test if the specified image is compatible with current loaded images in sequence
     */
    public boolean isCompatible(IcyBufferedImage image)
    {
        if ((colorModel == null) || isEmpty())
            return true;

        return (image.getWidth() == getWidth()) && (image.getHeight() == getHeight())
                && isCompatible(image.getIcyColorModel());
    }

    /**
     * Test if the specified colorModel is compatible with sequence colorModel
     */
    public boolean isCompatible(IcyColorModel cm)
    {
        // test that colorModel are compatible
        if (colorModel == null)
            return true;

        return colorModel.isCompatible(cm);
    }

    /**
     * Create a compatible LUT for this sequence
     */
    public LUT createCompatibleLUT()
    {
        IcyColorModel cm = colorModel;
        // not yet defined ? use default one
        if (colorModel == null)
            cm = IcyColorModel.createInstance();
        else
            cm = IcyColorModel.createInstance(colorModel, true, true);

        return new LUT(cm);
    }

    /**
     * Return true if specified LUT is compatible with sequence LUT
     */
    public boolean isLutCompatible(LUT lut)
    {
        IcyColorModel cm = colorModel;
        // not yet defined ? use default one
        if (cm == null)
            cm = IcyColorModel.createInstance();

        return lut.isCompatible(cm);
    }

    /**
     * Return the colorModel
     */
    public IcyColorModel getColorModel()
    {
        return colorModel;
    }

    /**
     * Return the data type of sequence
     */
    public DataType getDataType_()
    {
        if (colorModel == null)
            return DataType.UNDEFINED;

        return colorModel.getDataType_();
    }

    /**
     * Return the data type of sequence
     * 
     * @deprecated use {@link #getDataType_()} instead
     */
    @Deprecated
    public int getDataType()
    {
        if (colorModel == null)
            return TypeUtil.TYPE_UNDEFINED;

        return colorModel.getDataType();
    }

    /**
     * @deprecated use {@link #getDataType_()} instead
     */
    @Deprecated
    public boolean isFloatDataType()
    {
        return getDataType_().isFloat();
    }

    /**
     * @deprecated use {@link #getDataType_()} instead
     */
    @Deprecated
    public boolean isSignedDataType()
    {
        return getDataType_().isSigned();
    }

    private double[][] adjustBounds(double[][] curBounds, double[][] bounds)
    {
        if (bounds == null)
            return curBounds;

        for (int comp = 0; comp < bounds.length; comp++)
        {
            final double[] compBounds = bounds[comp];
            final double[] curCompBounds = curBounds[comp];

            if (curCompBounds[0] < compBounds[0])
                compBounds[0] = curCompBounds[0];
            if (curCompBounds[1] > compBounds[1])
                compBounds[1] = curCompBounds[1];
        }

        return bounds;
    }

    /**
     * Recalculate all image components bounds (min and max values)
     */
    private void recalculateAllImageComponentsBounds(boolean adjustByteToo)
    {
        // nothing to do...
        if ((colorModel == null) || isEmpty())
            return;

        final ArrayList<VolumetricImage> volumes = getAllVolumetricImage();

        beginUpdate();
        try
        {
            // recalculate images bounds (automatically update sequence bounds with event)
            for (VolumetricImage volImg : volumes)
                for (IcyBufferedImage img : volImg.getAllImage())
                    img.updateComponentsBounds(componentUserBoundsAutoUpdate, adjustByteToo);
        }
        finally
        {
            endUpdate();
        }
    }

    /**
     * Update components bounds (min and max values)<br>
     * At this point we assume images has correct components bounds information
     */
    private void internalUpdateComponentsBounds()
    {
        // nothing to do...
        if ((colorModel == null) || isEmpty())
            return;

        double[][] bounds;

        bounds = null;
        // recalculate bounds from all images
        synchronized (volumetricImages)
        {
            for (VolumetricImage volImg : volumetricImages.values())
            {
                synchronized (volImg.images)
                {
                    for (IcyBufferedImage img : volImg.images.values())
                        bounds = adjustBounds(img.getComponentsAbsBounds(), bounds);
                }
            }
        }

        // set new computed bounds
        colorModel.setComponentsAbsBounds(bounds);

        if (componentUserBoundsAutoUpdate)
        {
            bounds = null;
            // recalculate user bounds from all images
            synchronized (volumetricImages)
            {
                for (VolumetricImage volImg : volumetricImages.values())
                {
                    synchronized (volImg.images)
                    {
                        for (IcyBufferedImage img : volImg.images.values())
                            bounds = adjustBounds(img.getComponentsUserBounds(), bounds);
                    }
                }
            }

            // set new computed bounds
            colorModel.setComponentsUserBounds(bounds);
        }
    }

    /**
     * Update components bounds (min and max values)<br>
     * 
     * @param forceRecalculation
     *        If true all images components bounds will be recalculated<br>
     *        else we assume they are already updated<br>
     *        and only sequence components bounds are updated.
     * @param adjustByteToo
     *        If true bounds are adjusted even for byte sequence data type<br>
     *        else we force byte sequence data type to have their bounds fixed to [0..255] range
     */
    public void updateComponentsBounds(boolean forceRecalculation, boolean adjustByteToo)
    {
        // force calculation of all images bounds
        if (forceRecalculation)
            recalculateAllImageComponentsBounds(adjustByteToo);
        // then update sequence bounds
        internalUpdateComponentsBounds();
    }

    /**
     * Update components bounds (min and max values)<br>
     * 
     * @param forceRecalculation
     *        if true, all images components bounds will be recalculated<br>
     *        else we assume they are already updated<br>
     *        and only sequence components bounds are updated.
     */
    public void updateComponentsBounds(boolean forceRecalculation)
    {
        updateComponentsBounds(forceRecalculation, true);
    }

    /**
     * Update components bounds (min and max values)<br>
     * All images components bounds are recalculated.
     * 
     * @deprecated Use {@link #updateComponentsBounds(boolean, boolean)} instead
     */
    @Deprecated
    public void updateComponentsBounds()
    {
        // force recalculation
        updateComponentsBounds(true, true);
    }

    /**
     * Get component absolute minimum value
     */
    public double getComponentAbsMinValue(int component)
    {
        return colorModel.getComponentAbsMinValue(component);
    }

    /**
     * Get component absolute maximum value
     */
    public double getComponentAbsMaxValue(int component)
    {
        return colorModel.getComponentAbsMaxValue(component);
    }

    /**
     * Get component absolute bounds (min and max values)
     */
    public double[] getComponentAbsBounds(int component)
    {
        return colorModel.getComponentAbsBounds(component);
    }

    /**
     * Get components absolute bounds (min and max values)
     */
    public double[][] getComponentsAbsBounds()
    {
        final int sizeC = getSizeC();
        final double[][] result = new double[sizeC][];

        for (int c = 0; c < sizeC; c++)
            result[c] = getComponentAbsBounds(c);

        return result;
    }

    /**
     * Get global absolute bounds (min and max values for all components)
     */
    public double[] getGlobalComponentAbsBounds()
    {
        final int sizeC = getSizeC();
        final double[] result = getComponentAbsBounds(0);

        for (int c = 1; c < sizeC; c++)
        {
            final double[] bounds = getComponentAbsBounds(c);
            result[0] = Math.min(bounds[0], result[0]);
            result[1] = Math.max(bounds[1], result[1]);
        }

        return result;
    }

    /**
     * Get component user minimum value
     */
    public double getComponentUserMinValue(int component)
    {
        return colorModel.getComponentUserMinValue(component);
    }

    /**
     * Get component user maximum value
     */
    public double getComponentUserMaxValue(int component)
    {
        return colorModel.getComponentUserMaxValue(component);
    }

    /**
     * Get component user bounds (min and max values)
     */
    public double[] getComponentUserBounds(int component)
    {
        return colorModel.getComponentUserBounds(component);
    }

    /**
     * Get components user bounds (min and max values)
     */
    public double[][] getComponentsUserBounds()
    {
        final int c = getSizeC();
        final double[][] result = new double[c][];

        for (int component = 0; component < c; component++)
            result[component] = getComponentUserBounds(component);

        return result;
    }

    /**
     * Return a direct reference to 4D byte array data [T][Z][C][XY]
     */
    public Object getDataXYCZT()
    {
        switch (getDataType_().getJavaType())
        {
            case BYTE:
                return getDataXYCZTAsByte();
            case SHORT:
                return getDataXYCZTAsShort();
            case INT:
                return getDataXYCZTAsInt();
            case FLOAT:
                return getDataXYCZTAsFloat();
            case DOUBLE:
                return getDataXYCZTAsDouble();
            default:
                return null;
        }
    }

    /**
     * Return a direct reference to 3D byte array data [Z][C][XY] for specified t
     */
    public Object getDataXYCZ(int t)
    {
        switch (getDataType_().getJavaType())
        {
            case BYTE:
                return getDataXYCZAsByte(t);
            case SHORT:
                return getDataXYCZAsShort(t);
            case INT:
                return getDataXYCZAsInt(t);
            case FLOAT:
                return getDataXYCZAsFloat(t);
            case DOUBLE:
                return getDataXYCZAsDouble(t);
            default:
                return null;
        }
    }

    /**
     * Return a direct reference to 2D byte array data [C][XY] for specified t, z
     */
    public Object getDataXYC(int t, int z)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataXYC();

        return null;
    }

    /**
     * Return a direct reference to 1D byte array data [XY] for specified t, z, c
     */
    public Object getDataXY(int t, int z, int c)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataXY(c);

        return null;
    }

    /**
     * Return a direct reference to 3D byte array data [T][Z][XY] for specified c
     */
    public Object getDataXYZT(int c)
    {
        switch (getDataType_().getJavaType())
        {
            case BYTE:
                return getDataXYZTAsByte(c);
            case SHORT:
                return getDataXYZTAsShort(c);
            case INT:
                return getDataXYZTAsInt(c);
            case FLOAT:
                return getDataXYZTAsFloat(c);
            case DOUBLE:
                return getDataXYZTAsDouble(c);
            default:
                return null;
        }
    }

    /**
     * Return a direct reference to 2D byte array data [Z][XY] for specified t, c
     */
    public Object getDataXYZ(int t, int c)
    {
        switch (getDataType_().getJavaType())
        {
            case BYTE:
                return getDataXYZAsByte(t, c);
            case SHORT:
                return getDataXYZAsShort(t, c);
            case INT:
                return getDataXYZAsInt(t, c);
            case FLOAT:
                return getDataXYZAsFloat(t, c);
            case DOUBLE:
                return getDataXYZAsDouble(t, c);
            default:
                return null;
        }
    }

    /**
     * Return a 1D array data copy [XYCZT] of internal 4D array data [T][Z][C][XY]
     */
    public Object getDataCopyXYCZT()
    {
        return getDataCopyXYCZT(null, 0);
    }

    /**
     * Return a 1D array data copy [XYCZT] of internal 4D array data [T][Z][C][XY]<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public Object getDataCopyXYCZT(Object out, int off)
    {
        switch (getDataType_().getJavaType())
        {
            case BYTE:
                return getDataCopyXYCZTAsByte((byte[]) out, off);
            case SHORT:
                return getDataCopyXYCZTAsShort((short[]) out, off);
            case INT:
                return getDataCopyXYCZTAsInt((int[]) out, off);
            case FLOAT:
                return getDataCopyXYCZTAsFloat((float[]) out, off);
            case DOUBLE:
                return getDataCopyXYCZTAsDouble((double[]) out, off);
            default:
                return null;
        }
    }

    /**
     * Return a 1D array data copy [XYCZ] of internal 3D array data [Z][C][XY] for specified t
     */
    public Object getDataCopyXYCZ(int t)
    {
        return getDataCopyXYCZ(t, null, 0);
    }

    /**
     * Return a 1D array data copy [XYCZ] of internal 3D array data [Z][C][XY] for specified t<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public Object getDataCopyXYCZ(int t, Object out, int off)
    {
        switch (getDataType_().getJavaType())
        {
            case BYTE:
                return getDataCopyXYCZAsByte(t, (byte[]) out, off);
            case SHORT:
                return getDataCopyXYCZAsShort(t, (short[]) out, off);
            case INT:
                return getDataCopyXYCZAsInt(t, (int[]) out, off);
            case FLOAT:
                return getDataCopyXYCZAsFloat(t, (float[]) out, off);
            case DOUBLE:
                return getDataCopyXYCZAsDouble(t, (double[]) out, off);
            default:
                return null;
        }
    }

    /**
     * Return a 1D array data copy [XYC] of internal 2D array data [C][XY] for specified t, z
     */
    public Object getDataCopyXYC(int t, int z)
    {
        return getDataCopyXYC(t, z, null, 0);
    }

    /**
     * Return a 1D array data copy [XYC] of internal 2D array data [C][XY] for specified t, z<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public Object getDataCopyXYC(int t, int z, Object out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyXYC(out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [XY] of internal 1D array data [XY] for specified t, z, c
     */
    public Object getDataCopyXY(int t, int z, int c)
    {
        return getDataCopyXY(t, z, c, null, 0);
    }

    /**
     * Return a 1D array data copy [XY] of internal 1D array data [XY] for specified t, z, c<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public Object getDataCopyXY(int t, int z, int c, Object out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyXY(c, out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [CXYZT] of internal 4D array data [T][Z][C][XY]
     */
    public Object getDataCopyCXYZT()
    {
        return getDataCopyCXYZT(null, 0);
    }

    /**
     * Return a 1D array data copy [CXYZT] of internal 4D array data [T][Z][C][XY]<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public Object getDataCopyCXYZT(Object out, int off)
    {
        switch (getDataType_().getJavaType())
        {
            case BYTE:
                return getDataCopyCXYZTAsByte((byte[]) out, off);
            case SHORT:
                return getDataCopyCXYZTAsShort((short[]) out, off);
            case INT:
                return getDataCopyCXYZTAsInt((int[]) out, off);
            case FLOAT:
                return getDataCopyCXYZTAsFloat((float[]) out, off);
            case DOUBLE:
                return getDataCopyCXYZTAsDouble((double[]) out, off);
            default:
                return null;
        }
    }

    /**
     * Return a 1D array data copy [CXYZ] of internal 3D array data [Z][C][XY] for specified t
     */
    public Object getDataCopyCXYZ(int t)
    {
        return getDataCopyCXYZ(t, null, 0);
    }

    /**
     * Return a 1D array data copy [CXYZ] of internal 3D array data [Z][C][XY] for specified t<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public Object getDataCopyCXYZ(int t, Object out, int off)
    {
        switch (getDataType_().getJavaType())
        {
            case BYTE:
                return getDataCopyCXYZAsByte(t, (byte[]) out, off);
            case SHORT:
                return getDataCopyCXYZAsShort(t, (short[]) out, off);
            case INT:
                return getDataCopyCXYZAsInt(t, (int[]) out, off);
            case FLOAT:
                return getDataCopyCXYZAsFloat(t, (float[]) out, off);
            case DOUBLE:
                return getDataCopyCXYZAsDouble(t, (double[]) out, off);
            default:
                return null;
        }
    }

    /**
     * Return a 1D array data copy [CXY] of internal 2D array data [C][XY] for specified t, z
     */
    public Object getDataCopyCXY(int t, int z)
    {
        return getDataCopyCXY(t, z, null, 0);
    }

    /**
     * Return a 1D array data copy [CXY] of internal 2D array data [C][XY] for specified t, z<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public Object getDataCopyCXY(int t, int z, Object out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyCXY(out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [C] of specified t, z, x, y
     */
    public Object getDataCopyC(int t, int z, int x, int y)
    {
        return getDataCopyC(t, z, x, y, null, 0);
    }

    /**
     * Return a 1D array data copy [C] of specified t, z, x, y<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public Object getDataCopyC(int t, int z, int x, int y, Object out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyC(x, y, out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [XYZT] of internal 3D array data [T][Z][XY] for specified c
     */
    public Object getDataCopyXYZT(int c)
    {
        return getDataCopyXYZT(c, null, 0);
    }

    /**
     * Return a 1D array data copy [XYZT] of internal 3D array data [T][Z][XY] for specified c<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public Object getDataCopyXYZT(int c, Object out, int off)
    {
        switch (getDataType_().getJavaType())
        {
            case BYTE:
                return getDataCopyXYZTAsByte(c, (byte[]) out, off);
            case SHORT:
                return getDataCopyXYZTAsShort(c, (short[]) out, off);
            case INT:
                return getDataCopyXYZTAsInt(c, (int[]) out, off);
            case FLOAT:
                return getDataCopyXYZTAsFloat(c, (float[]) out, off);
            case DOUBLE:
                return getDataCopyXYZTAsDouble(c, (double[]) out, off);
            default:
                return null;
        }
    }

    /**
     * Return a 1D array data copy [XYZ] of internal 2D array data [Z][XY] for specified t, c
     */
    public Object getDataCopyXYZ(int t, int c)
    {
        return getDataCopyXYZ(t, c, null, 0);
    }

    /**
     * Return a 1D array data copy [XYZ] of internal 2D array data [Z][XY] for specified t, c<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public Object getDataCopyXYZ(int t, int c, Object out, int off)
    {
        switch (getDataType_().getJavaType())
        {
            case BYTE:
                return getDataCopyXYZAsByte(t, c, (byte[]) out, off);
            case SHORT:
                return getDataCopyXYZAsShort(t, c, (short[]) out, off);
            case INT:
                return getDataCopyXYZAsInt(t, c, (int[]) out, off);
            case FLOAT:
                return getDataCopyXYZAsFloat(t, c, (float[]) out, off);
            case DOUBLE:
                return getDataCopyXYZAsDouble(t, c, (double[]) out, off);
            default:
                return null;
        }
    }

    /**
     * Return a direct reference to 4D byte array data [T][Z][C][XY]
     */
    public byte[][][][] getDataXYCZTAsByte()
    {
        final int sizeT = getSizeT();
        final byte[][][][] result = new byte[sizeT][][][];

        for (int t = 0; t < sizeT; t++)
            result[t] = getDataXYCZAsByte(t);

        return result;

    }

    /**
     * Return a direct reference to 4D byte array data [T][Z][C][XY]
     */
    public short[][][][] getDataXYCZTAsShort()
    {
        final int sizeT = getSizeT();
        final short[][][][] result = new short[sizeT][][][];

        for (int t = 0; t < sizeT; t++)
            result[t] = getDataXYCZAsShort(t);

        return result;
    }

    /**
     * Return a direct reference to 4D byte array data [T][Z][C][XY]
     */
    public int[][][][] getDataXYCZTAsInt()
    {
        final int sizeT = getSizeT();
        final int[][][][] result = new int[sizeT][][][];

        for (int t = 0; t < sizeT; t++)
            result[t] = getDataXYCZAsInt(t);

        return result;
    }

    /**
     * Return a direct reference to 4D byte array data [T][Z][C][XY]
     */
    public float[][][][] getDataXYCZTAsFloat()
    {
        final int sizeT = getSizeT();
        final float[][][][] result = new float[sizeT][][][];

        for (int t = 0; t < sizeT; t++)
            result[t] = getDataXYCZAsFloat(t);

        return result;
    }

    /**
     * Return a direct reference to 4D byte array data [T][Z][C][XY]
     */
    public double[][][][] getDataXYCZTAsDouble()
    {
        final int sizeT = getSizeT();
        final double[][][][] result = new double[sizeT][][][];

        for (int t = 0; t < sizeT; t++)
            result[t] = getDataXYCZAsDouble(t);

        return result;
    }

    /**
     * Return a direct reference to 3D byte array data [Z][C][XY] for specified t
     */
    public byte[][][] getDataXYCZAsByte(int t)
    {
        final int sizeZ = getSizeZ(t);
        final byte[][][] result = new byte[sizeZ][][];

        for (int z = 0; z < sizeZ; z++)
            result[z] = getDataXYCAsByte(t, z);

        return result;
    }

    /**
     * Return a direct reference to 3D byte array data [Z][C][XY] for specified t
     */
    public short[][][] getDataXYCZAsShort(int t)
    {
        final int sizeZ = getSizeZ(t);
        final short[][][] result = new short[sizeZ][][];

        for (int z = 0; z < sizeZ; z++)
            result[z] = getDataXYCAsShort(t, z);

        return result;
    }

    /**
     * Return a direct reference to 3D byte array data [Z][C][XY] for specified t
     */
    public int[][][] getDataXYCZAsInt(int t)
    {
        final int sizeZ = getSizeZ(t);
        final int[][][] result = new int[sizeZ][][];

        for (int z = 0; z < sizeZ; z++)
            result[z] = getDataXYCAsInt(t, z);

        return result;
    }

    /**
     * Return a direct reference to 3D byte array data [Z][C][XY] for specified t
     */
    public float[][][] getDataXYCZAsFloat(int t)
    {
        final int sizeZ = getSizeZ(t);
        final float[][][] result = new float[sizeZ][][];

        for (int z = 0; z < sizeZ; z++)
            result[z] = getDataXYCAsFloat(t, z);

        return result;
    }

    /**
     * Return a direct reference to 3D byte array data [Z][C][XY] for specified t
     */
    public double[][][] getDataXYCZAsDouble(int t)
    {
        final int sizeZ = getSizeZ(t);
        final double[][][] result = new double[sizeZ][][];

        for (int z = 0; z < sizeZ; z++)
            result[z] = getDataXYCAsDouble(t, z);

        return result;
    }

    /**
     * Return a direct reference to 2D byte array data [C][XY] for specified t, z
     */
    public byte[][] getDataXYCAsByte(int t, int z)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataXYCAsByte();

        return null;
    }

    /**
     * Return a direct reference to 2D byte array data [C][XY] for specified t, z
     */
    public short[][] getDataXYCAsShort(int t, int z)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataXYCAsShort();

        return null;
    }

    /**
     * Return a direct reference to 2D byte array data [C][XY] for specified t, z
     */
    public int[][] getDataXYCAsInt(int t, int z)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataXYCAsInt();

        return null;
    }

    /**
     * Return a direct reference to 2D byte array data [C][XY] for specified t, z
     */
    public float[][] getDataXYCAsFloat(int t, int z)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataXYCAsFloat();

        return null;
    }

    /**
     * Return a direct reference to 2D byte array data [C][XY] for specified t, z
     */
    public double[][] getDataXYCAsDouble(int t, int z)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataXYCAsDouble();

        return null;
    }

    /**
     * Return a direct reference to 1D byte array data [XY] for specified t, z, c
     */
    public byte[] getDataXYAsByte(int t, int z, int c)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataXYAsByte(c);

        return null;
    }

    /**
     * Return a direct reference to 1D byte array data [XY] for specified t, z, c
     */
    public short[] getDataXYAsShort(int t, int z, int c)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataXYAsShort(c);

        return null;
    }

    /**
     * Return a direct reference to 1D byte array data [XY] for specified t, z, c
     */
    public int[] getDataXYAsInt(int t, int z, int c)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataXYAsInt(c);

        return null;
    }

    /**
     * Return a direct reference to 1D byte array data [XY] for specified t, z, c
     */
    public float[] getDataXYAsFloat(int t, int z, int c)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataXYAsFloat(c);

        return null;
    }

    /**
     * Return a direct reference to 1D byte array data [XY] for specified t, z, c
     */
    public double[] getDataXYAsDouble(int t, int z, int c)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataXYAsDouble(c);

        return null;
    }

    /**
     * Return a direct reference to 3D byte array data [T][Z][XY] for specified c
     */
    public byte[][][] getDataXYZTAsByte(int c)
    {
        final int sizeT = getSizeT();
        final byte[][][] result = new byte[sizeT][][];

        for (int t = 0; t < sizeT; t++)
            result[t] = getDataXYZAsByte(t, c);

        return result;
    }

    /**
     * Return a direct reference to 3D byte array data [T][Z][XY] for specified c
     */
    public short[][][] getDataXYZTAsShort(int c)
    {
        final int sizeT = getSizeT();
        final short[][][] result = new short[sizeT][][];

        for (int t = 0; t < sizeT; t++)
            result[t] = getDataXYZAsShort(t, c);

        return result;
    }

    /**
     * Return a direct reference to 3D byte array data [T][Z][XY] for specified c
     */
    public int[][][] getDataXYZTAsInt(int c)
    {
        final int sizeT = getSizeT();
        final int[][][] result = new int[sizeT][][];

        for (int t = 0; t < sizeT; t++)
            result[t] = getDataXYZAsInt(t, c);

        return result;
    }

    /**
     * Return a direct reference to 3D byte array data [T][Z][XY] for specified c
     */
    public float[][][] getDataXYZTAsFloat(int c)
    {
        final int sizeT = getSizeT();
        final float[][][] result = new float[sizeT][][];

        for (int t = 0; t < sizeT; t++)
            result[t] = getDataXYZAsFloat(t, c);

        return result;
    }

    /**
     * Return a direct reference to 3D byte array data [T][Z][XY] for specified c
     */
    public double[][][] getDataXYZTAsDouble(int c)
    {
        final int sizeT = getSizeT();
        final double[][][] result = new double[sizeT][][];

        for (int t = 0; t < sizeT; t++)
            result[t] = getDataXYZAsDouble(t, c);

        return result;
    }

    /**
     * Return a direct reference to 2D byte array data [Z][XY] for specified t, c
     */
    public byte[][] getDataXYZAsByte(int t, int c)
    {
        final int sizeZ = getSizeZ(t);
        final byte[][] result = new byte[sizeZ][];

        for (int z = 0; z < sizeZ; z++)
            result[z] = getDataXYAsByte(t, z, c);

        return result;
    }

    /**
     * Return a direct reference to 2D byte array data [Z][XY] for specified t, c
     */
    public short[][] getDataXYZAsShort(int t, int c)
    {
        final int sizeZ = getSizeZ(t);
        final short[][] result = new short[sizeZ][];

        for (int z = 0; z < sizeZ; z++)
            result[z] = getDataXYAsShort(t, z, c);

        return result;
    }

    /**
     * Return a direct reference to 2D byte array data [Z][XY] for specified t, c
     */
    public int[][] getDataXYZAsInt(int t, int c)
    {
        final int sizeZ = getSizeZ(t);
        final int[][] result = new int[sizeZ][];

        for (int z = 0; z < sizeZ; z++)
            result[z] = getDataXYAsInt(t, z, c);

        return result;
    }

    /**
     * Return a direct reference to 2D byte array data [Z][XY] for specified t, c
     */
    public float[][] getDataXYZAsFloat(int t, int c)
    {
        final int sizeZ = getSizeZ(t);
        final float[][] result = new float[sizeZ][];

        for (int z = 0; z < sizeZ; z++)
            result[z] = getDataXYAsFloat(t, z, c);

        return result;
    }

    /**
     * Return a direct reference to 2D byte array data [Z][XY] for specified t, c
     */
    public double[][] getDataXYZAsDouble(int t, int c)
    {
        final int sizeZ = getSizeZ(t);
        final double[][] result = new double[sizeZ][];

        for (int z = 0; z < sizeZ; z++)
            result[z] = getDataXYAsDouble(t, z, c);

        return result;
    }

    /**
     * Return a 1D array data copy [XYCZT] of internal 4D array data [T][Z][C][XY]
     */
    public byte[] getDataCopyXYCZTAsByte()
    {
        return getDataCopyXYCZTAsByte(null, 0);
    }

    /**
     * Return a 1D array data copy [XYCZT] of internal 4D array data [T][Z][C][XY]<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public byte[] getDataCopyXYCZTAsByte(byte[] out, int off)
    {
        final int sizeT = getSizeT();
        final int len = getSizeX() * getSizeY() * getSizeC() * getSizeZ();
        final byte[] result = Array1DUtil.allocIfNull(out, len * sizeT);
        int offset = off;

        for (int t = 0; t < sizeT; t++)
        {
            getDataCopyXYCZAsByte(t, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [XYCZT] of internal 4D array data [T][Z][C][XY]
     */
    public short[] getDataCopyXYCZTAsShort()
    {
        return getDataCopyXYCZTAsShort(null, 0);
    }

    /**
     * Return a 1D array data copy [XYCZT] of internal 4D array data [T][Z][C][XY]<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public short[] getDataCopyXYCZTAsShort(short[] out, int off)
    {
        final int sizeT = getSizeT();
        final int len = getSizeX() * getSizeY() * getSizeC() * getSizeZ();
        final short[] result = Array1DUtil.allocIfNull(out, len * sizeT);
        int offset = off;

        for (int t = 0; t < sizeT; t++)
        {
            getDataCopyXYCZAsShort(t, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [XYCZT] of internal 4D array data [T][Z][C][XY]
     */
    public int[] getDataCopyXYCZTAsInt()
    {
        return getDataCopyXYCZTAsInt(null, 0);
    }

    /**
     * Return a 1D array data copy [XYCZT] of internal 4D array data [T][Z][C][XY]<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public int[] getDataCopyXYCZTAsInt(int[] out, int off)
    {
        final int sizeT = getSizeT();
        final int len = getSizeX() * getSizeY() * getSizeC() * getSizeZ();
        final int[] result = Array1DUtil.allocIfNull(out, len * sizeT);
        int offset = off;

        for (int t = 0; t < sizeT; t++)
        {
            getDataCopyXYCZAsInt(t, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [XYCZT] of internal 4D array data [T][Z][C][XY]
     */
    public float[] getDataCopyXYCZTAsFloat()
    {
        return getDataCopyXYCZTAsFloat(null, 0);
    }

    /**
     * Return a 1D array data copy [XYCZT] of internal 4D array data [T][Z][C][XY]<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public float[] getDataCopyXYCZTAsFloat(float[] out, int off)
    {
        final int sizeT = getSizeT();
        final int len = getSizeX() * getSizeY() * getSizeC() * getSizeZ();
        final float[] result = Array1DUtil.allocIfNull(out, len * sizeT);
        int offset = off;

        for (int t = 0; t < sizeT; t++)
        {
            getDataCopyXYCZAsFloat(t, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [XYCZT] of internal 4D array data [T][Z][C][XY]
     */
    public double[] getDataCopyXYCZTAsDouble()
    {
        return getDataCopyXYCZTAsDouble(null, 0);
    }

    /**
     * Return a 1D array data copy [XYCZT] of internal 4D array data [T][Z][C][XY]<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public double[] getDataCopyXYCZTAsDouble(double[] out, int off)
    {
        final int sizeT = getSizeT();
        final int len = getSizeX() * getSizeY() * getSizeC() * getSizeZ();
        final double[] result = Array1DUtil.allocIfNull(out, len * sizeT);
        int offset = off;

        for (int t = 0; t < sizeT; t++)
        {
            getDataCopyXYCZAsDouble(t, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [XYCZ] of internal 3D array data [Z][C][XY] for specified t
     */
    public byte[] getDataCopyXYCZAsByte(int t)
    {
        return getDataCopyXYCZAsByte(t, null, 0);
    }

    /**
     * Return a 1D array data copy [XYCZ] of internal 3D array data [Z][C][XY] for specified t<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public byte[] getDataCopyXYCZAsByte(int t, byte[] out, int off)
    {
        final int sizeZ = getSizeZ();
        final int len = getSizeX() * getSizeY() * getSizeC();
        final byte[] result = Array1DUtil.allocIfNull(out, len * sizeZ);
        int offset = off;

        for (int z = 0; z < sizeZ; z++)
        {
            getDataCopyXYCAsByte(t, z, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [XYCZ] of internal 3D array data [Z][C][XY] for specified t
     */
    public short[] getDataCopyXYCZAsShort(int t)
    {
        return getDataCopyXYCZAsShort(t, null, 0);
    }

    /**
     * Return a 1D array data copy [XYCZ] of internal 3D array data [Z][C][XY] for specified t<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public short[] getDataCopyXYCZAsShort(int t, short[] out, int off)
    {
        final int sizeZ = getSizeZ();
        final int len = getSizeX() * getSizeY() * getSizeC();
        final short[] result = Array1DUtil.allocIfNull(out, len * sizeZ);
        int offset = off;

        for (int z = 0; z < sizeZ; z++)
        {
            getDataCopyXYCAsShort(t, z, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [XYCZ] of internal 3D array data [Z][C][XY] for specified t
     */
    public int[] getDataCopyXYCZAsInt(int t)
    {
        return getDataCopyXYCZAsInt(t, null, 0);
    }

    /**
     * Return a 1D array data copy [XYCZ] of internal 3D array data [Z][C][XY] for specified t<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public int[] getDataCopyXYCZAsInt(int t, int[] out, int off)
    {
        final int sizeZ = getSizeZ();
        final int len = getSizeX() * getSizeY() * getSizeC();
        final int[] result = Array1DUtil.allocIfNull(out, len * sizeZ);
        int offset = off;

        for (int z = 0; z < sizeZ; z++)
        {
            getDataCopyXYCAsInt(t, z, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [XYCZ] of internal 3D array data [Z][C][XY] for specified t
     */
    public float[] getDataCopyXYCZAsFloat(int t)
    {
        return getDataCopyXYCZAsFloat(t, null, 0);
    }

    /**
     * Return a 1D array data copy [XYCZ] of internal 3D array data [Z][C][XY] for specified t<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public float[] getDataCopyXYCZAsFloat(int t, float[] out, int off)
    {
        final int sizeZ = getSizeZ();
        final int len = getSizeX() * getSizeY() * getSizeC();
        final float[] result = Array1DUtil.allocIfNull(out, len * sizeZ);
        int offset = off;

        for (int z = 0; z < sizeZ; z++)
        {
            getDataCopyXYCAsFloat(t, z, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [XYCZ] of internal 3D array data [Z][C][XY] for specified t
     */
    public double[] getDataCopyXYCZAsDouble(int t)
    {
        return getDataCopyXYCZAsDouble(t, null, 0);
    }

    /**
     * Return a 1D array data copy [XYCZ] of internal 3D array data [Z][C][XY] for specified t<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public double[] getDataCopyXYCZAsDouble(int t, double[] out, int off)
    {
        final int sizeZ = getSizeZ();
        final int len = getSizeX() * getSizeY() * getSizeC();
        final double[] result = Array1DUtil.allocIfNull(out, len * sizeZ);
        int offset = off;

        for (int z = 0; z < sizeZ; z++)
        {
            getDataCopyXYCAsDouble(t, z, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [XYC] of internal 2D array data [C][XY] for specified t, z
     */
    public byte[] getDataCopyXYCAsByte(int t, int z)
    {
        return getDataCopyXYCAsByte(t, z, null, 0);
    }

    /**
     * Return a 1D array data copy [XYC] of internal 2D array data [C][XY] for specified t, z<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public byte[] getDataCopyXYCAsByte(int t, int z, byte[] out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyXYCAsByte(out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [XYC] of internal 2D array data [C][XY] for specified t, z
     */
    public short[] getDataCopyXYCAsShort(int t, int z)
    {
        return getDataCopyXYCAsShort(t, z, null, 0);
    }

    /**
     * Return a 1D array data copy [XYC] of internal 2D array data [C][XY] for specified t, z<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public short[] getDataCopyXYCAsShort(int t, int z, short[] out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyXYCAsShort(out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [XYC] of internal 2D array data [C][XY] for specified t, z
     */
    public int[] getDataCopyXYCAsInt(int t, int z)
    {
        return getDataCopyXYCAsInt(t, z, null, 0);
    }

    /**
     * Return a 1D array data copy [XYC] of internal 2D array data [C][XY] for specified t, z<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public int[] getDataCopyXYCAsInt(int t, int z, int[] out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyXYCAsInt(out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [XYC] of internal 2D array data [C][XY] for specified t, z
     */
    public float[] getDataCopyXYCAsFloat(int t, int z)
    {
        return getDataCopyXYCAsFloat(t, z, null, 0);
    }

    /**
     * Return a 1D array data copy [XYC] of internal 2D array data [C][XY] for specified t, z<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public float[] getDataCopyXYCAsFloat(int t, int z, float[] out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyXYCAsFloat(out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [XYC] of internal 2D array data [C][XY] for specified t, z
     */
    public double[] getDataCopyXYCAsDouble(int t, int z)
    {
        return getDataCopyXYCAsDouble(t, z, null, 0);
    }

    /**
     * Return a 1D array data copy [XYC] of internal 2D array data [C][XY] for specified t, z<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public double[] getDataCopyXYCAsDouble(int t, int z, double[] out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyXYCAsDouble(out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [XY] of internal 1D array data [XY] for specified t, z, c
     */
    public byte[] getDataCopyXYAsByte(int t, int z, int c)
    {
        return getDataCopyXYAsByte(t, z, c, null, 0);
    }

    /**
     * Return a 1D array data copy [XY] of internal 1D array data [XY] for specified t, z, c<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public byte[] getDataCopyXYAsByte(int t, int z, int c, byte[] out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyXYAsByte(c, out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [XY] of internal 1D array data [XY] for specified t, z, c
     */
    public short[] getDataCopyXYAsShort(int t, int z, int c)
    {
        return getDataCopyXYAsShort(t, z, c, null, 0);
    }

    /**
     * Return a 1D array data copy [XY] of internal 1D array data [XY] for specified t, z, c<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public short[] getDataCopyXYAsShort(int t, int z, int c, short[] out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyXYAsShort(c, out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [XY] of internal 1D array data [XY] for specified t, z, c
     */
    public int[] getDataCopyXYAsInt(int t, int z, int c)
    {
        return getDataCopyXYAsInt(t, z, c, null, 0);
    }

    /**
     * Return a 1D array data copy [XY] of internal 1D array data [XY] for specified t, z, c<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public int[] getDataCopyXYAsInt(int t, int z, int c, int[] out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyXYAsInt(c, out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [XY] of internal 1D array data [XY] for specified t, z, c
     */
    public float[] getDataCopyXYAsFloat(int t, int z, int c)
    {
        return getDataCopyXYAsFloat(t, z, c, null, 0);
    }

    /**
     * Return a 1D array data copy [XY] of internal 1D array data [XY] for specified t, z, c<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public float[] getDataCopyXYAsFloat(int t, int z, int c, float[] out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyXYAsFloat(c, out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [XY] of internal 1D array data [XY] for specified t, z, c
     */
    public double[] getDataCopyXYAsDouble(int t, int z, int c)
    {
        return getDataCopyXYAsDouble(t, z, c, null, 0);
    }

    /**
     * Return a 1D array data copy [XY] of internal 1D array data [XY] for specified t, z, c<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public double[] getDataCopyXYAsDouble(int t, int z, int c, double[] out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyXYAsDouble(c, out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [CXYZT] of internal 4D array data [T][Z][C][XY]
     */
    public byte[] getDataCopyCXYZTAsByte()
    {
        return getDataCopyCXYZTAsByte(null, 0);
    }

    /**
     * Return a 1D array data copy [CXYZT] of internal 4D array data [T][Z][C][XY]<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public byte[] getDataCopyCXYZTAsByte(byte[] out, int off)
    {
        final int sizeT = getSizeT();
        final int len = getSizeX() * getSizeY() * getSizeC() * getSizeZ();
        final byte[] result = Array1DUtil.allocIfNull(out, len * sizeT);
        int offset = off;

        for (int t = 0; t < sizeT; t++)
        {
            getDataCopyCXYZAsByte(t, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [CXYZT] of internal 4D array data [T][Z][C][XY]
     */
    public short[] getDataCopyCXYZTAsShort()
    {
        return getDataCopyCXYZTAsShort(null, 0);
    }

    /**
     * Return a 1D array data copy [CXYZT] of internal 4D array data [T][Z][C][XY]<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public short[] getDataCopyCXYZTAsShort(short[] out, int off)
    {
        final int sizeT = getSizeT();
        final int len = getSizeX() * getSizeY() * getSizeC() * getSizeZ();
        final short[] result = Array1DUtil.allocIfNull(out, len * sizeT);
        int offset = off;

        for (int t = 0; t < sizeT; t++)
        {
            getDataCopyCXYZAsShort(t, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [CXYZT] of internal 4D array data [T][Z][C][XY]
     */
    public int[] getDataCopyCXYZTAsInt()
    {
        return getDataCopyCXYZTAsInt(null, 0);
    }

    /**
     * Return a 1D array data copy [CXYZT] of internal 4D array data [T][Z][C][XY]<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public int[] getDataCopyCXYZTAsInt(int[] out, int off)
    {
        final int sizeT = getSizeT();
        final int len = getSizeX() * getSizeY() * getSizeC() * getSizeZ();
        final int[] result = Array1DUtil.allocIfNull(out, len * sizeT);
        int offset = off;

        for (int t = 0; t < sizeT; t++)
        {
            getDataCopyCXYZAsInt(t, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [CXYZT] of internal 4D array data [T][Z][C][XY]
     */
    public float[] getDataCopyCXYZTAsFloat()
    {
        return getDataCopyCXYZTAsFloat(null, 0);
    }

    /**
     * Return a 1D array data copy [CXYZT] of internal 4D array data [T][Z][C][XY]<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public float[] getDataCopyCXYZTAsFloat(float[] out, int off)
    {
        final int sizeT = getSizeT();
        final int len = getSizeX() * getSizeY() * getSizeC() * getSizeZ();
        final float[] result = Array1DUtil.allocIfNull(out, len * sizeT);
        int offset = off;

        for (int t = 0; t < sizeT; t++)
        {
            getDataCopyCXYZAsFloat(t, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [CXYZT] of internal 4D array data [T][Z][C][XY]
     */
    public double[] getDataCopyCXYZTAsDouble()
    {
        return getDataCopyCXYZTAsDouble(null, 0);
    }

    /**
     * Return a 1D array data copy [CXYZT] of internal 4D array data [T][Z][C][XY]<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public double[] getDataCopyCXYZTAsDouble(double[] out, int off)
    {
        final int sizeT = getSizeT();
        final int len = getSizeX() * getSizeY() * getSizeC() * getSizeZ();
        final double[] result = Array1DUtil.allocIfNull(out, len * sizeT);
        int offset = off;

        for (int t = 0; t < sizeT; t++)
        {
            getDataCopyCXYZAsDouble(t, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [CXYZ] of internal 3D array data [Z][C][XY] for specified t
     */
    public byte[] getDataCopyCXYZAsByte(int t)
    {
        return getDataCopyCXYZAsByte(t, null, 0);
    }

    /**
     * Return a 1D array data copy [CXYZ] of internal 3D array data [Z][C][XY] for specified t<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public byte[] getDataCopyCXYZAsByte(int t, byte[] out, int off)
    {
        final int sizeZ = getSizeZ();
        final int len = getSizeX() * getSizeY() * getSizeC();
        final byte[] result = Array1DUtil.allocIfNull(out, len * sizeZ);
        int offset = off;

        for (int z = 0; z < sizeZ; z++)
        {
            getDataCopyCXYAsByte(t, z, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [CXYZ] of internal 3D array data [Z][C][XY] for specified t
     */
    public short[] getDataCopyCXYZAsShort(int t)
    {
        return getDataCopyCXYZAsShort(t, null, 0);
    }

    /**
     * Return a 1D array data copy [CXYZ] of internal 3D array data [Z][C][XY] for specified t<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public short[] getDataCopyCXYZAsShort(int t, short[] out, int off)
    {
        final int sizeZ = getSizeZ();
        final int len = getSizeX() * getSizeY() * getSizeC();
        final short[] result = Array1DUtil.allocIfNull(out, len * sizeZ);
        int offset = off;

        for (int z = 0; z < sizeZ; z++)
        {
            getDataCopyCXYAsShort(t, z, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [CXYZ] of internal 3D array data [Z][C][XY] for specified t
     */
    public int[] getDataCopyCXYZAsInt(int t)
    {
        return getDataCopyCXYZAsInt(t, null, 0);
    }

    /**
     * Return a 1D array data copy [CXYZ] of internal 3D array data [Z][C][XY] for specified t<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public int[] getDataCopyCXYZAsInt(int t, int[] out, int off)
    {
        final int sizeZ = getSizeZ();
        final int len = getSizeX() * getSizeY() * getSizeC();
        final int[] result = Array1DUtil.allocIfNull(out, len * sizeZ);
        int offset = off;

        for (int z = 0; z < sizeZ; z++)
        {
            getDataCopyCXYAsInt(t, z, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [CXYZ] of internal 3D array data [Z][C][XY] for specified t
     */
    public float[] getDataCopyCXYZAsFloat(int t)
    {
        return getDataCopyCXYZAsFloat(t, null, 0);
    }

    /**
     * Return a 1D array data copy [CXYZ] of internal 3D array data [Z][C][XY] for specified t<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public float[] getDataCopyCXYZAsFloat(int t, float[] out, int off)
    {
        final int sizeZ = getSizeZ();
        final int len = getSizeX() * getSizeY() * getSizeC();
        final float[] result = Array1DUtil.allocIfNull(out, len * sizeZ);
        int offset = off;

        for (int z = 0; z < sizeZ; z++)
        {
            getDataCopyCXYAsFloat(t, z, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [CXYZ] of internal 3D array data [Z][C][XY] for specified t
     */
    public double[] getDataCopyCXYZAsDouble(int t)
    {
        return getDataCopyCXYZAsDouble(t, null, 0);
    }

    /**
     * Return a 1D array data copy [CXYZ] of internal 3D array data [Z][C][XY] for specified t<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public double[] getDataCopyCXYZAsDouble(int t, double[] out, int off)
    {
        final int sizeZ = getSizeZ();
        final int len = getSizeX() * getSizeY() * getSizeC();
        final double[] result = Array1DUtil.allocIfNull(out, len * sizeZ);
        int offset = off;

        for (int z = 0; z < sizeZ; z++)
        {
            getDataCopyCXYAsDouble(t, z, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [CXY] of internal 2D array data [C][XY] for specified t, z
     */
    public byte[] getDataCopyCXYAsByte(int t, int z)
    {
        return getDataCopyCXYAsByte(t, z, null, 0);
    }

    /**
     * Return a 1D array data copy [CXY] of internal 2D array data [C][XY] for specified t, z<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public byte[] getDataCopyCXYAsByte(int t, int z, byte[] out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyCXYAsByte(out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [CXY] of internal 2D array data [C][XY] for specified t, z
     */
    public short[] getDataCopyCXYAsShort(int t, int z)
    {
        return getDataCopyCXYAsShort(t, z, null, 0);
    }

    /**
     * Return a 1D array data copy [CXY] of internal 2D array data [C][XY] for specified t, z<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public short[] getDataCopyCXYAsShort(int t, int z, short[] out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyCXYAsShort(out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [CXY] of internal 2D array data [C][XY] for specified t, z
     */
    public int[] getDataCopyCXYAsInt(int t, int z)
    {
        return getDataCopyCXYAsInt(t, z, null, 0);
    }

    /**
     * Return a 1D array data copy [CXY] of internal 2D array data [C][XY] for specified t, z<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public int[] getDataCopyCXYAsInt(int t, int z, int[] out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyCXYAsInt(out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [CXY] of internal 2D array data [C][XY] for specified t, z
     */
    public float[] getDataCopyCXYAsFloat(int t, int z)
    {
        return getDataCopyCXYAsFloat(t, z, null, 0);
    }

    /**
     * Return a 1D array data copy [CXY] of internal 2D array data [C][XY] for specified t, z<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public float[] getDataCopyCXYAsFloat(int t, int z, float[] out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyCXYAsFloat(out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [CXY] of internal 2D array data [C][XY] for specified t, z
     */
    public double[] getDataCopyCXYAsDouble(int t, int z)
    {
        return getDataCopyCXYAsDouble(t, z, null, 0);
    }

    /**
     * Return a 1D array data copy [CXY] of internal 2D array data [C][XY] for specified t, z<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public double[] getDataCopyCXYAsDouble(int t, int z, double[] out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyCXYAsDouble(out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [C] of specified t, z, x, y
     */
    public byte[] getDataCopyCAsByte(int t, int z, int x, int y)
    {
        return getDataCopyCAsByte(t, z, x, y, null, 0);
    }

    /**
     * Return a 1D array data copy [C] of specified t, z, x, y<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public byte[] getDataCopyCAsByte(int t, int z, int x, int y, byte[] out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyCAsByte(x, y, out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [C] of specified t, z, x, y
     */
    public short[] getDataCopyCAsShort(int t, int z, int x, int y)
    {
        return getDataCopyCAsShort(t, z, x, y, null, 0);
    }

    /**
     * Return a 1D array data copy [C] of specified t, z, x, y<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public short[] getDataCopyCAsShort(int t, int z, int x, int y, short[] out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyCAsShort(x, y, out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [C] of specified t, z, x, y
     */
    public int[] getDataCopyCAsInt(int t, int z, int x, int y)
    {
        return getDataCopyCAsInt(t, z, x, y, null, 0);
    }

    /**
     * Return a 1D array data copy [C] of specified t, z, x, y<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public int[] getDataCopyCAsInt(int t, int z, int x, int y, int[] out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyCAsInt(x, y, out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [C] of specified t, z, x, y
     */
    public float[] getDataCopyCAsFloat(int t, int z, int x, int y)
    {
        return getDataCopyCAsFloat(t, z, x, y, null, 0);
    }

    /**
     * Return a 1D array data copy [C] of specified t, z, x, y<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public float[] getDataCopyCAsFloat(int t, int z, int x, int y, float[] out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyCAsFloat(x, y, out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [C] of specified t, z, x, y
     */
    public double[] getDataCopyCAsDouble(int t, int z, int x, int y)
    {
        return getDataCopyCAsDouble(t, z, x, y, null, 0);
    }

    /**
     * Return a 1D array data copy [C] of specified t, z, x, y<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public double[] getDataCopyCAsDouble(int t, int z, int x, int y, double[] out, int off)
    {
        final IcyBufferedImage img = getImage(t, z);

        if (img != null)
            return img.getDataCopyCAsDouble(x, y, out, off);

        return out;
    }

    /**
     * Return a 1D array data copy [XYZT] of internal 3D array data [T][Z][XY] for specified c
     */
    public byte[] getDataCopyXYZTAsByte(int c)
    {
        return getDataCopyXYZTAsByte(c, null, 0);
    }

    /**
     * Return a 1D array data copy [XYZT] of internal 3D array data [T][Z][XY] for specified c<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public byte[] getDataCopyXYZTAsByte(int c, byte[] out, int off)
    {
        final int sizeT = getSizeT();
        final int len = getSizeX() * getSizeY() * getSizeZ();
        final byte[] result = Array1DUtil.allocIfNull(out, len * sizeT);
        int offset = off;

        for (int t = 0; t < sizeT; t++)
        {
            getDataCopyXYZAsByte(t, c, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [XYZT] of internal 3D array data [T][Z][XY] for specified c
     */
    public short[] getDataCopyXYZTAsShort(int c)
    {
        return getDataCopyXYZTAsShort(c, null, 0);
    }

    /**
     * Return a 1D array data copy [XYZT] of internal 3D array data [T][Z][XY] for specified c<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public short[] getDataCopyXYZTAsShort(int c, short[] out, int off)
    {
        final int sizeT = getSizeT();
        final int len = getSizeX() * getSizeY() * getSizeZ();
        final short[] result = Array1DUtil.allocIfNull(out, len * sizeT);
        int offset = off;

        for (int t = 0; t < sizeT; t++)
        {
            getDataCopyXYZAsShort(t, c, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [XYZT] of internal 3D array data [T][Z][XY] for specified c
     */
    public int[] getDataCopyXYZTAsInt(int c)
    {
        return getDataCopyXYZTAsInt(c, null, 0);
    }

    /**
     * Return a 1D array data copy [XYZT] of internal 3D array data [T][Z][XY] for specified c<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public int[] getDataCopyXYZTAsInt(int c, int[] out, int off)
    {
        final int sizeT = getSizeT();
        final int len = getSizeX() * getSizeY() * getSizeZ();
        final int[] result = Array1DUtil.allocIfNull(out, len * sizeT);
        int offset = off;

        for (int t = 0; t < sizeT; t++)
        {
            getDataCopyXYZAsInt(t, c, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [XYZT] of internal 3D array data [T][Z][XY] for specified c
     */
    public float[] getDataCopyXYZTAsFloat(int c)
    {
        return getDataCopyXYZTAsFloat(c, null, 0);
    }

    /**
     * Return a 1D array data copy [XYZT] of internal 3D array data [T][Z][XY] for specified c<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public float[] getDataCopyXYZTAsFloat(int c, float[] out, int off)
    {
        final int sizeT = getSizeT();
        final int len = getSizeX() * getSizeY() * getSizeZ();
        final float[] result = Array1DUtil.allocIfNull(out, len * sizeT);
        int offset = off;

        for (int t = 0; t < sizeT; t++)
        {
            getDataCopyXYZAsFloat(t, c, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [XYZT] of internal 3D array data [T][Z][XY] for specified c
     */
    public double[] getDataCopyXYZTAsDouble(int c)
    {
        return getDataCopyXYZTAsDouble(c, null, 0);
    }

    /**
     * Return a 1D array data copy [XYZT] of internal 3D array data [T][Z][XY] for specified c<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public double[] getDataCopyXYZTAsDouble(int c, double[] out, int off)
    {
        final int sizeT = getSizeT();
        final int len = getSizeX() * getSizeY() * getSizeZ();
        final double[] result = Array1DUtil.allocIfNull(out, len * sizeT);
        int offset = off;

        for (int t = 0; t < sizeT; t++)
        {
            getDataCopyXYZAsDouble(t, c, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [XYZ] of internal 2D array data [Z][XY] for specified t, c
     */
    public byte[] getDataCopyXYZAsByte(int t, int c)
    {
        return getDataCopyXYZAsByte(t, c, null, 0);
    }

    /**
     * Return a 1D array data copy [XYZ] of internal 2D array data [Z][XY] for specified t, c<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public byte[] getDataCopyXYZAsByte(int t, int c, byte[] out, int off)
    {
        final int sizeZ = getSizeZ();
        final int len = getSizeX() * getSizeY();
        final byte[] result = Array1DUtil.allocIfNull(out, len * sizeZ);
        int offset = off;

        for (int z = 0; z < sizeZ; z++)
        {
            getDataCopyXYAsByte(t, z, c, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [XYZ] of internal 2D array data [Z][XY] for specified t, c
     */
    public short[] getDataCopyXYZAsShort(int t, int c)
    {
        return getDataCopyXYZAsShort(t, c, null, 0);
    }

    /**
     * Return a 1D array data copy [XYZ] of internal 2D array data [Z][XY] for specified t, c<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public short[] getDataCopyXYZAsShort(int t, int c, short[] out, int off)
    {
        final int sizeZ = getSizeZ();
        final int len = getSizeX() * getSizeY();
        final short[] result = Array1DUtil.allocIfNull(out, len * sizeZ);
        int offset = off;

        for (int z = 0; z < sizeZ; z++)
        {
            getDataCopyXYAsShort(t, z, c, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [XYZ] of internal 2D array data [Z][XY] for specified t, c
     */
    public int[] getDataCopyXYZAsInt(int t, int c)
    {
        return getDataCopyXYZAsInt(t, c, null, 0);
    }

    /**
     * Return a 1D array data copy [XYZ] of internal 2D array data [Z][XY] for specified t, c<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public int[] getDataCopyXYZAsInt(int t, int c, int[] out, int off)
    {
        final int sizeZ = getSizeZ();
        final int len = getSizeX() * getSizeY();
        final int[] result = Array1DUtil.allocIfNull(out, len * sizeZ);
        int offset = off;

        for (int z = 0; z < sizeZ; z++)
        {
            getDataCopyXYAsInt(t, z, c, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [XYZ] of internal 2D array data [Z][XY] for specified t, c
     */
    public float[] getDataCopyXYZAsFloat(int t, int c)
    {
        return getDataCopyXYZAsFloat(t, c, null, 0);
    }

    /**
     * Return a 1D array data copy [XYZ] of internal 2D array data [Z][XY] for specified t, c<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public float[] getDataCopyXYZAsFloat(int t, int c, float[] out, int off)
    {
        final int sizeZ = getSizeZ();
        final int len = getSizeX() * getSizeY();
        final float[] result = Array1DUtil.allocIfNull(out, len * sizeZ);
        int offset = off;

        for (int z = 0; z < sizeZ; z++)
        {
            getDataCopyXYAsFloat(t, z, c, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Return a 1D array data copy [XYZ] of internal 2D array data [Z][XY] for specified t, c
     */
    public double[] getDataCopyXYZAsDouble(int t, int c)
    {
        return getDataCopyXYZAsDouble(t, c, null, 0);
    }

    /**
     * Return a 1D array data copy [XYZ] of internal 2D array data [Z][XY] for specified t, c<br>
     * If (out != null) then it's used to store result at the specified offset
     */
    public double[] getDataCopyXYZAsDouble(int t, int c, double[] out, int off)
    {
        final int sizeZ = getSizeZ();
        final int len = getSizeX() * getSizeY();
        final double[] result = Array1DUtil.allocIfNull(out, len * sizeZ);
        int offset = off;

        for (int z = 0; z < sizeZ; z++)
        {
            getDataCopyXYAsDouble(t, z, c, result, offset);
            offset += len;
        }

        return result;
    }

    /**
     * Create a sub sequence with from specified coordinates and with specified dimensions
     */
    public Sequence getSubSequence(int startX, int startY, int startZ, int startT, int sizeX, int sizeY, int sizeZ,
            int sizeT)
    {
        final Sequence result = new Sequence();

        result.beginUpdate();
        try
        {
            for (int t = 0; t < sizeT; t++)
            {
                for (int z = 0; z < sizeZ; z++)
                {
                    final IcyBufferedImage img = getImage(startT + t, startZ + z);

                    if (img != null)
                        result.setImage(t, z, img.getSubImageCopy(startX, startY, sizeX, sizeY));
                    else
                        result.setImage(t, z, null);
                }
            }
        }
        finally
        {
            result.endUpdate();
        }

        result.setName("Sub part of " + getName());

        return result;
    }

    /**
     * Create and return a copy of the sequence
     */
    public Sequence getCopy()
    {
        final Sequence result = new Sequence();
        final int sizeT = getSizeT();
        final int sizeZ = getSizeZ();

        for (int t = 0; t < sizeT; t++)
        {
            for (int z = 0; z < sizeZ; z++)
            {
                final IcyBufferedImage img = getImage(t, z);

                if (img != null)
                    result.setImage(t, z, img.getCopy());
                else
                    setImage(t, z, null);
            }
        }

        result.setName(getName() + " (copy)");

        return result;
    }

    /**
     * Set all viewer containing this sequence to time t.
     * 
     * @deprecated Use this piece of code instead :<br>
     *             <code>for(Viewer v: Icy.getMainInterface().getViewers(sequence))</code></br>
     *             <code>   v.setT(...)</code>
     */
    @Deprecated
    public void setT(int t)
    {
        for (Viewer viewer : Icy.getMainInterface().getViewers())
            if (viewer.getSequence() == this)
                viewer.setT(t);
    }

    /**
     * Load attached XML data
     */
    public boolean loadXMLData()
    {
        return persistent.loadXMLData();
    }

    /**
     * Synchronize XML data with sequence data :<br>
     * This function refresh all the meta data and ROIs of the sequence and put it in the current
     * XML Document.
     */
    public void refreshXMLData()
    {
        persistent.refreshXMLData();
    }

    /**
     * Save attached XML data
     */
    public boolean saveXMLData()
    {
        return persistent.saveXMLData();
    }

    /**
     * Get XML data node identified by specified name
     * 
     * @param name
     *        name of wanted node
     */
    public Node getNode(String name)
    {
        return persistent.getNode(name);
    }

    /**
     * Create a node with specified name and return it<br>
     * If the node already exists the existing node is returned
     * 
     * @param name
     *        name of node to set in attached XML data
     */
    public Node setNode(String name)
    {
        return persistent.setNode(name);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString()
    {
        return name;
    }

    /**
     * Do common job on "image add" here
     * 
     * @param image
     */
    public void onImageAdded(IcyBufferedImage image)
    {
        // colorModel not yet defined ?
        if (colorModel == null)
            // define it from the image colorModel
            setColorModel(IcyColorModel.createInstance(image.getIcyColorModel(), true, true));

        // add listener to image
        image.addListener(this);

        // notify changed
        dataChanged(image, SequenceEventType.ADDED);
    }

    /**
     * Do common job on "image replaced" here
     */
    public void onImageReplaced(IcyBufferedImage oldImage, IcyBufferedImage newImage)
    {
        // we replaced the only present image
        final boolean typeChange = getNumImage() == 1;

        beginUpdate();
        try
        {
            if (typeChange)
            {
                // colorModel not compatible ?
                if (!colorModel.isCompatible(newImage.getIcyColorModel()))
                    // define it from the new image colorModel
                    setColorModel(IcyColorModel.createInstance(newImage.getIcyColorModel(), true, true));
                else
                    // only inform about a type change (sequence sizeX and sizeY)
                    typeChanged();
            }

            // remove listener from old image
            oldImage.removeListener(this);
            // notify about old image remove
            dataChanged(oldImage, SequenceEventType.REMOVED);

            // add listener to new image
            newImage.addListener(this);
            // notify about new image added
            dataChanged(newImage, SequenceEventType.ADDED);
        }
        finally
        {
            endUpdate();
        }
    }

    /**
     * Do common job on "image remove" here
     * 
     * @param image
     */
    public void onImageRemoved(IcyBufferedImage image)
    {
        // no more image ?
        if (isEmpty())
            // free the global colorModel
            setColorModel(null);

        // remove listener from image
        image.removeListener(this);

        // notify changed
        dataChanged(image, SequenceEventType.REMOVED);
    }

    /**
     * fire change event
     */
    private void fireChangeEvent(SequenceEvent e)
    {
        for (SequenceListener listener : listeners.getListeners(SequenceListener.class))
            listener.sequenceChanged(e);
    }

    /**
     * fire close event
     */
    private void fireCloseEvent()
    {
        for (SequenceListener listener : listeners.getListeners(SequenceListener.class))
            listener.sequenceClosed(this);
    }

    public void beginUpdate()
    {
        updater.beginUpdate();
    }

    public void endUpdate()
    {
        updater.endUpdate();

        // update end ?
        if (!updater.isUpdating())
        {
            // do pending tasks
            if (componentBoundsInvalid)
            {
                componentBoundsInvalid = false;
                // images components bounds are correct at this point
                internalUpdateComponentsBounds();
            }
        }
    }

    public boolean isUpdating()
    {
        return updater.isUpdating();
    }

    /**
     * sequence name has changed
     */
    private void nameChanged()
    {
        updater.changed(new SequenceEvent(this, SequenceEventSourceType.SEQUENCE_NAME));
    }

    /**
     * sequence meta has changed
     */
    private void metaChanged(String metaName)
    {
        updater.changed(new SequenceEvent(this, SequenceEventSourceType.SEQUENCE_META, metaName));
    }

    /**
     * sequence meta has changed
     */
    private void metaChanged(String metaName, int param)
    {
        updater.changed(new SequenceEvent(this, SequenceEventSourceType.SEQUENCE_META, metaName, null, param));
    }

    /**
     * sequence type (colorModel, size) changed
     */
    private void typeChanged()
    {
        updater.changed(new SequenceEvent(this, SequenceEventSourceType.SEQUENCE_TYPE));

        final String[] cn = new String[getSizeC()];

        final int len = Math.min(channelsName.length, cn.length);
        int c = 0;
        // preserve previous channels name
        for (; c < len; c++)
            cn[c] = channelsName[c];
        // default channels name
        for (; c < cn.length; c++)
            cn[c] = DEFAULT_CHANNEL_NAME + c;

        // set new channels names
        channelsName = cn;

        // only if some changes happened in channels name
        if (len != cn.length)
            metaChanged(ID_CHANNEL_NAME);
    }

    /**
     * sequence colorMap changed
     */
    private void colormapChanged(IcyColorModel colorModel, int component)
    {
        updater.changed(new SequenceEvent(this, SequenceEventSourceType.SEQUENCE_COLORMAP, colorModel, component));
    }

    /**
     * sequence component bounds changed
     */
    private void componentBoundsChanged(IcyColorModel colorModel, int component)
    {
        updater.changed(new SequenceEvent(this, SequenceEventSourceType.SEQUENCE_COMPONENTBOUNDS, colorModel, component));
    }

    /**
     * painter has changed
     */
    private void painterChanged(Painter painter, SequenceEventType type)
    {
        updater.changed(new SequenceEvent(this, SequenceEventSourceType.SEQUENCE_PAINTER, painter, type));
    }

    /**
     * notify specified painter has changed (null means all painters)
     */
    public void painterChanged(Painter painter)
    {
        painterChanged(painter, SequenceEventType.CHANGED);
    }

    /**
     * notify roi has changed (global change)
     */
    public void roiChanged()
    {
        updater.changed(new SequenceEvent(this, SequenceEventSourceType.SEQUENCE_ROI));
    }

    /**
     * notify specified roi has changed (null means all rois)
     */
    private void roiChanged(ROI roi, SequenceEventType type)
    {
        updater.changed(new SequenceEvent(this, SequenceEventSourceType.SEQUENCE_ROI, roi, type));
    }

    /**
     * Data has changed (global change)<br>
     * Be careful, this implies all component bounds are recalculated, can be heavy !
     */
    public void dataChanged()
    {
        updater.changed(new SequenceEvent(this, SequenceEventSourceType.SEQUENCE_DATA, null));
    }

    /**
     * data has changed
     */
    private void dataChanged(IcyBufferedImage image, SequenceEventType type)
    {
        updater.changed(new SequenceEvent(this, SequenceEventSourceType.SEQUENCE_DATA, image, type, 0));
    }

    @Override
    public void colorModelChanged(IcyColorModelEvent e)
    {
        switch (e.getType())
        {
            case COLORMAP_CHANGED:
                colormapChanged(e.getColorModel(), e.getComponent());
                break;

            case SCALER_CHANGED:
                componentBoundsChanged(e.getColorModel(), e.getComponent());
                break;
        }
    }

    @Override
    public void imageChanged(IcyBufferedImageEvent e)
    {
        final IcyBufferedImage image = e.getImage();

        switch (e.getType())
        {
            case BOUNDS_CHANGED:
                // update sequence components bounds if automatic mode enabled
                if (componentAbsBoundsAutoUpdate)
                {
                    // updating sequence ? delay update
                    if (isUpdating())
                        componentBoundsInvalid = true;
                    else
                        // refresh sequence component bounds from images ones
                        internalUpdateComponentsBounds();
                }
                break;

            case COLORMAP_CHANGED:
                // ignore that, we don't care about image's colormap
                break;

            case DATA_CHANGED:
                // image data changed
                dataChanged(image, SequenceEventType.CHANGED);
                break;
        }
    }

    @Override
    public void roiChanged(ROIEvent event)
    {
        // notify the ROI has changed
        roiChanged(event.getSource(), SequenceEventType.CHANGED);
    }

    /**
     * process on sequence change
     */
    @Override
    public void onChanged(EventHierarchicalChecker e)
    {
        final SequenceEvent event = (SequenceEvent) e;

        switch (event.getSourceType())
        {
        // do here global process on sequence data change
            case SEQUENCE_DATA:
                // automatic components bounds update enabled
                if (componentAbsBoundsAutoUpdate)
                {
                    // generic CHANGED event
                    if (event.getSource() == null)
                        // recalculate all images bounds and update sequence bounds
                        updateComponentsBounds(true, false);
                    else
                        // refresh sequence component bounds from images ones
                        internalUpdateComponentsBounds();
                }
                break;

            // do here global process on sequence type change
            case SEQUENCE_TYPE:
                break;

            // do here global process on sequence colormap change
            case SEQUENCE_COLORMAP:
                break;

            // do here global process on sequence component bounds change
            case SEQUENCE_COMPONENTBOUNDS:
                break;

            // do here global process on sequence painter change
            case SEQUENCE_PAINTER:
                break;

            // do here global process on sequence ROI change
            case SEQUENCE_ROI:
                break;
        }

        // notify listener we have changed
        fireChangeEvent(event);
    }
}
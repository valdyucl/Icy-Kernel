/**
 * 
 */
package plugins.kernel.roi.descriptor.property;

import icy.roi.ROI;
import icy.roi.ROIDescriptor;
import icy.sequence.Sequence;

/**
 * Opacity descriptor class (see {@link ROIDescriptor})
 * 
 * @author Stephane
 */
public class ROIOpacityDescriptor extends ROIDescriptor
{
    public static final String ID = "Opacity";

    public ROIOpacityDescriptor()
    {
        super(ID, "Opacity", Float.class);
    }

    @Override
    public String getDescription()
    {
        return "Opacity factor to display ROI content";
    }

    @Override
    public boolean useSequenceData()
    {
        return false;
    }

    @Override
    public Object compute(ROI roi, Sequence sequence) throws UnsupportedOperationException
    {
        return Float.valueOf(getOpacity(roi));
    }

    /**
     * Returns ROI opacity
     */
    public static float getOpacity(ROI roi)
    {
        if (roi == null)
            return 1f;

        return roi.getOpacity();
    }
}
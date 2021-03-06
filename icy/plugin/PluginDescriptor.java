/*
 * Copyright 2010-2015 Institut Pasteur.
 * 
 * This file is part of Icy.
 * 
 * Icy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Icy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Icy. If not, see <http://www.gnu.org/licenses/>.
 */
package icy.plugin;

import icy.common.Version;
import icy.file.FileUtil;
import icy.file.xml.XMLPersistent;
import icy.file.xml.XMLPersistentHelper;
import icy.image.ImageUtil;
import icy.network.NetworkUtil;
import icy.network.URLUtil;
import icy.plugin.abstract_.Plugin;
import icy.plugin.interface_.PluginBundled;
import icy.plugin.interface_.PluginImageAnalysis;
import icy.preferences.RepositoryPreferences.RepositoryInfo;
import icy.resource.ResourceUtil;
import icy.util.ClassUtil;
import icy.util.JarUtil;
import icy.util.StringUtil;
import icy.util.XMLUtil;

import java.awt.Image;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.swing.ImageIcon;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * <br>
 * The plugin descriptor contains all the data needed to launch a plugin. <br>
 * 
 * @see PluginLauncher
 * @author Fabrice de Chaumont & Stephane
 */
public class PluginDescriptor implements XMLPersistent
{
    public static final int ICON_SIZE = 64;
    public static final int IMAGE_SIZE = 256;

    public static final ImageIcon DEFAULT_ICON = ResourceUtil.getImageIcon(ResourceUtil.IMAGE_PLUGIN_SMALL);
    public static final Image DEFAULT_IMAGE = ResourceUtil.IMAGE_PLUGIN;

    /**
     * @deprecated Use {@link PluginIdent#ID_CLASSNAME} instead
     */
    @Deprecated
    public static final String ID_CLASSNAME = PluginIdent.ID_CLASSNAME;
    /**
     * @deprecated Use {@link PluginIdent#ID_REQUIRED_KERNEL_VERSION} instead
     */
    @Deprecated
    public static final String ID_REQUIRED_KERNEL_VERSION = PluginIdent.ID_REQUIRED_KERNEL_VERSION;

    public static final String ID_URL = "url";
    public static final String ID_NAME = "name";

    public static final String ID_JAR_URL = "jar_url";
    public static final String ID_IMAGE_URL = "image_url";
    public static final String ID_ICON_URL = "icon_url";
    public static final String ID_AUTHOR = "author";
    public static final String ID_CHANGELOG = "changelog";
    public static final String ID_WEB = "web";
    public static final String ID_EMAIL = "email";
    public static final String ID_DESCRIPTION = "description";
    public static final String ID_DEPENDENCIES = "dependencies";
    public static final String ID_DEPENDENCY = "dependency";

    protected Class<? extends Plugin> pluginClass;

    protected ImageIcon icon;
    protected Image image;

    protected String name;
    protected PluginIdent ident;
    protected String xmlUrl;
    protected String jarUrl;
    protected String imageUrl;
    protected String iconUrl;
    protected String author;
    protected String web;
    protected String email;
    protected String desc;
    protected String changeLog;

    protected boolean enabled;
    protected boolean descriptorLoaded;
    protected boolean iconLoaded;
    protected boolean imageLoaded;
    protected boolean changeLogLoaded;
    // boolean checkingForUpdate;
    // boolean updateChecked;
    // PluginDescriptor onlineDescriptor;

    // private final List<String> publicClasseNames;
    protected final List<PluginIdent> required;

    // only for online descriptor
    protected RepositoryInfo repository;

    // /**
    // * Get online plugin of specified PluginIdent<br>
    // * Take care of "allow beta" global flag<br>
    // * This method can take sometime !<br>
    // */
    // public static PluginDescriptor getOnlinePlugin(PluginIdent ident, boolean loadImage)
    // {
    // PluginDescriptor betaDescriptor = null;
    // PluginDescriptor stableDescriptor = null;
    //
    // try
    // {
    // // get beta online plugin descriptor if allowed
    // if (PluginPreferences.getAllowBeta())
    // betaDescriptor = new PluginDescriptor(ident.getUrlBeta(), loadImage);
    // }
    // catch (Exception e)
    // {
    // betaDescriptor = null;
    // }
    //
    // try
    // {
    // // get stable online plugin descriptor
    // stableDescriptor = new PluginDescriptor(ident.getUrlStable(), loadImage);
    // }
    // catch (Exception e)
    // {
    // stableDescriptor = null;
    // }
    //
    // if ((betaDescriptor != null) && ((stableDescriptor == null) ||
    // betaDescriptor.isNewerOrEqual(stableDescriptor)))
    // return betaDescriptor;
    //
    // return stableDescriptor;
    // }

    /**
     * Returns the index for the specified plugin in the specified list.<br>
     * Returns -1 if not found.
     */
    public static int getIndex(List<PluginDescriptor> list, PluginDescriptor plugin)
    {
        return getIndex(list, plugin.getIdent());
    }

    /**
     * Returns the index for the specified plugin in the specified list.<br>
     * Returns -1 if not found.
     */
    public static int getIndex(List<PluginDescriptor> list, PluginIdent ident)
    {
        final int size = list.size();

        for (int i = 0; i < size; i++)
            if (list.get(i).getIdent().equals(ident))
                return i;

        return -1;
    }

    /**
     * Returns the index for the specified plugin in the specified list.<br>
     * Returns -1 if not found.
     */
    public static int getIndex(List<PluginDescriptor> list, String className)
    {
        final int size = list.size();

        for (int i = 0; i < size; i++)
            if (list.get(i).getClassName().equals(className))
                return i;

        return -1;
    }

    /**
     * Returns true if the specified plugin is present in the specified list.
     */
    public static boolean existInList(List<PluginDescriptor> list, PluginDescriptor plugin)
    {
        return existInList(list, plugin.getIdent());
    }

    /**
     * Returns true if the specified plugin is present in the specified list.
     */
    public static boolean existInList(List<PluginDescriptor> list, PluginIdent ident)
    {
        return getIndex(list, ident) != -1;
    }

    /**
     * Returns true if the specified plugin is present in the specified list.
     */
    public static boolean existInList(List<PluginDescriptor> list, String className)
    {
        return getIndex(list, className) != -1;
    }

    public static boolean existInList(Set<PluginDescriptor> plugins, PluginIdent ident)
    {
        for (PluginDescriptor plugin : plugins)
            if (plugin.getIdent().equals(ident))
                return true;

        return false;
    }

    public static void addToList(List<PluginDescriptor> list, PluginDescriptor plugin, int position)
    {
        if ((plugin != null) && !existInList(list, plugin))
            list.add(position, plugin);
    }

    public static void addToList(List<PluginDescriptor> list, PluginDescriptor plugin)
    {
        if ((plugin != null) && !existInList(list, plugin))
            list.add(plugin);
    }

    public static boolean removeFromList(List<PluginDescriptor> list, String className)
    {
        for (int i = list.size() - 1; i >= 0; i--)
        {
            final PluginDescriptor p = list.get(i);

            if (p.getClassName().equals(className))
            {
                list.remove(i);
                return true;
            }
        }

        return false;
    }

    // public static String getPluginTypeString(int type)
    // {
    // if ((type >= 0) && (type < pluginTypeString.length))
    // return pluginTypeString[type];
    //
    // return "";
    // }

    public static ArrayList<PluginDescriptor> getPlugins(List<PluginDescriptor> list, String className)
    {
        final ArrayList<PluginDescriptor> result = new ArrayList<PluginDescriptor>();

        for (PluginDescriptor plugin : list)
            if (plugin.getClassName().equals(className))
                result.add(plugin);

        return result;
    }

    public static PluginDescriptor getPlugin(List<PluginDescriptor> list, String className)
    {
        for (PluginDescriptor plugin : list)
            if (plugin.getClassName().equals(className))
                return plugin;

        return null;
    }

    public static PluginDescriptor getPlugin(List<PluginDescriptor> list, PluginIdent ident, boolean acceptNewer)
    {
        if (acceptNewer)
        {
            for (PluginDescriptor plugin : list)
                if (plugin.getIdent().isNewerOrEqual(ident))
                    return plugin;
        }
        else
        {
            for (PluginDescriptor plugin : list)
                if (plugin.getIdent().equals(ident))
                    return plugin;
        }

        return null;
    }

    public PluginDescriptor()
    {
        super();

        pluginClass = null;

        icon = DEFAULT_ICON;
        image = DEFAULT_IMAGE;

        xmlUrl = "";
        name = "";
        ident = new PluginIdent();
        jarUrl = "";
        imageUrl = "";
        iconUrl = "";
        author = "";
        web = "";
        email = "";
        desc = "";
        changeLog = "";

        required = new ArrayList<PluginIdent>();
        repository = null;

        // default
        enabled = true;
        descriptorLoaded = true;
        changeLogLoaded = true;
        iconLoaded = true;
        imageLoaded = true;
    }

    /**
     * Create from class, used for local plugin.
     */
    public PluginDescriptor(Class<? extends Plugin> clazz)
    {
        this();

        this.pluginClass = clazz;

        final String baseResourceName = clazz.getSimpleName();
        final String baseLocalName = ClassUtil.getPathFromQualifiedName(clazz.getName());

        // load icon
        URL iconUrl = clazz.getResource(baseResourceName + getIconExtension());
        if (iconUrl == null)
            iconUrl = URLUtil.getURL(baseLocalName + getIconExtension());
        // loadIcon(url);

        // load image
        URL imageUrl = clazz.getResource(baseResourceName + getImageExtension());
        if (imageUrl == null)
            imageUrl = URLUtil.getURL(baseLocalName + getImageExtension());
        // loadImage(url);

        // load xml
        URL xmlUrl = clazz.getResource(baseResourceName + getXMLExtension());
        if (xmlUrl == null)
            xmlUrl = URLUtil.getURL(baseLocalName + getXMLExtension());

        // can't load XML from specified URL ?
        if (!loadFromXML(xmlUrl))
        {
            // xml is absent or incorrect, we set default informations
            ident.setClassName(pluginClass.getName());
            name = pluginClass.getSimpleName();
            desc = name + " plugin";
        }

        // overwrite image and icon url with their local equivalent (keep online for XML url)
        this.iconUrl = iconUrl.toString();
        this.imageUrl = imageUrl.toString();

        // only descriptor is loaded here
        descriptorLoaded = true;
        changeLogLoaded = false;
        iconLoaded = false;
        imageLoaded = false;
    }

    /**
     * Create from plugin online identifier, used for online plugin only.
     * 
     * @throws IllegalArgumentException
     */
    public PluginDescriptor(PluginOnlineIdent ident, RepositoryInfo repos) throws IllegalArgumentException
    {
        this();

        this.ident.setClassName(ident.getClassName());
        this.ident.setVersion(ident.getVersion());
        this.ident.setRequiredKernelVersion(ident.getRequiredKernelVersion());
        this.xmlUrl = ident.getUrl();
        this.name = ident.getName();
        this.repository = repos;

        // mark descriptor and images as not yet loaded
        descriptorLoaded = false;
        changeLogLoaded = false;
        iconLoaded = false;
        imageLoaded = false;
    }

    /**
     * @deprecated Use {@link #loadDescriptor()} or {@link #loadAll()} instead
     */
    @Deprecated
    public boolean load(boolean loadImages)
    {
        if (loadDescriptor())
        {
            loadChangeLog();
            if (loadImages)
                return loadImages();
        }

        return false;
    }

    /**
     * Load descriptor informations (xmlUrl field should be correctly filled)
     */
    public boolean loadDescriptor()
    {
        return loadDescriptor(false);
    }

    /**
     * Load descriptor informations (xmlUrl field should be correctly filled).<br>
     * Returns <code>false</code> if the operation failed.
     */
    public boolean loadDescriptor(boolean reload)
    {
        // already loaded ?
        if (descriptorLoaded && !reload)
            return true;

        // just to avoid retry indefinitely if it fails
        descriptorLoaded = true;

        // retrieve document
        final Document document = XMLUtil.loadDocument(xmlUrl,
                (repository != null) ? repository.getAuthenticationInfo() : null, true);

        if (document != null)
        {
            // load xml
            if (!loadFromXML(document.getDocumentElement()))
            {
                System.err.println("Can't find valid XML file from '" + xmlUrl + "' for plugin class '"
                        + ident.getClassName() + "'");
                return false;
            }

            return true;
        }

        // display error only for first load
        if (!reload)
            System.err.println("Can't load XML file from '" + xmlUrl + "' for plugin class '" + ident.getClassName()
                    + "'");

        return false;
    }

    /**
     * Load change log field (xmlUrl field should be correctly filled)
     */
    public boolean loadChangeLog()
    {
        // already loaded ?
        if (changeLogLoaded)
            return true;

        // just to avoid retry indefinitely if it fails
        changeLogLoaded = true;

        // retrieve document
        final Document document = XMLUtil.loadDocument(xmlUrl,
                (repository != null) ? repository.getAuthenticationInfo() : null, true);

        if (document != null)
        {
            final Element node = document.getDocumentElement();

            if (node != null)
            {
                setChangeLog(XMLUtil.getElementValue(node, ID_CHANGELOG, ""));
                return true;
            }

            System.err.println("Can't find valid XML file from '" + xmlUrl + "' for plugin class '"
                    + ident.getClassName() + "'");
        }

        System.err.println("Can't load XML file from '" + xmlUrl + "' for plugin class '" + ident.getClassName() + "'");

        return false;
    }

    /**
     * Load 64x64 icon (icon url field should be correctly filled)
     */
    public boolean loadIcon()
    {
        // already loaded ?
        if (iconLoaded)
            return true;

        // need descriptor to be loaded first
        loadDescriptor();
        // just to avoid retry indefinitely if it fails
        iconLoaded = true;

        // load icon
        return loadIcon(URLUtil.getURL(iconUrl));
    }

    /**
     * Load 256x256 image (image url field should be correctly filled)
     */
    public boolean loadImage()
    {
        // already loaded ?
        if (imageLoaded)
            return true;

        // need descriptor to be loaded first
        loadDescriptor();
        // just to avoid retry indefinitely if it fails
        imageLoaded = true;

        // load image
        return loadImage(URLUtil.getURL(imageUrl));
    }

    /**
     * Load icon and image (both icon and image url fields should be correctly filled)
     */
    public boolean loadImages()
    {
        return loadIcon() & loadImage();
    }

    /**
     * Load descriptor and images if not already done
     */
    public boolean loadAll()
    {
        return loadDescriptor() & loadChangeLog() & loadImages();
    }

    /**
     * Check if the plugin class is an instance of (or subclass of) the specified class.
     */
    public boolean isInstanceOf(Class<?> baseClazz)
    {
        return ClassUtil.isSubClass(pluginClass, baseClazz);
    }

    /**
     * Return true if the plugin class is abstract
     */
    public boolean isAbstract()
    {
        return ClassUtil.isAbstract(pluginClass);
    }

    /**
     * Return true if the plugin class is private
     */
    public boolean isPrivate()
    {
        return ClassUtil.isPrivate(pluginClass);
    }

    /**
     * Return true if the plugin class is an interface
     */
    public boolean isInterface()
    {
        return pluginClass.isInterface();
    }

    /**
     * return true if the plugin has an action which can be started from menu
     */
    public boolean isActionable()
    {
        return isClassLoaded() && !isPrivate() && !isAbstract() && !isInterface()
                && isInstanceOf(PluginImageAnalysis.class);
    }

    /**
     * Return true if the plugin is bundled inside another plugin (mean it does not have a proper
     * descriptor)
     */
    public boolean isBundled()
    {
        return isClassLoaded() && isInstanceOf(PluginBundled.class);
    }

    /**
     * Return true if the plugin is in beta state
     */
    public boolean isBeta()
    {
        return getVersion().isBeta();
    }

    /**
     * Return true if this plugin is a system application plugin (declared in plugins.kernel
     * package).
     */
    public boolean isKernelPlugin()
    {
        return getClassName().startsWith(PluginLoader.PLUGIN_KERNEL_PACKAGE + ".");
    }

    boolean loadIcon(URL url)
    {
        // load icon
        if (url != null)
            icon = ResourceUtil.getImageIcon(
                    ImageUtil.load(NetworkUtil.getInputStream(url,
                            (repository != null) ? repository.getAuthenticationInfo() : null, true, false), false),
                    ICON_SIZE);

        // get default icon
        if (icon == null)
        {
            icon = DEFAULT_ICON;
            return false;
        }

        return true;
    }

    boolean loadImage(URL url)
    {
        // load image
        if (url != null)
            image = ImageUtil.scale(
                    ImageUtil.load(NetworkUtil.getInputStream(url,
                            (repository != null) ? repository.getAuthenticationInfo() : null, true, false), false),
                    IMAGE_SIZE, IMAGE_SIZE);

        // get default image
        if (image == null)
        {
            image = DEFAULT_IMAGE;
            return false;
        }

        return true;
    }

    // public void save()
    // {
    // // save icon
    // if (icon != null)
    // ImageUtil.saveImage(ImageUtil.toRenderedImage(icon.getImage()), "png", getIconFilename());
    // // save image
    // if (image != null)
    // ImageUtil.saveImage(ImageUtil.toRenderedImage(image), "png", getImageFilename());
    // // save xml
    // saveToXML();
    // }

    public boolean loadFromXML(String path)
    {
        return XMLPersistentHelper.loadFromXML(this, path);
    }

    public boolean loadFromXML(URL xmlUrl)
    {
        return XMLPersistentHelper.loadFromXML(this, xmlUrl);
    }

    @Override
    public boolean loadFromXML(Node node)
    {
        return loadFromXML(node, false);
    }

    public boolean loadFromXML(Node node, boolean loadChangeLog)
    {
        if (node == null)
            return false;

        // get the plugin ident
        ident.loadFromXML(node);

        setName(XMLUtil.getElementValue(node, ID_NAME, ""));
        setXmlUrl(XMLUtil.getElementValue(node, ID_URL, ""));
        setJarUrl(XMLUtil.getElementValue(node, ID_JAR_URL, ""));
        setImageUrl(XMLUtil.getElementValue(node, ID_IMAGE_URL, ""));
        setIconUrl(XMLUtil.getElementValue(node, ID_ICON_URL, ""));
        setAuthor(XMLUtil.getElementValue(node, ID_AUTHOR, ""));
        setWeb(XMLUtil.getElementValue(node, ID_WEB, ""));
        setEmail(XMLUtil.getElementValue(node, ID_EMAIL, ""));
        setDescription(XMLUtil.getElementValue(node, ID_DESCRIPTION, ""));
        if (loadChangeLog)
            setChangeLog(XMLUtil.getElementValue(node, ID_CHANGELOG, ""));
        else
            setChangeLog("");

        final Node nodeDependances = XMLUtil.getElement(node, ID_DEPENDENCIES);
        if (nodeDependances != null)
        {
            final ArrayList<Node> nodesDependances = XMLUtil.getChildren(nodeDependances, ID_DEPENDENCY);

            for (Node n : nodesDependances)
            {
                final PluginIdent ident = new PluginIdent();
                // required don't need URL information as we now search from classname
                ident.loadFromXML(n);
                if (!ident.isEmpty())
                    required.add(ident);
            }
        }

        return true;
    }

    public boolean saveToXML()
    {
        return XMLPersistentHelper.saveToXML(this, getXMLFilename());
    }

    @Override
    public boolean saveToXML(Node node)
    {
        if (node == null)
            return false;

        ident.saveToXML(node);

        XMLUtil.setElementValue(node, ID_NAME, getName());
        XMLUtil.setElementValue(node, ID_URL, getXmlUrl());
        XMLUtil.setElementValue(node, ID_JAR_URL, getJarUrl());
        XMLUtil.setElementValue(node, ID_IMAGE_URL, getImageUrl());
        XMLUtil.setElementValue(node, ID_ICON_URL, getIconUrl());
        XMLUtil.setElementValue(node, ID_AUTHOR, getAuthor());
        XMLUtil.setElementValue(node, ID_WEB, getWeb());
        XMLUtil.setElementValue(node, ID_EMAIL, getEmail());
        XMLUtil.setElementValue(node, ID_DESCRIPTION, getDescription());
        loadChangeLog();
        XMLUtil.setElementValue(node, ID_CHANGELOG, getChangeLog());

        // synchronized (dateFormatter)
        // {
        // XMLUtil.addChildElement(root, ID_INSTALL_DATE, dateFormatter.format(installed));
        // XMLUtil.addChildElement(root, ID_LASTUSE_DATE, dateFormatter.format(lastUse));
        // }

        // final Element publicClasses = XMLUtil.setElement(node, ID_PUBLIC_CLASSES);
        // if (publicClasses != null)
        // {
        // XMLUtil.removeAllChilds(publicClasses);
        // for (String className : publicClasseNames)
        // XMLUtil.addValue(XMLUtil.addElement(publicClasses, ID_CLASSNAME), className);
        // }

        final Element dependances = XMLUtil.setElement(node, ID_DEPENDENCIES);
        if (dependances != null)
        {
            XMLUtil.removeAllChildren(dependances);
            for (PluginIdent dep : required)
                dep.saveToXML(XMLUtil.addElement(dependances, ID_DEPENDENCY));
        }

        return true;
    }

    public boolean isClassLoaded()
    {
        return pluginClass != null;
    }

    /**
     * Returns the plugin class name.<br>
     * Ex: "plugins.tutorial.Example1"
     */
    public String getClassName()
    {
        return ident.getClassName();
    }

    public String getSimpleClassName()
    {
        return ident.getSimpleClassName();
    }

    /**
     * Returns the package name of the plugin class.
     */
    public String getPackageName()
    {
        return ident.getPackageName();
    }

    /**
     * Returns the minimum package name (remove "icy" or/and "plugin" header)<br>
     */
    public String getSimplePackageName()
    {
        return ident.getSimplePackageName();
    }

    /**
     * Returns the author package name (first part of simple package name)
     */
    public String getAuthorPackageName()
    {
        return ident.getAuthorPackageName();
    }

    /**
     * @deprecated useless method
     */
    @Deprecated
    public String getClassAsString()
    {
        if (pluginClass != null)
            return pluginClass.toString();

        return "";
    }

    /**
     * @return the pluginClass
     */
    public Class<? extends Plugin> getPluginClass()
    {
        return pluginClass;
    }

    /**
     * return associated filename
     */
    public String getFilename()
    {
        return ClassUtil.getPathFromQualifiedName(getClassName());
    }

    /**
     * Returns the XML file extension.
     */
    public String getXMLExtension()
    {
        return XMLUtil.FILE_DOT_EXTENSION;
    }

    /**
     * return xml filename
     */
    public String getXMLFilename()
    {
        return getFilename() + getXMLExtension();
    }

    /**
     * return icon extension
     */
    public String getIconExtension()
    {
        return "_icon.png";
    }

    /**
     * return icon filename
     */
    public String getIconFilename()
    {
        return getFilename() + getIconExtension();
    }

    /**
     * return image extension
     */
    public String getImageExtension()
    {
        return ".png";
    }

    /**
     * return image filename
     */
    public String getImageFilename()
    {
        return getFilename() + getImageExtension();
    }

    /**
     * Returns the JAR file extension.
     */
    public String getJarExtension()
    {
        return JarUtil.FILE_DOT_EXTENSION;
    }

    /**
     * return jar filename
     */
    public String getJarFilename()
    {
        return getFilename() + getJarExtension();
    }

    /**
     * @return the icon
     */
    public ImageIcon getIcon()
    {
        loadIcon();
        return icon;
    }

    /**
     * @return the icon as image
     */
    public Image getIconAsImage()
    {
        final ImageIcon i = getIcon();

        if (i != null)
            return i.getImage();

        return null;
    }

    /**
     * @return the image
     */
    public Image getImage()
    {
        loadImage();
        return image;
    }

    // /**
    // * @return the lastUse
    // */
    // public Date getLastUse()
    // {
    // return lastUse;
    // }
    //
    // /**
    // * @param lastUse
    // * the lastUse to set
    // */
    // public void setLastUse(Date lastUse)
    // {
    // this.lastUse = lastUse;
    // }

    /**
     * @return the ident
     */
    public PluginIdent getIdent()
    {
        return ident;
    }

    /**
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * @return the version
     */
    public Version getVersion()
    {
        if (ident != null)
            return ident.getVersion();

        return new Version();
    }

    // /**
    // * @return the url for current version
    // */
    // public String getUrlCurrent()
    // {
    // if (ident != null)
    // {
    // final Version ver = ident.getVersion();
    //
    // if (ver.isBeta())
    // return ident.getUrlBeta();
    //
    // return ident.getUrlStable();
    // }
    //
    // return "";
    // }

    /**
     * @return the url
     */
    public String getUrl()
    {
        // url is default XML url
        return getXmlUrl();
    }

    /**
     * @return the url for xml file
     */
    public String getXmlUrl()
    {
        return xmlUrl;
    }

    /**
     * @return the desc
     * @deprecated use {@link #getDescription()} instead
     */
    @Deprecated
    public String getDesc()
    {
        return getDescription();
    }

    /**
     * @return the description
     */
    public String getDescription()
    {
        return desc;
    }

    /**
     * @param xmlUrl
     *        the xmlUrl to set
     */
    public void setXmlUrl(String xmlUrl)
    {
        this.xmlUrl = xmlUrl;
    }

    /**
     * @param repository
     *        the repository to set
     */
    public void setRepository(RepositoryInfo repository)
    {
        this.repository = repository;
    }

    /**
     * @return the jarUrl
     */
    public String getJarUrl()
    {
        return jarUrl;
    }

    /**
     * @param jarUrl
     *        the jarUrl to set
     */
    public void setJarUrl(String jarUrl)
    {
        this.jarUrl = jarUrl;
    }

    /**
     * @return the imageUrl
     */
    public String getImageUrl()
    {
        return imageUrl;
    }

    /**
     * @param imageUrl
     *        the imageUrl to set
     */
    public void setImageUrl(String imageUrl)
    {
        this.imageUrl = imageUrl;
    }

    /**
     * @return the iconUrl
     */
    public String getIconUrl()
    {
        return iconUrl;
    }

    /**
     * @param iconUrl
     *        the iconUrl to set
     */
    public void setIconUrl(String iconUrl)
    {
        this.iconUrl = iconUrl;
    }

    /**
     * Returns the author's plugin name.
     */
    public String getAuthor()
    {
        return author;
    }

    /**
     * Returns the website url of this plugin.
     */
    public String getWeb()
    {
        return web;
    }

    /**
     * @return the email
     */
    public String getEmail()
    {
        return email;
    }

    /**
     * @return the changeLog
     */
    public String getChangeLog()
    {
        return changeLog;
    }

    /**
     * @deprecated Use {@link #getChangeLog()} instead
     */
    @Deprecated
    public String getChangesLog()
    {
        return getChangeLog();
    }

    /**
     * @return the requiredKernelVersion
     */
    public Version getRequiredKernelVersion()
    {
        return ident.getRequiredKernelVersion();
    }

    /**
     * Returns true if descriptor is loaded.
     */
    public boolean isDescriptorLoaded()
    {
        return descriptorLoaded;
    }

    /**
     * @deprecated Use {@link #isDescriptorLoaded()} instead
     */
    @Deprecated
    public boolean isLoaded()
    {
        return descriptorLoaded;
    }

    /**
     * Returns true if change log is loaded.
     */
    public boolean isChangeLogLoaded()
    {
        return changeLogLoaded;
    }

    /**
     * Returns true if icon is loaded.
     */
    public boolean isIconLoaded()
    {
        return iconLoaded;
    }

    /**
     * Returns true if image is loaded.
     */
    public boolean isImageLoaded()
    {
        return imageLoaded;
    }

    /**
     * Returns true if image and icon are loaded.
     */
    public boolean isImagesLoaded()
    {
        return iconLoaded && imageLoaded;
    }

    /**
     * Returns true if both descriptor and images are loaded.
     */
    public boolean isAllLoaded()
    {
        return descriptorLoaded && changeLogLoaded && iconLoaded && imageLoaded;
    }

    /**
     * @return the required
     */
    public List<PluginIdent> getRequired()
    {
        return new ArrayList<PluginIdent>(required);
    }

    public RepositoryInfo getRepository()
    {
        return repository;
    }

    /**
     * @return the enabled
     */
    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * @param enabled
     *        the enabled to set
     */
    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    /**
     * Return true if plugin is installed (corresponding JAR file exist)
     */
    public boolean isInstalled()
    {
        return FileUtil.exists(getJarFilename());
    }

    // /**
    // * @return the hasUpdate
    // */
    // public boolean getHasUpdate()
    // {
    // // true if online version > local version
    // return (onlineDescriptor != null) && onlineDescriptor.getVersion().isGreater(getVersion());
    // }
    //
    // /**
    // * @return the checkingForUpdate
    // */
    // public boolean isCheckingForUpdate()
    // {
    // return checkingForUpdate;
    // }
    //
    // /**
    // * @return the onlineDescriptor
    // */
    // public PluginDescriptor getOnlineDescriptor()
    // {
    // return onlineDescriptor;
    // }

    // /**
    // * @return the updateChecked
    // */
    // public boolean isUpdateChecked()
    // {
    // return updateChecked;
    // }
    //
    // /**
    // * check for update (asynchronous as it can take sometime)
    // */
    // public void checkForUpdate()
    // {
    // if (updateChecked)
    // return;
    //
    // checkingForUpdate = true;
    //
    // ThreadUtil.bgRunWait(new Runnable()
    // {
    // @Override
    // public void run()
    // {
    // try
    // {
    // onlineDescriptor = getOnlinePlugin(getIdent(), false);
    // }
    // catch (Exception E)
    // {
    // onlineDescriptor = null;
    // }
    // finally
    // {
    // checkingForUpdate = false;
    // updateChecked = true;
    // }
    // }
    // });
    // }

    /**
     * @param name
     *        the name to set
     */
    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * @param author
     *        the author to set
     */
    public void setAuthor(String author)
    {
        this.author = author;
    }

    /**
     * @param web
     *        the web to set
     */
    public void setWeb(String web)
    {
        this.web = web;
    }

    /**
     * @param email
     *        the email to set
     */
    public void setEmail(String email)
    {
        this.email = email;
    }

    /**
     * @param desc
     *        the description to set
     */
    public void setDescription(String desc)
    {
        this.desc = desc;
    }

    /**
     * @param value
     *        the changeLog to set
     */
    public void setChangeLog(String value)
    {
        this.changeLog = value;
    }

    /**
     * @deprecated use {@link #setChangeLog(String)}
     */
    @Deprecated
    public void setChangesLog(String value)
    {
        setChangeLog(value);
    }

    /**
     * Return true if specified plugin is required by current plugin
     */
    public boolean requires(PluginDescriptor plugin)
    {
        final PluginIdent curIdent = plugin.getIdent();

        for (PluginIdent ident : required)
            if (ident.isOlderOrEqual(curIdent))
                return true;

        return false;
    }

    public boolean isOlderOrEqual(PluginDescriptor plugin)
    {
        return ident.isOlderOrEqual(plugin.getIdent());
    }

    public boolean isOlder(PluginDescriptor plugin)
    {
        return ident.isOlder(plugin.getIdent());
    }

    public boolean isNewerOrEqual(PluginDescriptor plugin)
    {
        return ident.isNewerOrEqual(plugin.getIdent());
    }

    public boolean isNewer(PluginDescriptor plugin)
    {
        return ident.isNewer(plugin.getIdent());
    }

    @Override
    public String toString()
    {
        return getName() + " " + getVersion().toString();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof PluginDescriptor)
        {
            final PluginDescriptor plug = (PluginDescriptor) obj;

            return getClassName().equals(plug.getClassName()) && getVersion().equals(plug.getVersion());
        }

        return super.equals(obj);
    }

    @Override
    public int hashCode()
    {
        return getClassName().hashCode() ^ getVersion().hashCode();
    }

    public static class PluginIdent implements XMLPersistent
    {
        /**
         * Returns the index for the specified plugin ident in the specified list.<br>
         * Returns -1 if not found.
         */
        public static int getIndex(List<PluginIdent> list, PluginIdent ident)
        {
            final int size = list.size();

            for (int i = 0; i < size; i++)
                if (list.get(i).equals(ident))
                    return i;

            return -1;
        }

        /**
         * Returns the index for the specified plugin in the specified list.<br>
         * Returns -1 if not found.
         */
        public static int getIndex(List<? extends PluginIdent> list, String className)
        {
            final int size = list.size();

            for (int i = 0; i < size; i++)
                if (list.get(i).getClassName().equals(className))
                    return i;

            return -1;
        }

        public static final String ID_CLASSNAME = "classname";
        public static final String ID_VERSION = "version";
        public static final String ID_REQUIRED_KERNEL_VERSION = "required_kernel_version";

        protected String className;
        protected Version version;
        protected Version requiredKernelVersion;

        /**
         * 
         */
        public PluginIdent()
        {
            super();

            // default
            className = "";
            version = new Version();
            requiredKernelVersion = new Version();
        }

        public boolean loadFromXMLShort(Node node)
        {
            if (node == null)
                return false;

            setClassName(XMLUtil.getElementValue(node, ID_CLASSNAME, ""));
            setVersion(new Version(XMLUtil.getElementValue(node, ID_VERSION, "")));

            return true;
        }

        @Override
        public boolean loadFromXML(Node node)
        {
            if (!loadFromXMLShort(node))
                return false;

            setRequiredKernelVersion(new Version(XMLUtil.getElementValue(node, ID_REQUIRED_KERNEL_VERSION, "")));

            return true;
        }

        public boolean saveToXMLShort(Node node)
        {
            if (node == null)
                return false;

            XMLUtil.setElementValue(node, ID_CLASSNAME, getClassName());
            XMLUtil.setElementValue(node, ID_VERSION, getVersion().toString());

            return true;
        }

        @Override
        public boolean saveToXML(Node node)
        {
            if (!saveToXMLShort(node))
                return false;

            XMLUtil.setElementValue(node, ID_REQUIRED_KERNEL_VERSION, getRequiredKernelVersion().toString());

            return true;
        }

        public boolean isEmpty()
        {
            return StringUtil.isEmpty(className) && version.isEmpty() && requiredKernelVersion.isEmpty();
        }

        /**
         * @return the className
         */
        public String getClassName()
        {
            return className;
        }

        /**
         * @param className
         *        the className to set
         */
        public void setClassName(String className)
        {
            this.className = className;
        }

        /**
         * return the simple className
         */
        public String getSimpleClassName()
        {
            return ClassUtil.getSimpleClassName(className);
        }

        /**
         * return the package name
         */
        public String getPackageName()
        {
            return ClassUtil.getPackageName(className);
        }

        /**
         * return the minimum package name (remove "icy" or/and "plugin" header)<br>
         */
        public String getSimplePackageName()
        {
            String result = getPackageName();

            if (result.startsWith("icy."))
                result = result.substring(4);
            if (result.startsWith(PluginLoader.PLUGIN_PACKAGE))
                result = result.substring(PluginLoader.PLUGIN_PACKAGE.length() + 1);

            return result;
        }

        /**
         * return the author package name (first part of simple package name)
         */
        public String getAuthorPackageName()
        {
            final String result = getSimplePackageName();
            final int index = result.indexOf('.');

            if (index != -1)
                return result.substring(0, index);

            return result;
        }

        /**
         * @param version
         *        the version to set
         */
        public void setVersion(Version version)
        {
            this.version = version;
        }

        /**
         * @return the version
         */
        public Version getVersion()
        {
            return version;
        }

        /**
         * @return the requiredKernelVersion
         */
        public Version getRequiredKernelVersion()
        {
            return requiredKernelVersion;
        }

        /**
         * @param requiredKernelVersion
         *        the requiredKernelVersion to set
         */
        public void setRequiredKernelVersion(Version requiredKernelVersion)
        {
            this.requiredKernelVersion = requiredKernelVersion;
        }

        public boolean isOlderOrEqual(PluginIdent ident)
        {
            return className.equals(ident.getClassName()) && version.isOlderOrEqual(ident.getVersion());
        }

        public boolean isOlder(PluginIdent ident)
        {
            return className.equals(ident.getClassName()) && version.isOlder(ident.getVersion());
        }

        public boolean isNewerOrEqual(PluginIdent ident)
        {
            return className.equals(ident.getClassName()) && version.isNewerOrEqual(ident.getVersion());
        }

        public boolean isNewer(PluginIdent ident)
        {
            return className.equals(ident.getClassName()) && version.isNewer(ident.getVersion());
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof PluginIdent)
            {
                final PluginIdent ident = (PluginIdent) obj;
                return ident.getClassName().equals(className) && ident.getVersion().equals(getVersion());
            }

            return super.equals(obj);
        }

        @Override
        public int hashCode()
        {
            return className.hashCode() ^ version.hashCode();
        }

        @Override
        public String toString()
        {
            return className + " " + version.toString();
        }
    }

    public static class PluginOnlineIdent extends PluginIdent
    {
        protected String name;
        protected String url;

        public PluginOnlineIdent()
        {
            super();

            name = "";
            url = "";
        }

        /**
         * @return the name
         */
        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        /**
         * @return the url
         */
        public String getUrl()
        {
            return url;
        }

        public void setUrl(String url)
        {
            this.url = url;
        }

        @Override
        public boolean loadFromXML(Node node)
        {
            if (super.loadFromXML(node))
            {
                setName(XMLUtil.getElementValue(node, PluginDescriptor.ID_NAME, ""));
                setUrl(XMLUtil.getElementValue(node, PluginDescriptor.ID_URL, ""));
                return true;
            }

            return false;
        }

        @Override
        public boolean saveToXML(Node node)
        {
            if (super.saveToXML(node))
            {
                XMLUtil.setElementValue(node, PluginDescriptor.ID_NAME, getName());
                XMLUtil.setElementValue(node, PluginDescriptor.ID_URL, getUrl());
                return true;
            }

            return false;
        }
    }

    /**
     * Sort plugins on name with kernel plugins appearing first.
     */
    public static class PluginKernelNameSorter implements Comparator<PluginDescriptor>
    {
        // static class
        public static PluginKernelNameSorter instance = new PluginKernelNameSorter();

        // static class
        private PluginKernelNameSorter()
        {
            super();
        }

        @Override
        public int compare(PluginDescriptor o1, PluginDescriptor o2)
        {
            final String packageName1 = o1.getPackageName();
            final String packageName2 = o2.getPackageName();

            if (packageName1.startsWith(PluginLoader.PLUGIN_KERNEL_PACKAGE))
            {
                if (!packageName2.startsWith(PluginLoader.PLUGIN_KERNEL_PACKAGE))
                    return -1;
            }
            else if (packageName2.startsWith(PluginLoader.PLUGIN_KERNEL_PACKAGE))
                return 1;

            return o1.toString().compareToIgnoreCase(o2.toString());
        }
    }

    /**
     * Sort plugins on name.
     */
    public static class PluginNameSorter implements Comparator<PluginDescriptor>
    {
        // static class
        public static PluginNameSorter instance = new PluginNameSorter();

        // static class
        private PluginNameSorter()
        {
            super();
        }

        @Override
        public int compare(PluginDescriptor o1, PluginDescriptor o2)
        {
            return o1.toString().compareToIgnoreCase(o2.toString());
        }
    }

    /**
     * Sort plugins on class name.
     */
    public static class PluginClassNameSorter implements Comparator<PluginDescriptor>
    {
        // static class
        public static PluginClassNameSorter instance = new PluginClassNameSorter();

        // static class
        private PluginClassNameSorter()
        {
            super();
        }

        @Override
        public int compare(PluginDescriptor o1, PluginDescriptor o2)
        {
            return o1.getClassName().compareToIgnoreCase(o2.getClassName());
        }
    }

}

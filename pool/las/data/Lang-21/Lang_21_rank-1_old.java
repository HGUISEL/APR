/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.servicemix.kernel.main;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.CountDownLatch;
import java.security.Security;
import java.security.Provider;

import org.apache.felix.framework.Felix;
import org.apache.felix.framework.cache.BundleCache;
import org.apache.felix.framework.util.StringMap;
import org.apache.servicemix.kernel.main.spi.MainService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.startlevel.StartLevel;

/**
 * <p>
 * This class is the default way to instantiate and execute the framework. It is not
 * intended to be the only way to instantiate and execute the framework; rather, it is
 * one example of how to do so. When embedding the framework in a host application,
 * this class can serve as a simple guide of how to do so. It may even be
 * worthwhile to reuse some of its property handling capabilities. This class
 * is completely static and is only intended to start a single instance of
 * the framework.
 * </p>
 */
public class Main implements MainService, BundleActivator {
    /**
     * The default name used for the system properties file.
     */
    public static final String SYSTEM_PROPERTIES_FILE_NAME = "system.properties";
    /**
     * The default name used for the configuration properties file.
     */
    public static final String CONFIG_PROPERTIES_FILE_NAME = "config.properties";
    /**
     * The default name used for the startup properties file.
     */
    public static final String STARTUP_PROPERTIES_FILE_NAME = "startup.properties";
    /**
     * The property name prefix for the launcher's auto-install property.
     */
    public static final String PROPERTY_AUTO_INSTALL = "felix.auto.install";
    /**
     * The property for auto-discovering the bundles
     */
    public static final String PROPERTY_AUTO_START = "felix.auto.start";
    /**
     * The system property for specifying the ServiceMix home directory.  The home directory
     * hold the binary install of ServiceMix.
     */
    public static final String PROP_SERVICEMIX_HOME = "servicemix.home";
    /**
     * The environment variable for specifying the ServiceMix home directory.  The home directory
     * hold the binary install of ServiceMix.
     */
    public static final String ENV_SERVICEMIX_HOME = "SERVICEMIX_HOME";
    /**
     * The system property for specifying the ServiceMix base directory.  The base directory
     * holds the configuration and data for a ServiceMix instance.
     */
    public static final String PROP_SERVICEMIX_BASE = "servicemix.base";
    /**
     * The environment variable for specifying the ServiceMix base directory.  The base directory
     * holds the configuration and data for a ServiceMix instance.
     */
    public static final String ENV_SERVICEMIX_BASE = "SERVICEMIX_BASE";

    /**
     * Config property which identifies directories which contain bundles to be loaded by SMX
     */
    public static final String BUNDLE_LOCATIONS = "bundle.locations";

    /**
     * Config property that indicates we want to convert bundles locations to maven style urls
     */
    public static final String PROPERTY_CONVERT_TO_MAVEN_URL = "servicemix.maven.convert";

    /**
     * If a lock should be used before starting the runtime
     */
    public static final String PROPERTY_USE_LOCK = "servicemix.lock";

    /**
     * The lock implementation
     */
    public static final String PROPERTY_LOCK_CLASS = "servicemix.lock.class";

    public static final String PROPERTY_LOCK_CLASS_DEFAULT = SimpleFileLock.class.getName();

    public static final String PROPERTY_LOCK_URL = "servicemix.lock.jdbc.url";
 
    public static final String PROPERTY_LOCK_JDBC_DRIVER = "servicemix.lock.jdbc.driver";

    public static final String PROPERTY_LOCK_JDBC_USER = "servicemix.lock.jdbc.user";

    public static final String PROPERTY_LOCK_JDBC_PASSWORD = "servicemix.lock.jdbc.password";

    public static final String PROPERTY_LOCK_JDBC_TABLE = "servicemix.lock.jdbc.table";

    public static final String PROPERTY_LOCK_JDBC_CLUSTERNAME = "servicemix.lock.jdbc.clustername";

    public static final String PROPERTY_LOCK_JDBC_TIMEOUT = "servicemix.lock.jdbc.timeout";

    private File servicemixHome;
    private File servicemixBase;
    private static Properties m_configProps = null;
    private static Felix m_felix = null;
    private final String[] args;
    private int exitCode;
    private Lock lock;
    private CountDownLatch shutdown = new CountDownLatch(1);

    public Main(String[] args) {
        this.args = args;
    }

    public void launch() throws Exception {
        servicemixHome = getServiceMixHome();
        servicemixBase = getServiceMixBase(servicemixHome);

        //System.out.println("ServiceMix Home: "+main.servicemixHome.getPath());
        //System.out.println("ServiceMix Base: "+main.servicemixBase.getPath());

        System.setProperty(PROP_SERVICEMIX_HOME, servicemixHome.getPath());
        System.setProperty(PROP_SERVICEMIX_BASE, servicemixBase.getPath());

        // Load system properties.
        loadSystemProperties();

        // Read configuration properties.
        m_configProps = loadConfigProperties();

        // Copy framework properties from the system properties.
        Main.copySystemProperties(m_configProps);

        processSecurityProperties(m_configProps);

        String profileName = m_configProps.getProperty(BundleCache.CACHE_PROFILE_PROP);
        String profileDirName = m_configProps.getProperty(BundleCache.CACHE_PROFILE_DIR_PROP);

        // A profile directory or name must be specified.
        if ((profileDirName == null) && (profileName == null || profileName.length() == 0)) {
            setExitCode(-1);
            throw new Exception("Invalid " + CONFIG_PROPERTIES_FILE_NAME + " configuration.  The profile directory was not specified.");
        }

        // Register the Main class so that other bundles can inspect the command line args.
        BundleActivator activator = new BundleActivator() {
            private ServiceRegistration registration;

            public void start(BundleContext context) {
                registration = context.registerService(MainService.class.getName(), Main.this, null);
            }

            public void stop(BundleContext context) {
                registration.unregister();
                shutdown.countDown();
            }
        };
        List<BundleActivator> activations = new ArrayList<BundleActivator>();
        activations.add(this);
        activations.add(activator);

        try {
            lock(m_configProps);
            // Start up the OSGI framework
            m_felix = new Felix(new StringMap(m_configProps, false), activations);
            // Start lock monitor
            int pollLock = 30000;
            Thread lockMonitor = new LockMonitor(lock, m_felix, pollLock);
            lockMonitor.start();
            m_felix.start();
        }
        catch (Exception ex) {
            setExitCode(-1);
            throw new Exception("Could not create framework", ex);
        }
    }

    public void destroy(boolean await) throws Exception {
        try {
            if (await) {
                shutdown.await();
            }
            m_felix.stopAndWait();
        } finally {
            unlock();
        }
    }

    /**
     * Used to instigate auto-install and auto-start configuration
     * property processing via a custom framework activator during
     * framework startup.
     *
     * @param context The system bundle context.
     */
    public void start(BundleContext context) {
        Main.processAutoProperties(context);
    }

    /**
     * Currently does nothing as part of framework shutdown.
     *
     * @param context The system bundle context.
     */
    public void stop(BundleContext context) {
        // Do nothing.
    }

    /**
     * <p>
     * This method performs the main task of constructing an framework instance
     * and starting its execution. The following functions are performed
     * when invoked:
     * </p>
     * <ol>
     *   <li><i><b>Read the system properties file.<b></i> This is a file
     *       containing properties to be pushed into <tt>System.setProperty()</tt>
     *       before starting the framework. This mechanism is mainly shorthand
     *       for people starting the framework from the command line to avoid having
     *       to specify a bunch of <tt>-D</tt> system property definitions.
     *       The only properties defined in this file that will impact the framework's
     *       behavior are the those concerning setting HTTP proxies, such as
     *       <tt>http.proxyHost</tt>, <tt>http.proxyPort</tt>, and
     *       <tt>http.proxyAuth</tt>.
     *   </li>
     *   <li><i><b>Perform system property variable substitution on system
     *       properties.</b></i> Any system properties in the system property
     *       file whose value adheres to <tt>${&lt;system-prop-name&gt;}</tt>
     *       syntax will have their value substituted with the appropriate
     *       system property value.
     *   </li>
     *   <li><i><b>Read the framework's configuration property file.</b></i> This is
     *       a file containing properties used to configure the framework
     *       instance and to pass configuration information into
     *       bundles installed into the framework instance. The configuration
     *       property file is called <tt>config.properties</tt> by default
     *       and is located in the <tt>conf/</tt> directory of the Felix
     *       installation directory, which is the parent directory of the
     *       directory containing the <tt>felix.jar</tt> file. It is possible
     *       to use a different location for the property file by specifying
     *       the desired URL using the <tt>felix.config.properties</tt>
     *       system property; this should be set using the <tt>-D</tt> syntax
     *       when executing the JVM. Refer to the
     *       <a href="Felix.html#Felix(java.util.Map, java.util.List)">
     *       <tt>Felix</tt></a> constructor documentation for more
     *       information on the framework configuration options.
     *   </li>
     *   <li><i><b>Perform system property variable substitution on configuration
     *       properties.</b></i> Any configuration properties whose value adheres to
     *       <tt>${&lt;system-prop-name&gt;}</tt> syntax will have their value
     *       substituted with the appropriate system property value.
     *   </li>
     *   <li><i><b>Ensure the default bundle cache has sufficient information to
     *       initialize.</b></i> The default implementation of the bundle cache
     *       requires either a profile name or a profile directory in order to
     *       start. The configuration properties are checked for at least one
     *       of the <tt>felix.cache.profile</tt> or <tt>felix.cache.profiledir</tt>
     *       properties. If neither is found, the user is asked to supply a profile
     *       name that is added to the configuration property set. See the
     *       <a href="cache/DefaultBundleCache.html"><tt>DefaultBundleCache</tt></a>
     *       documentation for more details its configuration options.
     *   </li>
     *   <li><i><b>Creates and starts a framework instance.</b></i> A
     *       case insensitive
     *       <a href="util/StringMap.html"><tt>StringMap</tt></a>
     *       is created for the configuration property file and is passed
     *       into the framework.
     *   </li>
     * </ol>
     * <p>
     * It should be noted that simply starting an instance of the framework is not enough
     * to create an interactive session with it. It is necessary to install
     * and start bundles that provide an interactive impl; this is generally
     * done by specifying an "auto-start" property in the framework configuration
     * property file. If no interactive impl bundles are installed or if
     * the configuration property file cannot be found, the framework will appear to
     * be hung or deadlocked. This is not the case, it is executing correctly,
     * there is just no way to interact with it. Refer to the
     * <a href="Felix.html#Felix(java.util.Map, java.util.List)">
     * <tt>Felix</tt></a> constructor documentation for more information on
     * framework configuration options.
     * </p>
     * @param args An array of arguments, all of which are ignored.
     * @throws Exception If an error occurs.
     **/
    public static void main(String[] args) throws Exception {
        final Main main = new Main(args);
        try {
            main.launch();
            main.destroy(true);
        }
        catch (Exception ex) {
            System.err.println("Error occured shutting down framework: " + ex);
            ex.printStackTrace();
        } finally {
            System.exit(main.getExitCode());
        }
    }

    private static File getServiceMixHome() throws IOException {
        File rc = null;

        // Use the system property if specified.
        String path = System.getProperty(PROP_SERVICEMIX_HOME);
        if (path != null) {
            rc = validateDirectoryExists(path, "Invalid " + PROP_SERVICEMIX_HOME + " system property");
        }

        if (rc == null) {
            path = System.getenv(ENV_SERVICEMIX_HOME);
            if (path != null) {
                rc = validateDirectoryExists(path, "Invalid " + ENV_SERVICEMIX_HOME + " environment variable");
            }
        }

        // Try to figure it out using the jar file this class was loaded from.
        if (rc == null) {
            // guess the home from the location of the jar
            URL url = Main.class.getClassLoader().getResource(Main.class.getName().replace(".", "/") + ".class");
            if (url != null) {
                try {
                    JarURLConnection jarConnection = (JarURLConnection) url.openConnection();
                    url = jarConnection.getJarFileURL();
                    rc = new File(new URI(url.toString())).getCanonicalFile().getParentFile().getParentFile();
                } catch (Exception ignored) {
                }
            }
        }

        if (rc == null) {
            // Dig into the classpath to guess the location of the jar
            String classpath = System.getProperty("java.class.path");
            int index = classpath.toLowerCase().indexOf("servicemix.jar");
            int start = classpath.lastIndexOf(File.pathSeparator, index) + 1;
            if (index >= start) {
                String jarLocation = classpath.substring(start, index);
                rc = new File(jarLocation).getCanonicalFile().getParentFile();
            }
        }
        if (rc == null) {
            throw new IOException("The ServiceMix install directory could not be determined.  Please set the " + PROP_SERVICEMIX_HOME + " system property or the " + ENV_SERVICEMIX_HOME + " environment variable.");
        }

        return rc;
    }

    private static File validateDirectoryExists(String path, String errPrefix) {
        File rc;
        try {
            rc = new File(path).getCanonicalFile();
        } catch (IOException e) {
            throw new IllegalArgumentException(errPrefix + " '" + path + "' : " + e.getMessage());
        }
        if (!rc.exists()) {
            throw new IllegalArgumentException(errPrefix + " '" + path + "' : does not exist");
        }
        if (!rc.isDirectory()) {
            throw new IllegalArgumentException(errPrefix + " '" + path + "' : is not a directory");
        }
        return rc;
    }

    private static File getServiceMixBase(File defaultValue) {
        File rc = null;

        String path = System.getProperty(PROP_SERVICEMIX_BASE);
        if (path != null) {
            rc = validateDirectoryExists(path, "Invalid " + PROP_SERVICEMIX_BASE + " system property");
        }

        if (rc == null) {
            path = System.getenv(ENV_SERVICEMIX_BASE);
            if (path != null) {
                rc = validateDirectoryExists(path, "Invalid " + ENV_SERVICEMIX_BASE + " environment variable");
            }
        }

        if (rc == null) {
            rc = defaultValue;
        }
        return rc;
    }

    private static void processSecurityProperties(Properties m_configProps) {
        String prop = m_configProps.getProperty("org.apache.servicemix.security.providers");
        if (prop != null) {
            String[] providers = prop.split(",");
            for (String provider : providers) {
                try {
                    Security.addProvider((Provider) Class.forName(provider).newInstance());
                } catch (Throwable t) {
                    System.err.println("Unable to register security provider: " + t);
                }
            }
        }
    }

    /**
     * <p/>
     * Processes the auto-install and auto-start properties from the
     * specified configuration properties.
     */
    private static void processAutoProperties(BundleContext context) {
        // Check if we want to convert URLs to maven style
        boolean convertToMavenUrls = Boolean.parseBoolean(m_configProps.getProperty(PROPERTY_CONVERT_TO_MAVEN_URL, "true"));

        // Retrieve the Start Level service, since it will be needed
        // to set the start level of the installed bundles.
        StartLevel sl = (StartLevel) context.getService(
                context.getServiceReference(org.osgi.service.startlevel.StartLevel.class.getName()));

        // The auto-install property specifies a space-delimited list of
        // bundle URLs to be automatically installed into each new profile;
        // the start level to which the bundles are assigned is specified by
        // appending a ".n" to the auto-install property name, where "n" is
        // the desired start level for the list of bundles.
        for (Iterator i = m_configProps.keySet().iterator(); i.hasNext();) {
            String key = (String) i.next();

            // Ignore all keys that are not the auto-install property.
            if (!key.startsWith(PROPERTY_AUTO_INSTALL)) {
                continue;
            }

            // If the auto-install property does not have a start level,
            // then assume it is the default bundle start level, otherwise
            // parse the specified start level.
            int startLevel = sl.getInitialBundleStartLevel();
            if (!key.equals(PROPERTY_AUTO_INSTALL)) {
                try {
                    startLevel = Integer.parseInt(key.substring(key.lastIndexOf('.') + 1));
                }
                catch (NumberFormatException ex) {
                    System.err.println("Invalid property: " + key);
                }
            }

            StringTokenizer st = new StringTokenizer(m_configProps.getProperty(key), "\" ", true);
            if (st.countTokens() > 0) {
                String location = null;
                do {
                    location = nextLocation(st);
                    if (location != null) {
                        try {
                            String[] parts = convertToMavenUrlsIfNeeded(location, convertToMavenUrls);
                            Bundle b = context.installBundle(parts[0], new URL(parts[1]).openStream());
                            sl.setBundleStartLevel(b, startLevel);
                        }
                        catch (Exception ex) {
                            System.err.println("Auto-properties install: " + ex);
                        }
                    }
                }
                while (location != null);
            }
        }

        // The auto-start property specifies a space-delimited list of
        // bundle URLs to be automatically installed and started into each
        // new profile; the start level to which the bundles are assigned
        // is specified by appending a ".n" to the auto-start property name,
        // where "n" is the desired start level for the list of bundles.
        // The following code starts bundles in two passes, first it installs
        // them, then it starts them.
        for (Iterator i = m_configProps.keySet().iterator(); i.hasNext();) {
            String key = (String) i.next();

            // Ignore all keys that are not the auto-start property.
            if (!key.startsWith(PROPERTY_AUTO_START)) {
                continue;
            }

            // If the auto-start property does not have a start level,
            // then assume it is the default bundle start level, otherwise
            // parse the specified start level.
            int startLevel = sl.getInitialBundleStartLevel();
            if (!key.equals(PROPERTY_AUTO_START)) {
                try {
                    startLevel = Integer.parseInt(key.substring(key.lastIndexOf('.') + 1));
                }
                catch (NumberFormatException ex) {
                    System.err.println("Invalid property: " + key);
                }
            }

            StringTokenizer st = new StringTokenizer(m_configProps.getProperty(key), "\" ", true);
            if (st.countTokens() > 0) {
                String location = null;
                do {
                    location = nextLocation(st);
                    if (location != null) {
                        try {
                            String[] parts = convertToMavenUrlsIfNeeded(location, convertToMavenUrls);
                            Bundle b = context.installBundle(parts[0], new URL(parts[1]).openStream());
                            sl.setBundleStartLevel(b, startLevel);
                        }
                        catch (Exception ex) {
                            System.err.println("Auto-properties install:" + ex);
                        }
                    }
                }
                while (location != null);
            }
        }

        // Now loop through and start the installed bundles.
        for (Iterator i = m_configProps.keySet().iterator(); i.hasNext();) {
            String key = (String) i.next();
            if (key.startsWith(PROPERTY_AUTO_START)) {
                StringTokenizer st = new StringTokenizer(m_configProps.getProperty(key), "\" ", true);
                if (st.countTokens() > 0) {
                    String location = null;
                    do {
                        location = nextLocation(st);
                        if (location != null) {
                            // Installing twice just returns the same bundle.
                            try {
                                String[] parts = convertToMavenUrlsIfNeeded(location, convertToMavenUrls);
                                Bundle b = context.installBundle(parts[0], new URL(parts[1]).openStream());
                                if (b != null) {
                                    b.start();
                                }
                            }
                            catch (Exception ex) {
                                System.err.println("Auto-properties start: " + ex);
                            }
                        }
                    }
                    while (location != null);
                }
            }
        }
    }

    private static String[] convertToMavenUrlsIfNeeded(String location, boolean convertToMavenUrls) {
        String[] parts = location.split("\\|");
        if (convertToMavenUrls) {
            String[] p = parts[1].split("/");
            if (p.length >= 4 && p[p.length-1].startsWith(p[p.length-3] + "-" + p[p.length-2])) {
                String groupId = null;
                String artifactId = p[p.length-3];
                String version = p[p.length-2];
                String classifier;
                String type;
                String artifactIdVersion = artifactId + "-" + version;
                StringBuffer sb = new StringBuffer();
                if (p[p.length-1].charAt(artifactIdVersion.length()) == '-') {
                    classifier = p[p.length-1].substring(artifactIdVersion.length() + 1, p[p.length-1].lastIndexOf('.'));
                } else {
                    classifier = null;
                }
                type = p[p.length-1].substring(p[p.length-1].lastIndexOf('.') + 1);
                sb.append("mvn:");
                for (int j = 0; j < p.length - 3; j++) {
                    if (j > 0) {
                        sb.append('.');
                    }
                    sb.append(p[j]);
                }
                sb.append('/').append(artifactId).append('/').append(version);
                if (!"jar".equals(type) || classifier != null) {
                    sb.append('/');
                    if (!"jar".equals(type)) {
                        sb.append(type);
                    }
                    if (classifier != null) {
                        sb.append('/').append(classifier);
                    }
                }
                parts[1] = parts[0];
                parts[0] = sb.toString();
            } else {
                parts[1] = parts[0];
            }
        } else {
            parts[1] = parts[0];
        }
        return parts;
    }

    private static String nextLocation(StringTokenizer st) {
        String retVal = null;

        if (st.countTokens() > 0) {
            String tokenList = "\" ";
            StringBuffer tokBuf = new StringBuffer(10);
            String tok = null;
            boolean inQuote = false;
            boolean tokStarted = false;
            boolean exit = false;
            while ((st.hasMoreTokens()) && (!exit)) {
                tok = st.nextToken(tokenList);
                if (tok.equals("\"")) {
                    inQuote = !inQuote;
                    if (inQuote) {
                        tokenList = "\"";
                    } else {
                        tokenList = "\" ";
                    }

                } else if (tok.equals(" ")) {
                    if (tokStarted) {
                        retVal = tokBuf.toString();
                        tokStarted = false;
                        tokBuf = new StringBuffer(10);
                        exit = true;
                    }
                } else {
                    tokStarted = true;
                    tokBuf.append(tok.trim());
                }
            }

            // Handle case where end of token stream and
            // still got data
            if ((!exit) && (tokStarted)) {
                retVal = tokBuf.toString();
            }
        }

        return retVal;
    }

    /**
     * <p>
     * Loads the properties in the system property file associated with the
     * framework installation into <tt>System.setProperty()</tt>. These properties
     * are not directly used by the framework in anyway. By default, the system
     * property file is located in the <tt>conf/</tt> directory of the Felix
     * installation directory and is called "<tt>system.properties</tt>". The
     * installation directory of Felix is assumed to be the parent directory of
     * the <tt>felix.jar</tt> file as found on the system class path property.
     * The precise file from which to load system properties can be set by
     * initializing the "<tt>felix.system.properties</tt>" system property to an
     * arbitrary URL.
     * </p>
     */
    private void loadSystemProperties() {
        // The system properties file is either specified by a system
        // property or it is in the same directory as the Felix JAR file.
        // Try to load it from one of these places.

        // See if the property URL was specified as a property.
        URL propURL = null;
        try {
            File file = new File(new File(servicemixBase, "etc"), SYSTEM_PROPERTIES_FILE_NAME);
            propURL = file.toURL();
        }
        catch (MalformedURLException ex) {
            System.err.print("Main: " + ex);
            return;
        }

        // Read the properties file.
        Properties props = new Properties();
        InputStream is = null;
        try {
            is = propURL.openConnection().getInputStream();
            props.load(is);
            is.close();
        }
        catch (FileNotFoundException ex) {
            // Ignore file not found.
        }
        catch (Exception ex) {
            System.err.println(
                    "Main: Error loading system properties from " + propURL);
            System.err.println("Main: " + ex);
            try {
                if (is != null) is.close();
            }
            catch (IOException ex2) {
                // Nothing we can do.
            }
            return;
        }

        // Perform variable substitution on specified properties.
        for (Enumeration e = props.propertyNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            System.setProperty(name,
                    substVars(props.getProperty(name), name, null, null));
        }
    }

    /**
     * <p>
     * Loads the configuration properties in the configuration property file
     * associated with the framework installation; these properties
     * are accessible to the framework and to bundles and are intended
     * for configuration purposes. By default, the configuration property
     * file is located in the <tt>conf/</tt> directory of the Felix
     * installation directory and is called "<tt>config.properties</tt>".
     * The installation directory of Felix is assumed to be the parent
     * directory of the <tt>felix.jar</tt> file as found on the system class
     * path property. The precise file from which to load configuration
     * properties can be set by initializing the "<tt>felix.config.properties</tt>"
     * system property to an arbitrary URL.
     * </p>
     *
     * @return A <tt>Properties</tt> instance or <tt>null</tt> if there was an error.
     */
    private Properties loadConfigProperties() {
        // The config properties file is either specified by a system
        // property or it is in the conf/ directory of the Felix
        // installation directory.  Try to load it from one of these
        // places.

            ArrayList<File> bundleDirs = new ArrayList<File>();

        // See if the property URL was specified as a property.
        URL configPropURL = null;
        URL startupPropURL = null;

        try {
            File file = new File(new File(servicemixBase, "etc"), CONFIG_PROPERTIES_FILE_NAME);
            configPropURL = file.toURL();

            file = new File(new File(servicemixBase, "etc"), STARTUP_PROPERTIES_FILE_NAME);
            startupPropURL = file.toURL();

            if (servicemixBase.equals(servicemixHome)) {
                bundleDirs.add(new File(servicemixHome, "system"));
            } else {
                bundleDirs.add(new File(servicemixBase, "system"));
                bundleDirs.add(new File(servicemixHome, "system"));
            }

        }
        catch (MalformedURLException ex) {
            System.err.print("Main: " + ex);
            return null;
        }


        Properties configProps = loadPropertiesFile(configPropURL);
        Properties startupProps = loadPropertiesFile(startupPropURL);

        String locations = configProps.getProperty(BUNDLE_LOCATIONS);

        if (locations != null) {
            StringTokenizer st = new StringTokenizer(locations, "\" ", true);
            if (st.countTokens() > 0) {
                String location = null;
                do {
                    location = nextLocation(st);
                    if (location != null) {
                        File f = new File(location);
                        if (f.exists() && f.isDirectory()) {
                            bundleDirs.add(f);
                        } else {
                            System.err.println("Bundle location " + location
                                    + " does not exist or is not a directory.");
                        }
                    }
                }

                while (location != null);
            }
        }

        // Perform variable substitution for system properties.
        for (Enumeration e = configProps.propertyNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            configProps.setProperty(name,
                    substVars(configProps.getProperty(name), name, null, configProps));
        }

        // Mutate properties
        Main.processConfigurationProperties(configProps, startupProps, bundleDirs);

        return configProps;
    }

    private static Properties loadPropertiesFile(URL configPropURL) {
        // Read the properties file.
        Properties configProps = new Properties();
        InputStream is = null;
        try {
            is = configPropURL.openConnection().getInputStream();
            configProps.load(is);
            is.close();
        }
        catch (FileNotFoundException ex) {
            // Ignore file not found.
        }
        catch (Exception ex) {
            System.err.println(
                    "Error loading config properties from " + configPropURL);
            System.err.println("Main: " + ex);
            try {
                if (is != null) is.close();
            }
            catch (IOException ex2) {
                // Nothing we can do.
            }
            return null;
        }
        return configProps;
    }

    private static void copySystemProperties(Properties configProps) {
        for (Enumeration e = System.getProperties().propertyNames();
             e.hasMoreElements();) {
            String key = (String) e.nextElement();
            if (key.startsWith("felix.") ||
                    key.startsWith("servicemix.") ||
                    key.equals("org.osgi.framework.system.packages") ||
                    key.equals("org.osgi.framework.bootdelegation")) {
                configProps.setProperty(key, System.getProperty(key));
            }
        }
    }

    /**
     * Process properties to customize default felix behavior
     *
     * @param startupProps
     */
    private static void processConfigurationProperties(Properties props, Properties startupProps, ArrayList<File> bundleDirs) {
        if (bundleDirs == null) {
            return;
        }
        if ("all".equals(props.getProperty(PROPERTY_AUTO_START, "").trim())) {
            props.remove(PROPERTY_AUTO_START);
            ArrayList<File> jars = new ArrayList<File>();

            // We should start all the bundles in the system dir.
            for (File bundleDir : bundleDirs) {
                findJars(bundleDir, jars);
            }

            StringBuffer sb = new StringBuffer();

            for (File jar : jars) {
                try {
                    sb.append("\"").append(jar.toURL().toString()).append("\" ");
                } catch (MalformedURLException e) {
                    System.err.print("Ignoring " + jar.toString() + " (" + e + ")");
                }
            }

            props.setProperty(PROPERTY_AUTO_START, sb.toString());

        } else if (STARTUP_PROPERTIES_FILE_NAME.equals(props.getProperty(PROPERTY_AUTO_START, "").trim())) {
            props.remove(PROPERTY_AUTO_START);
            // We should start the bundles in the startup.properties file.
            HashMap<Integer, StringBuffer> levels = new HashMap<Integer, StringBuffer>();
            for (Iterator iterator = startupProps.keySet().iterator(); iterator.hasNext();) {
                String name = (String) iterator.next();
                File file = findFile(bundleDirs, name);

                if (file != null) {
                    Integer level;
                    try {
                        level = new Integer(startupProps.getProperty(name).trim());
                    } catch (NumberFormatException e1) {
                        System.err.print("Ignoring " + file.toString() + " (run level must be an integer)");
                        continue;
                    }
                    StringBuffer sb = levels.get(level);
                    if (sb == null) {
                        sb = new StringBuffer(256);
                        levels.put(level, sb);
                    }
                    try {
                        sb.append("\"").append(file.toURL().toString()).append("|").append(name).append("\" ");
                    } catch (MalformedURLException e) {
                        System.err.print("Ignoring " + file.toString() + " (" + e + ")");
                    }
                } else {
                    System.err.println("Bundle listed in " + STARTUP_PROPERTIES_FILE_NAME + " configuration not found: " + name);
                }
            }

            for (Map.Entry<Integer, StringBuffer> entry : levels.entrySet()) {
                props.setProperty(PROPERTY_AUTO_START + "." + entry.getKey(), entry.getValue().toString());
            }
        }

    }

    private static File findFile(ArrayList<File> bundleDirs, String name) {
        for (File bundleDir : bundleDirs) {
            File file = findFile(bundleDir, name);
            if (file != null) {
                return file;
            }
        }
        return null;
    }

    private static File findFile(File dir, String name) {
        File theFile = new File(dir, name);

        if (theFile.exists() && !theFile.isDirectory()) {
            return theFile;
        }

        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                return findFile(file, name);
            }
        }

        return null;
    }

    private static void findJars(File dir, ArrayList<File> jars) {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                findJars(file, jars);
            } else {
                if (file.toString().endsWith(".jar")) {
                    jars.add(file);
                }
            }
        }
    }

    private static final String DELIM_START = "${";
    private static final String DELIM_STOP = "}";

    /**
     * <p>
     * This method performs property variable substitution on the
     * specified value. If the specified value contains the syntax
     * <tt>${&lt;prop-name&gt;}</tt>, where <tt>&lt;prop-name&gt;</tt>
     * refers to either a configuration property or a system property,
     * then the corresponding property value is substituted for the variable
     * placeholder. Multiple variable placeholders may exist in the
     * specified value as well as nested variable placeholders, which
     * are substituted from inner most to outer most. Configuration
     * properties override system properties.
     * </p>
     *
     * @param val         The string on which to perform property substitution.
     * @param currentKey  The key of the property being evaluated used to
     *                    detect cycles.
     * @param cycleMap    Map of variable references used to detect nested cycles.
     * @param configProps Set of configuration properties.
     * @return The value of the specified string after system property substitution.
     * @throws IllegalArgumentException If there was a syntax error in the
     *                                  property placeholder syntax or a recursive variable reference.
     */
    public static String substVars(String val, String currentKey,
                                    Map<String, String> cycleMap, Properties configProps)
            throws IllegalArgumentException {
        // If there is currently no cycle map, then create
        // one for detecting cycles for this invocation.
        if (cycleMap == null) {
            cycleMap = new HashMap<String, String>();
        }

        // Put the current key in the cycle map.
        cycleMap.put(currentKey, currentKey);

        // Assume we have a value that is something like:
        // "leading ${foo.${bar}} middle ${baz} trailing"

        // Find the first ending '}' variable delimiter, which
        // will correspond to the first deepest nested variable
        // placeholder.
        int stopDelim = val.indexOf(DELIM_STOP);

        // Find the matching starting "${" variable delimiter
        // by looping until we find a start delimiter that is
        // greater than the stop delimiter we have found.
        int startDelim = val.indexOf(DELIM_START);
        while (stopDelim >= 0) {
            int idx = val.indexOf(DELIM_START, startDelim + DELIM_START.length());
            if ((idx < 0) || (idx > stopDelim)) {
                break;
            } else if (idx < stopDelim) {
                startDelim = idx;
            }
        }

        // If we do not have a start or stop delimiter, then just
        // return the existing value.
        if ((startDelim < 0) && (stopDelim < 0)) {
            return val;
        }
        // At this point, we found a stop delimiter without a start,
        // so throw an exception.
        else if (((startDelim < 0) || (startDelim > stopDelim))
                && (stopDelim >= 0)) {
            throw new IllegalArgumentException(
                    "stop delimiter with no start delimiter: "
                            + val);
        }

        // At this point, we have found a variable placeholder so
        // we must perform a variable substitution on it.
        // Using the start and stop delimiter indices, extract
        // the first, deepest nested variable placeholder.
        String variable =
                val.substring(startDelim + DELIM_START.length(), stopDelim);

        // Verify that this is not a recursive variable reference.
        if (cycleMap.get(variable) != null) {
            throw new IllegalArgumentException(
                    "recursive variable reference: " + variable);
        }

        // Get the value of the deepest nested variable placeholder.
        // Try to configuration properties first.
        String substValue = (configProps != null)
                ? configProps.getProperty(variable, null)
                : null;
        if (substValue == null) {
            // Ignore unknown property values.
            substValue = System.getProperty(variable, "");
        }

        // Remove the found variable from the cycle map, since
        // it may appear more than once in the value and we don't
        // want such situations to appear as a recursive reference.
        cycleMap.remove(variable);

        // Append the leading characters, the substituted value of
        // the variable, and the trailing characters to get the new
        // value.
        val = val.substring(0, startDelim)
                + substValue
                + val.substring(stopDelim + DELIM_STOP.length(), val.length());

        // Now perform substitution again, since there could still
        // be substitutions to make.
        val = substVars(val, currentKey, cycleMap, configProps);

        // Return the value.
        return val;
    }

    /* (non-Javadoc)
      * @see org.apache.servicemix.main.MainService#getArgs()
      */
    public String[] getArgs() {
        return args;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public File getServicemixHome() {
        return servicemixHome;
    }

    public File getServicemixBase() {
        return servicemixBase;
    }

    public void lock(Properties props) throws Exception {
        if (Boolean.parseBoolean(props.getProperty(PROPERTY_USE_LOCK, "true"))) {
            String clz = props.getProperty(PROPERTY_LOCK_CLASS, PROPERTY_LOCK_CLASS_DEFAULT);
            lock = (Lock) Class.forName(clz).getConstructor(Properties.class).newInstance(props);
            boolean lockLogged = false;
            for (;;) {
                if (lock.lock()) {
                    if (lockLogged) {
                        System.out.println("Lock acquired.");
                    }
                    break;
                } else if (!lockLogged) {
                    System.out.println("Waiting for the lock ...");
                    lockLogged = true;
                }
                Thread.sleep(1000);
            }
        }
    }

    public void unlock() throws Exception {
        if (lock != null) {
            lock.release();
        }
    }
}

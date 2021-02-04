/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.openejb.assembler.classic;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.EntityManagerFactory;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ManagedConnectionFactory;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;

import org.apache.openejb.Container;
import org.apache.openejb.DeploymentInfo;
import org.apache.openejb.EnvProps;
import org.apache.openejb.Injection;
import org.apache.openejb.OpenEJB;
import org.apache.openejb.OpenEJBException;
import org.apache.openejb.core.ConnectorReference;
import org.apache.openejb.core.CoreContainerSystem;
import org.apache.openejb.core.CoreDeploymentInfo;
import org.apache.openejb.core.SimpleTransactionSynchronizationRegistry;
import org.apache.openejb.core.TemporaryClassLoader;
import org.apache.openejb.javaagent.Agent;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.persistence.GlobalJndiDataSourceResolver;
import org.apache.openejb.persistence.JtaEntityManagerRegistry;
import org.apache.openejb.persistence.PersistenceClassLoaderHandler;
import org.apache.openejb.persistence.PersistenceDeployer;
import org.apache.openejb.persistence.PersistenceDeployerException;
import org.apache.openejb.spi.SecurityService;
import org.apache.openejb.util.Logger;
import org.apache.openejb.util.OpenEJBErrorHandler;
import org.apache.openejb.util.SafeToolkit;
import org.apache.openejb.util.proxy.ProxyFactory;
import org.apache.openejb.util.proxy.ProxyManager;
import org.apache.xbean.finder.ResourceFinder;
import org.apache.xbean.recipe.ObjectRecipe;
import org.apache.xbean.recipe.StaticRecipe;

public class Assembler extends AssemblerTool implements org.apache.openejb.spi.Assembler {

    public static final Logger logger = Logger.getInstance("OpenEJB.startup", Assembler.class.getPackage().getName());


    private final CoreContainerSystem containerSystem;
    private final PersistenceClassLoaderHandler persistenceClassLoaderHandler;
    private final JndiBuilder jndiBuilder;
    private TransactionManager transactionManager;
    private SecurityService securityService;

    public org.apache.openejb.spi.ContainerSystem getContainerSystem() {
        return containerSystem;
    }

    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    public SecurityService getSecurityService() {
        return securityService;
    }

    protected SafeToolkit toolkit = SafeToolkit.getToolkit("Assembler");
    protected OpenEjbConfiguration config;

    public Assembler() {
        persistenceClassLoaderHandler = new PersistenceClassLoaderHandlerImpl();

        installNaming();

        containerSystem = new CoreContainerSystem();

        jndiBuilder = new JndiBuilder(containerSystem.getJNDIContext());
    }

    public void init(Properties props) throws OpenEJBException {
        this.props = props;
        /* Get Configuration ////////////////////////////*/
        String className = props.getProperty(EnvProps.CONFIGURATION_FACTORY);
        if (className == null) {
            className = props.getProperty("openejb.configurator", "org.apache.openejb.alt.config.ConfigurationFactory");
        }

        OpenEjbConfigurationFactory configFactory = (OpenEjbConfigurationFactory) toolkit.newInstance(className);
        configFactory.init(props);
        config = configFactory.getOpenEjbConfiguration();
        /*\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\*/
    }

    public static void installNaming() {
        /* Add IntraVM JNDI service /////////////////////*/
        Properties systemProperties = System.getProperties();
        synchronized (systemProperties) {
            String str = systemProperties.getProperty(Context.URL_PKG_PREFIXES);
            String naming = "org.apache.openejb.core.ivm.naming";
            if (str == null) {
                str = naming;
            } else if (str.indexOf(naming) == -1) {
                str = naming + ":" + str;
            }
            systemProperties.setProperty(Context.URL_PKG_PREFIXES, str);
        }
        /*\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\*/}

    private static ThreadLocal<Map<String, Object>> context = new ThreadLocal<Map<String, Object>>();

    public static void setContext(Map<String, Object> map) {
        context.set(map);
    }

    public static Map<String, Object> getContext() {
        Map<String, Object> map = context.get();
        if (map == null) {
            map = new HashMap<String, Object>();
            context.set(map);
        }
        return map;
    }

    public void build() throws OpenEJBException {
        setContext(new HashMap<String, Object>());
        try {
            buildContainerSystem(config);
        } catch (OpenEJBException ae) {
            /* OpenEJBExceptions contain useful information and are debbugable.
             * Let the exception pass through to the top and be logged.
             */
            throw ae;
        } catch (Exception e) {
            /* General Exceptions at this level are too generic and difficult to debug.
             * These exceptions are considered unknown bugs and are fatal.
             * If you get an error at this level, please trap and handle the error
             * where it is most relevant.
             */
            OpenEJBErrorHandler.handleUnknownError(e, "Assembler");
            throw new OpenEJBException(e);
        } finally {
            context.set(null);
        }
    }

    /////////////////////////////////////////////////////////////////////
    ////
    ////    Public Methods Used for Assembly
    ////
    /////////////////////////////////////////////////////////////////////

    /**
     * When given a complete OpenEjbConfiguration graph this method
     * will construct an entire container system and return a reference to that
     * container system, as ContainerSystem instance.
     * <p/>
     * This method leverage the other assemble and apply methods which
     * can be used independently.
     * <p/>
     * Assembles and returns the {@link org.apache.openejb.core.CoreContainerSystem} using the
     * information from the {@link OpenEjbConfiguration} object passed in.
     * <pre>
     * This method performs the following actions(in order):
     * <p/>
     * 1  Assembles ProxyFactory
     * 2  Assembles External JNDI Contexts
     * 3  Assembles TransactionService
     * 4  Assembles SecurityService
     * 5  Assembles ConnectionManagers
     * 6  Assembles Connectors
     * 7  Assembles Containers
     * 8  Assembles Applications
     * </pre>
     *
     * @param configInfo
     * @throws Exception if there was a problem constructing the ContainerSystem.
     * @throws Exception
     * @see OpenEjbConfiguration
     */
    public void buildContainerSystem(OpenEjbConfiguration configInfo) throws Exception {


        ContainerSystemInfo containerSystemInfo = configInfo.containerSystem;

        /*[1] Assemble ProxyFactory //////////////////////////////////////////

            This operation must take place first because of interdependencies.
            As DeploymentInfo objects are registered with the ContainerSystem using the
            ContainerSystem.addDeploymentInfo() method, they are also added to the JNDI
            Naming Service for OpenEJB.  This requires that a proxy for the deployed bean's
            EJBHome be created. The proxy requires that the default proxy factory is set.
        */
        createProxyFactory(configInfo.facilities.intraVmServer);

        for (JndiContextInfo contextInfo : configInfo.facilities.remoteJndiContexts) {
            createExternalContext(contextInfo);
        }

        createTransactionManager(configInfo.facilities.transactionService);

        createSecurityService(configInfo.facilities.securityService);

        for (ConnectionManagerInfo connectionManagerInfo : configInfo.facilities.connectionManagers) {
            createConnectionManager(connectionManagerInfo);
        }

        for (ConnectorInfo connectorInfo : configInfo.facilities.connectors) {
            createConnector(connectorInfo);
        }

//        AssemblerTool.RoleMapping roleMapping = new AssemblerTool.RoleMapping(configInfo.facilities.securityService.roleMappings);

        // Containers
        for (ContainerInfo serviceInfo : containerSystemInfo.containers) {
            createContainer(serviceInfo);
        }

        for (AppInfo appInfo : containerSystemInfo.applications) {

            createApplication(appInfo, createAppClassLoader(appInfo));
        }
    }

    public void createEjbJar(EjbJarInfo ejbJar) throws NamingException, IOException, OpenEJBException {
        AppInfo appInfo = new AppInfo();
        appInfo.ejbJars.add(ejbJar);
        createApplication(appInfo);
    }

    public void createEjbJar(EjbJarInfo ejbJar, ClassLoader classLoader) throws NamingException, IOException, OpenEJBException {
        AppInfo appInfo = new AppInfo();
        appInfo.ejbJars.add(ejbJar);
        createApplication(appInfo, classLoader);
    }

    public void createClient(ClientInfo clientInfo) throws NamingException, IOException, OpenEJBException {
        AppInfo appInfo = new AppInfo();
        appInfo.clients.add(clientInfo);
        createApplication(appInfo);
    }

    public void createClient(ClientInfo clientInfo, ClassLoader classLoader) throws NamingException, IOException, OpenEJBException {
        AppInfo appInfo = new AppInfo();
        appInfo.clients.add(clientInfo);
        createApplication(appInfo, classLoader);
    }

    public void createApplication(AppInfo appInfo) throws OpenEJBException, IOException, NamingException {
        createApplication(appInfo, createAppClassLoader(appInfo));
    }

    public void createApplication(AppInfo appInfo, ClassLoader classLoader) throws OpenEJBException, IOException, NamingException {

        // JPA - Persistence Units MUST be processed first since they will add ClassFileTransformers
        // to the class loader which must be added before any classes are loaded
        HashMap<String, Map<String, EntityManagerFactory>> allFactories = new HashMap<String, Map<String, EntityManagerFactory>>();
        for (EjbJarInfo ejbJar : appInfo.ejbJars) {
            try {
                URL url = new File(ejbJar.jarPath).toURL();
                ResourceFinder resourceFinder = new ResourceFinder("", classLoader, url);

                PersistenceDeployer persistenceDeployer = new PersistenceDeployer(new GlobalJndiDataSourceResolver(null), persistenceClassLoaderHandler);
                Map<String, EntityManagerFactory> factories = persistenceDeployer.deploy(resourceFinder.findAll("META-INF/persistence.xml"), classLoader);
                allFactories.put(ejbJar.jarPath, factories);
            } catch (PersistenceDeployerException e1) {
                throw new OpenEJBException(e1);
            } catch (IOException e) {
                throw new OpenEJBException(e);
            }
        }

        // EJB
        EjbJarBuilder ejbJarBuilder = new EjbJarBuilder(props, classLoader);
        for (EjbJarInfo ejbJar : appInfo.ejbJars) {
            HashMap<String, DeploymentInfo> deployments = ejbJarBuilder.build(ejbJar, allFactories);
            RoleMapping roleMapping = new RoleMapping(new ArrayList());
            for (DeploymentInfo deploymentInfo : deployments.values()) {
                applyMethodPermissions((CoreDeploymentInfo) deploymentInfo, ejbJar.methodPermissions, roleMapping);
                applyTransactionAttributes((CoreDeploymentInfo) deploymentInfo, ejbJar.methodTransactions);
                containerSystem.addDeployment(deploymentInfo);
                jndiBuilder.bind(deploymentInfo);
            }

            for (EnterpriseBeanInfo beanInfo : ejbJar.enterpriseBeans) {
                CoreDeploymentInfo deployment = (CoreDeploymentInfo) deployments.get(beanInfo.ejbDeploymentId);
                applySecurityRoleReference(deployment, beanInfo, roleMapping);
            }
        }

        // App Client
        for (ClientInfo clientInfo : appInfo.clients) {
            JndiEncBuilder jndiEncBuilder = new JndiEncBuilder(clientInfo.jndiEnc);
            Context context = (Context) jndiEncBuilder.build().lookup("env");
            containerSystem.getJNDIContext().bind("java:openejb/client/" + clientInfo.moduleId + "/comp/env", context);
            if (clientInfo.codebase != null) {
                containerSystem.getJNDIContext().bind("java:openejb/client/" + clientInfo.moduleId + "/comp/path", clientInfo.codebase);
            }
            if (clientInfo.mainClass != null) {
                containerSystem.getJNDIContext().bind("java:openejb/client/" + clientInfo.moduleId + "/comp/mainClass", clientInfo.mainClass);
            }
            ArrayList<Injection> injections = new ArrayList<Injection>();
            JndiEncInfo jndiEnc = clientInfo.jndiEnc;
            for (EjbReferenceInfo info : jndiEnc.ejbReferences) {
                for (InjectionInfo target : info.targets) {
                    try {
                        Class targetClass = classLoader.loadClass(target.className);
                        Injection injection = new Injection(info.referenceName, target.propertyName, targetClass);
                        injections.add(injection);
                    } catch (ClassNotFoundException e) {
                        logger.error("Injection Target invalid: class=" + target.className + ", name=" + target.propertyName + ".  Exception: " + e.getMessage(), e);
                    }
                }
            }
            containerSystem.getJNDIContext().bind("java:openejb/client/" + clientInfo.moduleId + "/comp/injections", injections);
        }
    }

    public ClassLoader createAppClassLoader(AppInfo appInfo) throws OpenEJBException, IOException {
        List<URL> jars = new ArrayList<URL>();
        for (EjbJarInfo info : appInfo.ejbJars) {
            jars.add(toUrl(info.jarPath));
        }
        for (ClientInfo info : appInfo.clients) {
            jars.add(toUrl(info.codebase));
        }
        for (String jarPath : appInfo.libs) {
            jars.add(toUrl(jarPath));
        }

        // Generate the cmp2 concrete subclasses
        Cmp2Builder cmp2Builder = new Cmp2Builder(appInfo);
        File generatedJar = cmp2Builder.getJarFile();
        if (generatedJar != null) {
            jars.add(generatedJar.toURL());
        }

        // Create the class loader
        ClassLoader classLoader = new URLClassLoader(jars.toArray(new URL[]{}), OpenEJB.class.getClassLoader());
        return classLoader;
    }

    public void createExternalContext(JndiContextInfo contextInfo) throws OpenEJBException, NamingException {
        InitialContext result;
        try {
            InitialContext ic = new InitialContext(contextInfo.properties);
            result = ic;
        } catch (NamingException ne) {

            throw new OpenEJBException("The remote JNDI EJB references for remote-jndi-contexts = " + contextInfo.id + "+ could not be resolved.", ne);
        }
        InitialContext cntx = result;
        containerSystem.getJNDIContext().bind("java:openejb/remote_jndi_contexts/" + contextInfo.id, cntx);
    }

    public void createContainer(ContainerInfo serviceInfo) throws OpenEJBException, NamingException {

        ObjectRecipe serviceRecipe = new ObjectRecipe(serviceInfo.className, serviceInfo.factoryMethod, serviceInfo.constructorArgs.toArray(new String[0]), null);
        serviceRecipe.setAllProperties(serviceInfo.properties);

        serviceRecipe.setProperty("id", new StaticRecipe(serviceInfo.id));
        serviceRecipe.setProperty("transactionManager", new StaticRecipe(props.get(TransactionManager.class.getName())));
        serviceRecipe.setProperty("securityService", new StaticRecipe(props.get(SecurityService.class.getName())));

        Object service = serviceRecipe.create();

        Class interfce = serviceInterfaces.get(serviceInfo.serviceType);
        checkImplementation(interfce, service.getClass(), serviceInfo.serviceType, serviceInfo.id);

        this.containerSystem.getJNDIContext().bind("java:openejb/" + serviceInfo.serviceType + "/" + serviceInfo.id, service);

        SystemInstance.get().setComponent(interfce, service);

        props.put(interfce.getName(), service);
        props.put(serviceInfo.serviceType, service);
        props.put(serviceInfo.id, service);

        containerSystem.addContainer(serviceInfo.id, (Container) service);
    }

    public void createProxyFactory(IntraVmServerInfo serviceInfo) throws OpenEJBException, NamingException {

        ObjectRecipe serviceRecipe = new ObjectRecipe(serviceInfo.className, serviceInfo.factoryMethod, serviceInfo.constructorArgs.toArray(new String[0]), null);
        serviceRecipe.setAllProperties(serviceInfo.properties);

        Object service = serviceRecipe.create();

        Class interfce = serviceInterfaces.get(serviceInfo.serviceType);
        checkImplementation(interfce, service.getClass(), serviceInfo.serviceType, serviceInfo.id);

        ProxyManager.registerFactory(serviceInfo.id, (ProxyFactory) service);
        ProxyManager.setDefaultFactory(serviceInfo.id);

        this.containerSystem.getJNDIContext().bind("java:openejb/" + serviceInfo.serviceType + "/" + serviceInfo.id, service);

        SystemInstance.get().setComponent(interfce, service);

        getContext().put(interfce.getName(), service);

        props.put(interfce.getName(), service);
        props.put(serviceInfo.serviceType, service);
        props.put(serviceInfo.id, service);
    }

    public void createConnector(ConnectorInfo conInfo) throws OpenEJBException, NamingException {

        ManagedConnectionFactoryInfo serviceInfo = conInfo.managedConnectionFactory;

        ObjectRecipe serviceRecipe = new ObjectRecipe(serviceInfo.className, serviceInfo.factoryMethod, serviceInfo.constructorArgs.toArray(new String[0]), null);
        serviceRecipe.setAllProperties(serviceInfo.properties);

        Object service = serviceRecipe.create();

        Class interfce = serviceInterfaces.get(serviceInfo.serviceType);
        checkImplementation(interfce, service.getClass(), serviceInfo.serviceType, serviceInfo.id);

        ConnectionManager connectionManager = (ConnectionManager) props.get(conInfo.connectionManagerId);
        if (connectionManager == null) {
            throw new RuntimeException("Invalid connection manager specified for connector identity = " + conInfo.connectorId);
        }

        ManagedConnectionFactory managedConnectionFactory = (ManagedConnectionFactory) service;

        ConnectorReference reference = new ConnectorReference(connectionManager, managedConnectionFactory);

        containerSystem.getJNDIContext().bind("java:openejb/" + serviceInfo.serviceType + "/" + conInfo.connectorId, reference);
    }

    public void createConnectionManager(ConnectionManagerInfo serviceInfo) throws OpenEJBException, java.lang.reflect.InvocationTargetException, IllegalAccessException, NoSuchMethodException, NamingException {

        ObjectRecipe serviceRecipe = new ObjectRecipe(serviceInfo.className, serviceInfo.factoryMethod, serviceInfo.constructorArgs.toArray(new String[0]), null);
        serviceRecipe.setAllProperties(serviceInfo.properties);

        Object object = props.get("TransactionManager");
        serviceRecipe.setProperty("transactionManager", new StaticRecipe(object));

        Object service = serviceRecipe.create();

        Class interfce = serviceInterfaces.get(serviceInfo.serviceType);
        checkImplementation(interfce, service.getClass(), serviceInfo.serviceType, serviceInfo.id);

        this.containerSystem.getJNDIContext().bind("java:openejb/" + serviceInfo.serviceType + "/" + serviceInfo.id, service);

        SystemInstance.get().setComponent(interfce, service);

        getContext().put(interfce.getName(), service);

        props.put(interfce.getName(), service);
        props.put(serviceInfo.serviceType, service);
        props.put(serviceInfo.id, service);

    }

    public void createSecurityService(ServiceInfo serviceInfo) throws Exception {

        ObjectRecipe serviceRecipe = new ObjectRecipe(serviceInfo.className, serviceInfo.factoryMethod, serviceInfo.constructorArgs.toArray(new String[0]), null);
        serviceRecipe.setAllProperties(serviceInfo.properties);

        Object service = serviceRecipe.create();

        Class interfce = serviceInterfaces.get(serviceInfo.serviceType);
        checkImplementation(interfce, service.getClass(), serviceInfo.serviceType, serviceInfo.id);

        this.containerSystem.getJNDIContext().bind("java:openejb/" + serviceInfo.serviceType, service);

        SystemInstance.get().setComponent(interfce, service);

        getContext().put(interfce.getName(), service);

        props.put(interfce.getName(), service);
        props.put(serviceInfo.serviceType, service);
        props.put(serviceInfo.id, service);

        this.securityService = (SecurityService) service;
    }

    public void createTransactionManager(TransactionServiceInfo serviceInfo) throws NamingException, OpenEJBException {

        ObjectRecipe serviceRecipe = new ObjectRecipe(serviceInfo.className, serviceInfo.factoryMethod, serviceInfo.constructorArgs.toArray(new String[0]), null);
        serviceRecipe.setAllProperties(serviceInfo.properties);

        Object service = serviceRecipe.create();

        Class interfce = serviceInterfaces.get(serviceInfo.serviceType);
        checkImplementation(interfce, service.getClass(), serviceInfo.serviceType, serviceInfo.id);

        this.containerSystem.getJNDIContext().bind("java:openejb/" + serviceInfo.serviceType, service);

        SystemInstance.get().setComponent(interfce, service);

        getContext().put(interfce.getName(), service);

        props.put(interfce.getName(), service);
        props.put(serviceInfo.serviceType, service);
        props.put(serviceInfo.id, service);

        this.transactionManager = (TransactionManager) service;

        // todo find a better place for this

        // TransactionSynchronizationRegistry
        TransactionSynchronizationRegistry synchronizationRegistry ;
        if (transactionManager instanceof TransactionSynchronizationRegistry) {
            synchronizationRegistry = (TransactionSynchronizationRegistry) transactionManager;
        } else {
            // todo this sould be built
            synchronizationRegistry = new SimpleTransactionSynchronizationRegistry(transactionManager);
        }
        Assembler.getContext().put(TransactionSynchronizationRegistry.class.getName(), synchronizationRegistry);
        SystemInstance.get().setComponent(TransactionSynchronizationRegistry.class, synchronizationRegistry);

        // JtaEntityManagerRegistry
        // todo this sould be built
        JtaEntityManagerRegistry jtaEntityManagerRegistry = new JtaEntityManagerRegistry(synchronizationRegistry);
        Assembler.getContext().put(JtaEntityManagerRegistry.class.getName(), jtaEntityManagerRegistry);
        SystemInstance.get().setComponent(JtaEntityManagerRegistry.class, jtaEntityManagerRegistry);
    }

    private URL toUrl(String jarPath) throws OpenEJBException {
        try {
            return new File(jarPath).toURL();
        } catch (MalformedURLException e) {
            throw new OpenEJBException(messages.format("cl0001", jarPath, e.getMessage()));
        }
    }


    private static class PersistenceClassLoaderHandlerImpl implements PersistenceClassLoaderHandler {
        public void addTransformer(ClassLoader classLoader, ClassFileTransformer classFileTransformer) {
            Instrumentation instrumentation = Agent.getInstrumentation();
            if (instrumentation != null) {
                instrumentation.addTransformer(classFileTransformer);
            }
        }

        public ClassLoader getNewTempClassLoader(ClassLoader classLoader) {
            return new TemporaryClassLoader(classLoader);
        }
    }
}

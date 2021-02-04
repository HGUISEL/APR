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

import org.apache.openejb.Injection;
import org.apache.openejb.OpenEJBException;
import org.apache.openejb.InterfaceType;
import org.apache.openejb.spi.ContainerSystem;
import org.apache.openejb.core.CoreUserTransaction;
import org.apache.openejb.core.TransactionSynchronizationRegistryWrapper;
import org.apache.openejb.core.JndiFactory;
import org.apache.openejb.core.ivm.naming.CrossClassLoaderJndiReference;
import org.apache.openejb.core.ivm.naming.IntraVmJndiReference;
import org.apache.openejb.core.ivm.naming.JaxWsServiceReference;
import org.apache.openejb.core.ivm.naming.JndiReference;
import org.apache.openejb.core.ivm.naming.JndiUrlReference;
import org.apache.openejb.core.ivm.naming.PersistenceContextReference;
import org.apache.openejb.core.ivm.naming.Reference;
import org.apache.openejb.core.ivm.naming.SystemComponentReference;
import org.apache.openejb.core.ivm.naming.URLReference;
import org.apache.openejb.core.webservices.HandlerChainData;
import org.apache.openejb.core.webservices.PortRefData;
import org.apache.openejb.core.webservices.ServiceRefData;
import org.apache.openejb.loader.SystemInstance;
import org.apache.openejb.persistence.JtaEntityManager;
import org.apache.openejb.persistence.JtaEntityManagerRegistry;
import org.apache.openejb.util.LogCategory;
import org.apache.openejb.util.Logger;
import org.apache.openejb.core.timer.TimerServiceWrapper;
import org.omg.CORBA.ORB;

import javax.ejb.EJBContext;
import javax.ejb.TimerService;
import javax.ejb.spi.HandleDelegate;
import javax.naming.Context;
import javax.naming.LinkRef;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.persistence.EntityManagerFactory;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceContext;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.io.Serializable;

/**
 * TODO: This class is essentially an over glorified sym-linker.  The names
 * we were linking to are no longer guaranteed to be what we assume them to
 * be.  We need to come up with a different internal naming structure for
 * the global JNDI and finally create the default which will be the default
 * symlinked version of all the components.
 */
public class JndiEncBuilder {
    public static final Logger logger = Logger.getInstance(LogCategory.OPENEJB_STARTUP, JndiEncBuilder.class.getPackage().getName());

    private final boolean beanManagedTransactions;
    private final JndiEncInfo jndiEnc;
    private final URI moduleUri;
    private final List<Injection> injections;
    private final ClassLoader classLoader;

    private boolean useCrossClassLoaderRef = true;
    private boolean client = false;

    public JndiEncBuilder(JndiEncInfo jndiEnc, List<Injection> injections, String moduleId, ClassLoader classLoader) throws OpenEJBException {
        this(jndiEnc, injections, null, moduleId, classLoader);
    }

    public JndiEncBuilder(JndiEncInfo jndiEnc, List<Injection> injections, String transactionType, String moduleId, ClassLoader classLoader) throws OpenEJBException {
        this.jndiEnc = jndiEnc;
        this.injections = injections;
        beanManagedTransactions = transactionType != null && transactionType.equalsIgnoreCase("Bean");

        try {
            moduleUri = moduleId == null? null: new URI(moduleId);
        } catch (URISyntaxException e) {
            throw new OpenEJBException(e);
        }
        this.classLoader = classLoader;
    }

    public boolean isUseCrossClassLoaderRef() {
        return useCrossClassLoaderRef;
    }

    public void setUseCrossClassLoaderRef(boolean useCrossClassLoaderRef) {
        this.useCrossClassLoaderRef = useCrossClassLoaderRef;
    }

    public boolean isClient() {
        return client;
    }

    public void setClient(boolean client) {
        this.client = client;
    }

    public Context build(boolean isComp) throws OpenEJBException {
        JndiFactory jndiFactory = SystemInstance.get().getComponent(JndiFactory.class);

        if (SystemInstance.get().hasProperty("openejb.geronimo")){
            return jndiFactory.createComponentContext(new HashMap());
        }

        Map<String, Object> bindings = buildMap();
        if (isComp) {
            addSpecialCompBindings(bindings);
        }

        return jndiFactory.createComponentContext(bindings);
    }

    public Map<String, Object> buildMap() throws OpenEJBException {
        Map<String, Object> bindings = new HashMap<String, Object>();

        // get JtaEntityManagerRegistry
        JtaEntityManagerRegistry jtaEntityManagerRegistry = SystemInstance.get().getComponent(JtaEntityManagerRegistry.class);

        for (EjbReferenceInfo referenceInfo : jndiEnc.ejbReferences) {

            Reference reference = null;

            if (referenceInfo.location != null) {
                reference = buildReferenceLocation(referenceInfo.location);
            } else if (referenceInfo.ejbDeploymentId == null){
                reference = new LazyEjbReference(new Ref(referenceInfo), moduleUri, useCrossClassLoaderRef);
            } else {
                String jndiName = "openejb/Deployment/" + JndiBuilder.format(referenceInfo.ejbDeploymentId, referenceInfo.interfaceClassName, InterfaceType.BUSINESS_REMOTE);
                if (useCrossClassLoaderRef && referenceInfo.externalReference) {
                    reference = new CrossClassLoaderJndiReference(jndiName);
                } else {
                    reference = new IntraVmJndiReference(jndiName);
                }
            }
            bindings.put(normalize(referenceInfo.referenceName), reference);
        }

        for (EjbReferenceInfo referenceInfo : jndiEnc.ejbLocalReferences) {
            Reference reference = null;

            if (referenceInfo.location != null) {
                reference = buildReferenceLocation(referenceInfo.location);
            } else if (referenceInfo.ejbDeploymentId == null){
                reference = new LazyEjbReference(new Ref(referenceInfo), moduleUri, false);
            } else {
                String jndiName = "openejb/Deployment/" + JndiBuilder.format(referenceInfo.ejbDeploymentId, referenceInfo.interfaceClassName, referenceInfo.localbean ? InterfaceType.LOCALBEAN : InterfaceType.BUSINESS_LOCAL);
                reference = new IntraVmJndiReference(jndiName);
            }
            bindings.put(normalize(referenceInfo.referenceName), reference);
        }

        for (EnvEntryInfo entry : jndiEnc.envEntries) {

            if (entry.location != null) {
                Reference reference = buildReferenceLocation(entry.location);
                bindings.put(normalize(entry.referenceName), reference);
                continue;
            }

            try {
                Class type = Class.forName(entry.type.trim());
                Object obj = null;
                if (type == String.class)
                    obj = new String(entry.value);
                else if (type == Double.class) {
                    obj = new Double(entry.value);
                } else if (type == Integer.class) {
                    obj = new Integer(entry.value);
                } else if (type == Long.class) {
                    obj = new Long(entry.value);
                } else if (type == Float.class) {
                    obj = new Float(entry.value);
                } else if (type == Short.class) {
                    obj = new Short(entry.value);
                } else if (type == Boolean.class) {
                    obj = new Boolean(entry.value);
                } else if (type == Byte.class) {
                    obj = new Byte(entry.value);
                } else if (type == Character.class) {
                    StringBuilder sb = new StringBuilder(entry.value + " ");
                    obj = new Character(sb.charAt(0));
                } else if (type == URL.class) {
                    obj = new URL(entry.value);
                } else {
                    throw new IllegalArgumentException("Invalid env-ref-type " + type);
                }

                bindings.put(normalize(entry.referenceName), obj);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("Invalid environment entry type: " + entry.type.trim() + " for entry: " + entry.referenceName, e);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("The env-entry-value for entry " + entry.referenceName + " was not recognizable as type " + entry.type + ". Received Message: " + e.getLocalizedMessage(), e);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("URL for reference " + entry.referenceName + " was not a valid URL: " + entry.value, e);
            }
        }

        for (ResourceReferenceInfo referenceInfo : jndiEnc.resourceRefs) {
            Reference reference = null;

            if ("java.net.URL".equals(referenceInfo.referenceType)) {
                if (referenceInfo.location != null) {
                    reference = buildReferenceLocation(referenceInfo.location);
                } else {
                    reference = new URLReference(referenceInfo.resourceID);
                }
            } else if (referenceInfo.location != null) {
                reference = buildReferenceLocation(referenceInfo.location);
            } else if (referenceInfo.resourceID != null) {
                String jndiName = "openejb/Resource/" + referenceInfo.resourceID;
                reference = new IntraVmJndiReference(jndiName);
            } else {
                String jndiName = "openejb/Resource/" + referenceInfo.referenceName;
                reference = new IntraVmJndiReference(jndiName);
            }
            bindings.put(normalize(referenceInfo.referenceName), reference);
        }

        for (ResourceEnvReferenceInfo referenceInfo : jndiEnc.resourceEnvRefs) {
            LinkRef linkRef = null;
            try {
                Class<?> type = Class.forName(referenceInfo.resourceEnvRefType, true, EJBContext.class.getClassLoader());
                if (EJBContext.class.isAssignableFrom(type)) {
                    String jndiName = "comp/EJBContext";
                    linkRef = new LinkRef(jndiName);
                    bindings.put(normalize(referenceInfo.referenceName), linkRef);
                    continue;
                } else if (WebServiceContext.class.equals(type)) {
                    String jndiName = "comp/WebServiceContext";
                    linkRef = new LinkRef(jndiName);
                    bindings.put(normalize(referenceInfo.referenceName), linkRef);
                    continue;
                } else if (TimerService.class.equals(type)) {
                    String jndiName = "comp/TimerService";
                    linkRef = new LinkRef(jndiName);
                    bindings.put(normalize(referenceInfo.referenceName), linkRef);
                    continue;
                }
            } catch (ClassNotFoundException e) {
            }

            Object reference = null;
            if (UserTransaction.class.getName().equals(referenceInfo.resourceEnvRefType)) {
                reference = new IntraVmJndiReference("comp/UserTransaction");
            } else if (referenceInfo.location != null){
                reference = buildReferenceLocation(referenceInfo.location);
            } else if (referenceInfo.resourceID != null) {
                String jndiName = "openejb/Resource/" + referenceInfo.resourceID;
                reference = new IntraVmJndiReference(jndiName);
            } else {
                String jndiName = "openejb/Resource/" + referenceInfo.referenceName;
                reference = new IntraVmJndiReference(jndiName);
            }
            if (reference != null) {
                bindings.put(normalize(referenceInfo.referenceName), reference);
            }
        }

        for (PersistenceUnitReferenceInfo referenceInfo : jndiEnc.persistenceUnitRefs) {
            if (referenceInfo.location != null){
                Reference reference = buildReferenceLocation(referenceInfo.location);
                bindings.put(normalize(referenceInfo.referenceName), reference);
                continue;
            }

            String jndiName = "openejb/PersistenceUnit/" + referenceInfo.unitId;
            Reference reference = new IntraVmJndiReference(jndiName);
            bindings.put(normalize(referenceInfo.referenceName), reference);
        }

        for (PersistenceContextReferenceInfo contextInfo : jndiEnc.persistenceContextRefs) {
            if (contextInfo.location != null){
                Reference reference = buildReferenceLocation(contextInfo.location);
                bindings.put(normalize(contextInfo.referenceName), reference);
                continue;
            }

            Context context = SystemInstance.get().getComponent(ContainerSystem.class).getJNDIContext();
            EntityManagerFactory factory;
            try {
                factory = (EntityManagerFactory) context.lookup("openejb/PersistenceUnit/" + contextInfo.unitId);
            } catch (NamingException e) {
                throw new OpenEJBException("PersistenceUnit '" + contextInfo.unitId + "' not found for EXTENDED ref '" + contextInfo.referenceName + "'");
            }

            JtaEntityManager jtaEntityManager = new JtaEntityManager(contextInfo.persistenceUnitName, jtaEntityManagerRegistry, factory, contextInfo.properties, contextInfo.extended);
            Reference reference = new PersistenceContextReference(jtaEntityManager);
            bindings.put(normalize(contextInfo.referenceName), reference);
        }

        for (ServiceReferenceInfo referenceInfo : jndiEnc.serviceRefs) {
            if (referenceInfo.location != null){
                Reference reference = buildReferenceLocation(referenceInfo.location);
                bindings.put(normalize(referenceInfo.referenceName), reference);
                continue;
            }

            // load service class which is used to construct the port
            Class<? extends Service> serviceClass = Service.class;
            if (referenceInfo.serviceType != null) {
                try {
                    serviceClass = classLoader.loadClass(referenceInfo.serviceType).asSubclass(Service.class);
                } catch (Exception e) {
                    throw new OpenEJBException("Could not load service type class "+ referenceInfo.serviceType, e);
                }
            }

            // load the reference class which is the ultimate type of the port
            Class<?> referenceClass = null;
            if (referenceInfo.referenceType != null) {
                try {
                    referenceClass = classLoader.loadClass(referenceInfo.referenceType);
                } catch (Exception e) {
                    throw new OpenEJBException("Could not load reference type class " + referenceInfo.referenceType, e);
                }
            }

            // if ref class is a subclass of Service, use it for the service class
            if (referenceClass != null && Service.class.isAssignableFrom(referenceClass)) {
                serviceClass = referenceClass.asSubclass(Service.class);
            }

            // determine the location of the wsdl file
            URL wsdlUrl = null;
            if (referenceInfo.wsdlFile != null) {
                try {
                    wsdlUrl = new URL(referenceInfo.wsdlFile);
                } catch (MalformedURLException e) {
                    wsdlUrl = classLoader.getResource(referenceInfo.wsdlFile);
                    if (wsdlUrl == null) {
                        logger.warning("Error obtaining WSDL: " + referenceInfo.wsdlFile, e);
                    }

                }
            }

            // port refs
            List<PortRefData> portRefs = new ArrayList<PortRefData>(referenceInfo.portRefs.size());
            for (PortRefInfo portRefInfo : referenceInfo.portRefs) {
                PortRefData portRef = new PortRefData();
                portRef.setQName(portRefInfo.qname);
                portRef.setServiceEndpointInterface(portRefInfo.serviceEndpointInterface);
                portRef.setEnableMtom(portRefInfo.enableMtom);
                portRef.getProperties().putAll(portRefInfo.properties);
                portRefs.add(portRef);
            }

            // create the handle chains
            List<HandlerChainData> handlerChains = null;
            if (!referenceInfo.handlerChains.isEmpty()) {
                handlerChains = WsBuilder.toHandlerChainData(referenceInfo.handlerChains, classLoader);
            }

            if (!client) {
                Reference reference = new JaxWsServiceReference(referenceInfo.id,
                        referenceInfo.serviceQName,
                        serviceClass, referenceInfo.portQName,
                        referenceClass,
                        wsdlUrl,
                        portRefs,
                        handlerChains,
                        injections);
                bindings.put(normalize(referenceInfo.referenceName), reference);
            } else {
                ServiceRefData serviceRefData = new ServiceRefData(referenceInfo.id,
                        referenceInfo.serviceQName,
                        serviceClass, referenceInfo.portQName,
                        referenceClass,
                        wsdlUrl,
                        handlerChains,
                        portRefs);
                bindings.put(normalize(referenceInfo.referenceName), serviceRefData);
            }
        }
        return bindings;
    }

    private void addSpecialCompBindings(Map<String, Object> bindings) {
        // bind TransactionManager
        TransactionManager transactionManager = SystemInstance.get().getComponent(TransactionManager.class);
        bindings.put("comp/TransactionManager", transactionManager);

        // bind TransactionSynchronizationRegistry
        bindings.put("comp/TransactionSynchronizationRegistry", new TransactionSynchronizationRegistryWrapper());

        bindings.put("comp/ORB", new SystemComponentReference(ORB.class));
        bindings.put("comp/HandleDelegate", new SystemComponentReference(HandleDelegate.class));

        // bind UserTransaction if bean managed transactions
        UserTransaction userTransaction = null;
        if (beanManagedTransactions) {
            userTransaction = new CoreUserTransaction(transactionManager);
            bindings.put("comp/UserTransaction", userTransaction);
        }

        // bind TimerService
        if (!isClient()) {
            bindings.put("comp/TimerService", new TimerServiceWrapper());
        }
    }

    public static boolean bindingExists(Context context, Name contextName) {
        try {
            return context.lookup(contextName) != null;
        } catch (NamingException e) {
        }
        return false;
    }

    private Reference buildReferenceLocation(ReferenceLocationInfo location) {
        if (location.jndiProviderId != null){
            String subContextName = "openejb/remote_jndi_contexts/" + location.jndiProviderId;
            return new JndiReference(subContextName, location.jndiName);
        } else {
            return new JndiUrlReference(location.jndiName);
        }
    }

    private String normalize(String name) {
        //currently all names seem to be normalized properly
        return name;
    }

    private static class Ref implements EjbResolver.Reference, Serializable {
        private final EjbReferenceInfo info;

        public Ref(EjbReferenceInfo info) {
            this.info = info;
        }

        public String getEjbLink() {
            return info.link;
        }

        public String getHome() {
            return info.homeClassName;
        }

        public String getInterface() {
            return info.interfaceClassName;
        }

        public String getMappedName() {
            return null;
        }

        public String getName() {
            return info.referenceName;
        }

        public EjbResolver.Type getRefType() {
            if (info instanceof EjbLocalReferenceInfo){
                return EjbResolver.Type.LOCAL;
            } else if (info.homeClassName != null) {
                return EjbResolver.Type.REMOTE;
            } else {
                return EjbResolver.Type.UNKNOWN;
            }
        }
    }
}

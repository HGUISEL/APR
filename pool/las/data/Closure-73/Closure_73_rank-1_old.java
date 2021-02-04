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
package org.apache.felix.dm.impl;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import org.apache.felix.dm.Component;
import org.apache.felix.dm.ComponentStateListener;
import org.apache.felix.dm.Dependency;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;

/**
 * Adapter Service implementation. This class extends the FilterService in order to catch
 * some Service methods for configuring actual adapter service implementation.
 */
public class AdapterServiceImpl extends FilterService
{
    /**
     * Creates a new Adapter Service implementation.
     * @param dm the dependency manager used to create our internal adapter service
     * @param adapteeInterface the service interface to apply the adapter to
     * @param adapteeFilter the filter condition to use with the service interface
     */
    public AdapterServiceImpl(DependencyManager dm, Class adapteeInterface, String adapteeFilter)
    {
        super(dm.createComponent()); // This service will be filtered by our super class, allowing us to take control.
        m_service.setImplementation(new AdapterImpl(adapteeInterface, adapteeFilter))
                 .add(dm.createServiceDependency()
                      .setService(adapteeInterface, adapteeFilter)
                      .setAutoConfig(false)
                      .setCallbacks("added", "removed"));
    }
    
    public class AdapterImpl extends AbstractDecorator {
        private final Class m_adapteeInterface;
        private final String m_adapteeFilter;
        
        public AdapterImpl(Class adapteeInterface, String adapteeFilter) {
            m_adapteeInterface = adapteeInterface;
            m_adapteeFilter = adapteeFilter;
        }
        
        public Component createService(Object[] properties) {
            ServiceReference ref = (ServiceReference) properties[0]; 
            Properties props = new Properties();
            String[] keys = ref.getPropertyKeys();
            for (int i = 0; i < keys.length; i++) {
                String key = keys[i];
                if (key.equals(Constants.SERVICE_ID) || key.equals(Constants.SERVICE_RANKING) || key.equals(DependencyManager.ASPECT) || key.equals(Constants.OBJECTCLASS)) {
                    // do not copy these
                }
                else {
                    props.put(key, ref.getProperty(key));
                }
            }
            if (m_serviceProperties != null) {
                Enumeration e = m_serviceProperties.keys();
                while (e.hasMoreElements()) {
                    Object key = e.nextElement();
                    props.put(key, m_serviceProperties.get(key));
                }
            }
            List dependencies = m_service.getDependencies();
            dependencies.remove(0);
            Component service = m_manager.createComponent()
                .setInterface(m_serviceInterfaces, props)
                .setImplementation(m_serviceImpl)
                .setFactory(m_factory, m_factoryCreateMethod) // if not set, no effect
                .setComposition(m_compositionInstance, m_compositionMethod) // if not set, no effect
                .setCallbacks(m_callbackObject, m_init, m_start, m_stop, m_destroy) // if not set, no effect
                .add(m_manager.createServiceDependency()
                     .setService(m_adapteeInterface, ref)
                     .setRequired(true));
            
            configureAutoConfigState(service, m_service);
            
            for (int i = 0; i < dependencies.size(); i++) {
                service.add(((Dependency) dependencies.get(i)).createCopy());
            }
            
            for (int i = 0; i < m_stateListeners.size(); i ++) {
                service.addStateListener((ComponentStateListener) m_stateListeners.get(i));
            }
            return service;
        }
        public String toString() {
            return "Adapter for " + m_adapteeInterface + ((m_adapteeFilter != null) ? " with filter " + m_adapteeFilter : "");
        }
    }
}

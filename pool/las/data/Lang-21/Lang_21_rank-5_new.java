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
package org.apache.felix.scr.impl.manager;

import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.felix.scr.Component;
import org.apache.felix.scr.Reference;
import org.apache.felix.scr.impl.Activator;
import org.apache.felix.scr.impl.BundleComponentActivator;
import org.apache.felix.scr.impl.config.ScrConfiguration;
import org.apache.felix.scr.impl.helper.ComponentMethods;
import org.apache.felix.scr.impl.helper.MethodResult;
import org.apache.felix.scr.impl.helper.SimpleLogger;
import org.apache.felix.scr.impl.metadata.ComponentMetadata;
import org.apache.felix.scr.impl.metadata.ReferenceMetadata;
import org.apache.felix.scr.impl.metadata.ServiceMetadata;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServicePermission;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentConstants;
import org.osgi.service.log.LogService;


/**
 * The default ComponentManager. Objects of this class are responsible for managing
 * implementation object's lifecycle.
 *
 */
public abstract class AbstractComponentManager<S> implements Component, SimpleLogger
{
    //useful text for deactivation reason numbers
    static final String[] REASONS = {"Unspecified",
        "Component disabled",
        "Reference became unsatisfied",
        "Configuration modified",
        "Configuration deleted",
        "Component disabled",
        "Bundle stopped"};

    private final boolean m_factoryInstance;
    // the ID of this component
    private long m_componentId;

    // The metadata
    private final ComponentMetadata m_componentMetadata;

    private final ComponentMethods m_componentMethods;

    // The dependency managers that manage every dependency
    private final List<DependencyManager<S, ?>> m_dependencyManagers;

    private volatile boolean m_dependencyManagersInitialized;

    private volatile boolean m_dependenciesCollected;

    private final AtomicInteger m_trackingCount = new AtomicInteger( );

    // A reference to the BundleComponentActivator
    private BundleComponentActivator m_activator;

    // The ServiceRegistration is now tracked in the RegistrationManager
    
    private final ReentrantLock m_stateLock;

    protected volatile boolean m_enabled;
    protected final AtomicReference< CountDownLatch> m_enabledLatchRef = new AtomicReference<CountDownLatch>( new CountDownLatch(0) );

    protected volatile boolean m_internalEnabled;
    
    protected volatile boolean m_disposed;
    
    //service event tracking
    private int m_floor;

    private volatile int m_ceiling;

    private final Lock m_missingLock = new ReentrantLock();
    private final Condition m_missingCondition = m_missingLock.newCondition();
    private final Set<Integer> m_missing = new TreeSet<Integer>( );

    volatile boolean m_activated;
    
    protected final ReentrantReadWriteLock m_activationLock = new ReentrantReadWriteLock();

    /**
     * The constructor receives both the activator and the metadata
     *
     * @param activator
     * @param metadata
     * @param componentMethods
     */
    protected AbstractComponentManager( BundleComponentActivator activator, ComponentMetadata metadata, ComponentMethods componentMethods )
    {
        this( activator, metadata, componentMethods, false );
    }
    
    protected AbstractComponentManager( BundleComponentActivator activator, ComponentMetadata metadata, ComponentMethods componentMethods, boolean factoryInstance )
    {
        m_factoryInstance = factoryInstance;
        m_activator = activator;
        m_componentMetadata = metadata;
        this.m_componentMethods = componentMethods;
        m_componentId = -1;

        m_dependencyManagers = loadDependencyManagers( metadata );

        m_stateLock = new ReentrantLock( true );

        // dump component details
        if ( isLogEnabled( LogService.LOG_DEBUG ) )
        {
            log(
                LogService.LOG_DEBUG,
                "Component {0} created: DS={1}, implementation={2}, immediate={3}, default-enabled={4}, factory={5}, configuration-policy={6}, activate={7}, deactivate={8}, modified={9} configuration-pid={10}",
                new Object[]
                    { metadata.getName(), metadata.getNamespaceCode(),
                        metadata.getImplementationClassName(), metadata.isImmediate(),
                        metadata.isEnabled(), metadata.getFactoryIdentifier(),
                        metadata.getConfigurationPolicy(), metadata.getActivate(), metadata.getDeactivate(),
                        metadata.getModified(), metadata.getConfigurationPid() }, null );

            if ( metadata.getServiceMetadata() != null )
            {
                log( LogService.LOG_DEBUG, "Component {0} Services: servicefactory={1}, services={2}", new Object[]
                    { metadata.getName(), Boolean.valueOf( metadata.getServiceMetadata().isServiceFactory() ),
                        Arrays.asList( metadata.getServiceMetadata().getProvides() ) }, null );
            }

            if ( metadata.getProperties() != null )
            {
                log( LogService.LOG_DEBUG, "Component {0} Properties: {1}", new Object[]
                    { metadata.getName(), metadata.getProperties() }, null );
            }
        }
    }

    final long getLockTimeout()
    {
        BundleComponentActivator activator = getActivator();
        if ( activator != null )
        {
            return activator.getConfiguration().lockTimeout();
        }
        return ScrConfiguration.DEFAULT_LOCK_TIMEOUT_MILLISECONDS;
    }

    private void obtainLock( Lock lock, String source )
    {
        try
        {
            if (!lock.tryLock( getLockTimeout(), TimeUnit.MILLISECONDS ) )
            {
                dumpThreads();
                throw new IllegalStateException( "Could not obtain lock" );
            }
        }
        catch ( InterruptedException e )
        {
            try
            {
                if (!lock.tryLock( getLockTimeout(), TimeUnit.MILLISECONDS ) )
                {
                    dumpThreads();
                    throw new IllegalStateException( "Could not obtain lock" );
                }
            }
            catch ( InterruptedException e1 )
            {
                Thread.currentThread().interrupt();
                //TODO is there a better exception to throw?
                throw new IllegalStateException( "Interrupted twice: Could not obtain lock" );
            }
            Thread.currentThread().interrupt();
        }
    }
    
    final void obtainActivationReadLock( String source )
    {
        obtainLock( m_activationLock.readLock(), source);
    }

    final void releaseActivationReadLock( String source )
    {
        m_activationLock.readLock().unlock();
    }
    
    final void obtainActivationWriteLock( String source )
    {
        obtainLock( m_activationLock.writeLock(), source);
    }

    final void releaseActivationWriteeLock( String source )
    {
        if ( m_activationLock.getWriteHoldCount() > 0 )
        {
            m_activationLock.writeLock().unlock();
        }
    }
    
    final void obtainStateLock( String source )
    {
        obtainLock( m_stateLock, source );
    }

    final void releaseStateLock( String source )
    {
        m_stateLock.unlock();
    }

    final boolean isStateLocked()
    {
        return m_stateLock.getHoldCount() > 0;
    }
    
    final void dumpThreads()
    {
        try
        {
            String dump = new ThreadDump().call();
            log( LogService.LOG_DEBUG, dump, null );
        }
        catch ( Throwable t )
        {
            log( LogService.LOG_DEBUG, "Could not dump threads", t );
        }
    }

    //service event tracking
    void tracked( int trackingCount )
    {
        m_missingLock.lock();
        try
        {
            if (trackingCount == m_floor + 1 )
            {
                m_floor++;
                m_missing.remove( trackingCount );
            }
            else if ( trackingCount < m_ceiling )
            {
                m_missing.remove( trackingCount );
            }
            if ( trackingCount > m_ceiling )
            {
                for (int i = m_ceiling + 1; i < trackingCount; i++ )
                {
                    m_missing.add( i );
                }
                m_ceiling = trackingCount;
            }
            m_missingCondition.signalAll();
        }
        finally
        {
            m_missingLock.unlock();
        }
    }

    /**
     * We effectively maintain the set of completely processed service event tracking counts.  This method waits for all events prior 
     * to the parameter tracking count to complete, then returns.  See further documentation in EdgeInfo.
     * @param trackingCount
     */
    void waitForTracked( int trackingCount )
    {
        m_missingLock.lock();
        try
        {
            while ( m_ceiling  < trackingCount || ( !m_missing.isEmpty() && m_missing.iterator().next() < trackingCount))
            {
                log( LogService.LOG_DEBUG, "waitForTracked trackingCount: {0} ceiling: {1} missing: {2}",
                        new Object[] {trackingCount, m_ceiling, m_missing}, null );
                try
                {
                    if ( !doMissingWait())
                    {
                        return;                        
                    }
                }
                catch ( InterruptedException e )
                {
                    try
                    {
                        if ( !doMissingWait())
                        {
                            return;                        
                        }
                    }
                    catch ( InterruptedException e1 )
                    {
                        log( LogService.LOG_ERROR, "waitForTracked interrupted twice: {0} ceiling: {1} missing: {2},  Expect further errors",
                                new Object[] {trackingCount, m_ceiling, m_missing}, e1 );
                    }
                    Thread.currentThread().interrupt();
                }
            }
        }
        finally
        {
            m_missingLock.unlock();
        }
    }
    
    private boolean doMissingWait() throws InterruptedException
    {
        if ( !m_missingCondition.await( getLockTimeout(), TimeUnit.MILLISECONDS ))
        {
            log( LogService.LOG_ERROR, "waitForTracked timed out: {0} ceiling: {1} missing: {2},  Expect further errors",
                    new Object[] {m_trackingCount, m_ceiling, m_missing}, null );
            dumpThreads();
            m_missing.clear();
            return false;
        }
        return true;
    }

//---------- Component ID management

    void registerComponentId()
    {
        final BundleComponentActivator activator = getActivator();
        if ( activator != null )
        {
            this.m_componentId = activator.registerComponentId( this );
        }
    }


    void unregisterComponentId()
    {
        if ( this.m_componentId >= 0 )
        {
            final BundleComponentActivator activator = getActivator();
            if ( activator != null )
            {
                activator.unregisterComponentId( this );
            }
            this.m_componentId = -1;
        }
    }


    //---------- Asynchronous frontend to state change methods ----------------
    private static final AtomicLong taskCounter = new AtomicLong( );

    /**
     * Enables this component and - if satisfied - also activates it. If
     * enabling the component fails for any reason, the component ends up
     * disabled.
     * <p>
     * This method ignores the <i>enabled</i> flag of the component metadata
     * and just enables as requested.
     * <p>
     * This method enables and activates the component immediately.
     */
    public final void enable()
    {
        enable( true );
    }


    public final void enable( final boolean async )
    {
        if (m_enabled)
        {
            return;
        }
        CountDownLatch enableLatch = null;
        try
        {
            enableLatch = enableLatchWait();
            enableInternal();
            if ( !async )
            {
                activateInternal( m_trackingCount.get() );
            }
        }
        finally
        {
            if ( !async )
            {
                enableLatch.countDown();
            }
            m_enabled = true;
        }

        if ( async )
        {
            final CountDownLatch latch = enableLatch;
            m_activator.schedule( new Runnable()
            {

                long count = taskCounter.incrementAndGet();

                public void run()
                {
                    try
                    {
                        activateInternal( m_trackingCount.get() );
                    }
                    finally
                    {
                        latch.countDown();
                    }
                }

                public String toString()
                {
                    return "Async Activate: " + getComponentMetadata().getName() + " id: " + count;
                }
            } );
        }
    }

    /**
     * Use a CountDownLatch as a non-reentrant "lock" that can be passed between threads.
     * This lock assures that enable, disable, and reconfigure operations do not overlap.
     * 
     * @return the latch to count down when the operation is complete (in the calling or another thread)
     * @throws InterruptedException
     */
    CountDownLatch enableLatchWait()
    {
        CountDownLatch enabledLatch;
        CountDownLatch newEnabledLatch;
        do
        {
            enabledLatch = m_enabledLatchRef.get();
            boolean waited = false;
            boolean interrupted = false;
            while ( !waited )
            {
                try
                {
                    enabledLatch.await();
                    waited = true;
                }
                catch ( InterruptedException e )
                {
                    interrupted = true;
                }
            }
            if ( interrupted )
            {
                Thread.currentThread().interrupt();
            }
            newEnabledLatch = new CountDownLatch(1);
        }
        while ( !m_enabledLatchRef.compareAndSet( enabledLatch, newEnabledLatch) );
        return newEnabledLatch;  
    }

    /**
     * Disables this component and - if active - first deactivates it. The
     * component may be reenabled by calling the {@link #enable()} method.
     * <p>
     * This method deactivates and disables the component immediately.
     */
    public final void disable()
    {
        disable( true );
    }


    public final void disable( final boolean async )
    {
        if (!m_enabled)
        {
            return;
        }
        CountDownLatch enableLatch = null;
        try
        {
            enableLatch = enableLatchWait();
            if ( !async )
            {
                deactivateInternal( ComponentConstants.DEACTIVATION_REASON_DISABLED, true, m_trackingCount.get() );
            }
            disableInternal();
        }
        finally
        {
            if (!async)
            {
                enableLatch.countDown();
            }
            m_enabled = false;
        }

        if ( async )
        {
            final CountDownLatch latch = enableLatch;
            m_activator.schedule( new Runnable()
            {

                long count = taskCounter.incrementAndGet();

                public void run()
                {
                    try
                    {
                        deactivateInternal( ComponentConstants.DEACTIVATION_REASON_DISABLED, true, m_trackingCount.get() );
                    }
                    finally
                    {
                        latch.countDown();
                    }
                }

                public String toString()
                {
                    return "Async Deactivate: " + getComponentMetadata().getName() + " id: " + count;
                }

            } );
        }
    }

    // supports the ComponentInstance.dispose() method
    void dispose()
    {
        dispose( ComponentConstants.DEACTIVATION_REASON_DISPOSED );
    }

    /**
     * Disposes off this component deactivating and disabling it first as
     * required. After disposing off the component, it may not be used anymore.
     * <p>
     * This method unlike the other state change methods immediately takes
     * action and disposes the component. The reason for this is, that this
     * method has to actually complete before other actions like bundle stopping
     * may continue.
     */
    public void dispose( int reason )
    {
        m_disposed = true;
        disposeInternal( reason );
    }
    
    <T> void registerMissingDependency( DependencyManager<S, T> dm, ServiceReference<T> ref, int trackingCount)
    {
        BundleComponentActivator activator = getActivator();
        if ( activator != null )
        {
            activator.registerMissingDependency( dm, ref, trackingCount );
        }
    }

    //---------- Component interface ------------------------------------------

    public long getId()
    {
        return m_componentId;
    }

    public String getName() {
        return m_componentMetadata.getName();
    }

    /**
     * Returns the <code>Bundle</code> providing this component. If the
     * component as already been disposed off, this method returns
     * <code>null</code>.
     */
    public Bundle getBundle()
    {
        final BundleContext context = getBundleContext();
        if ( context != null )
        {
            try
            {
                return context.getBundle();
            }
            catch ( IllegalStateException ise )
            {
                // if the bundle context is not valid any more
            }
        }
        // already disposed off component or bundle context is invalid
        return null;
    }
    
    BundleContext getBundleContext()
    {
        final BundleComponentActivator activator = getActivator();
        if ( activator != null )
        {
            return activator.getBundleContext();
        }
        return null;        
    }


    public String getClassName()
    {
        return m_componentMetadata.getImplementationClassName();
    }

    public String getFactory()
    {
        return m_componentMetadata.getFactoryIdentifier();
    }

    public Reference[] getReferences()
    {
        if ( m_dependencyManagers != null && m_dependencyManagers.size() > 0 )
        {
            return (Reference[]) m_dependencyManagers.toArray(
                    new Reference[m_dependencyManagers.size()] );
        }

        return null;
    }

    public boolean isImmediate()
    {
        return m_componentMetadata.isImmediate();

    }

    public boolean isDefaultEnabled()
    {
        return m_componentMetadata.isEnabled();
    }


    public String getActivate()
    {
        return m_componentMetadata.getActivate();
    }


    public boolean isActivateDeclared()
    {
        return m_componentMetadata.isActivateDeclared();
    }


    public String getDeactivate()
    {
        return m_componentMetadata.getDeactivate();
    }


    public boolean isDeactivateDeclared()
    {
        return m_componentMetadata.isDeactivateDeclared();
    }


    public String getModified()
    {
        return m_componentMetadata.getModified();
    }


    public String getConfigurationPolicy()
    {
        return m_componentMetadata.getConfigurationPolicy();
    }

    public String getConfigurationPid()
    {
        return m_componentMetadata.getConfigurationPid();
    }

    public boolean isConfigurationPidDeclared()
    {
        return m_componentMetadata.isConfigurationPidDeclared();
    }

    public boolean isServiceFactory()
    {
        return m_componentMetadata.getServiceMetadata() != null
                && m_componentMetadata.getServiceMetadata().isServiceFactory();
    }

    public boolean isFactory()
    {
        return false;
    }

    public String[] getServices()
    {
        if ( m_componentMetadata.getServiceMetadata() != null )
        {
            return m_componentMetadata.getServiceMetadata().getProvides();
        }

        return null;
    }

    //-------------- atomic transition methods -------------------------------

    final void enableInternal()
    {
        if ( m_disposed )
        {
            throw new IllegalStateException( "enable: " + this );
        }
        if ( !isActivatorActive() )
        {
            log( LogService.LOG_DEBUG, "Bundle's component activator is not active; not enabling component",
                    null );
            return;
        }

        registerComponentId();
        // Before creating the implementation object, we are going to
        // test if we have configuration if such is required
        if ( hasConfiguration() || !getComponentMetadata().isConfigurationRequired() )
        {
            // Update our target filters.
            log( LogService.LOG_DEBUG, "Updating target filters", null );
            updateTargets( getProperties() );
        }

        m_internalEnabled = true;
        log( LogService.LOG_DEBUG, "Component enabled", null );
    }

    final void activateInternal( int trackingCount )
    {
        log( LogService.LOG_DEBUG, "ActivateInternal",
                null );
        if ( m_disposed )
        {
            log( LogService.LOG_DEBUG, "ActivateInternal: disposed",
                    null );
            return;
        }
        if ( m_activated ) {
            log( LogService.LOG_DEBUG, "ActivateInternal: already activated",
                    null );
            return;
        }
        if ( !isEnabled())
        {
            log( LogService.LOG_DEBUG, "Component is not enabled; not activating component",
                    null );
            return;
        }
        if ( !isActivatorActive() )
        {
            log( LogService.LOG_DEBUG, "Bundle's component activator is not active; not activating component",
                    null );
            return;
        }

        log( LogService.LOG_DEBUG, "Activating component from state {0}", new Object[] {getState()},  null );

        // Before creating the implementation object, we are going to
        // test if we have configuration if such is required
        if ( !hasConfiguration() && getComponentMetadata().isConfigurationRequired() )
        {
            log( LogService.LOG_DEBUG, "Missing required configuration, cannot activate", null );
            return;
        }

        // Before creating the implementation object, we are going to
        // test that the bundle has enough permissions to register services
        if ( !hasServiceRegistrationPermissions() )
        {
            log( LogService.LOG_DEBUG, "Component is not permitted to register all services, cannot activate",
                    null );
            return;
        }

        obtainActivationReadLock( "activateInternal" );
        try
        {
            // Before creating the implementation object, we are going to
            // test if all the mandatory dependencies are satisfied
            if ( !verifyDependencyManagers() )
            {
                log( LogService.LOG_DEBUG, "Not all dependencies satisfied, cannot activate", null );
                return;
            }

            if ( !registerService() )
            {
                //some other thread is activating us, or we got concurrently deactivated.
                return;
            }


            if ( ( isImmediate() || getComponentMetadata().isFactory() ) )
            {
                getServiceInternal();
            }
        }
        finally
        {
            releaseActivationReadLock( "activateInternal" );
        }
    }

    final void deactivateInternal( int reason, boolean disable, int trackingCount )
    {
        if ( m_disposed )
        {
            return;
        }
        if ( m_factoryInstance )
        {
            disposeInternal( reason );
            return;
        }
        log( LogService.LOG_DEBUG, "Deactivating component", null );

        // catch any problems from deleting the component to prevent the
        // component to remain in the deactivating state !
        obtainActivationReadLock( "deactivateInternal" );
        try
        {
            doDeactivate( reason, disable );
        }
        finally 
        {
            releaseActivationReadLock( "deactivateInternal" );
        }
        if ( isFactory() )
        {
            clear();
        }
    }

    final void disableInternal()
    {
        m_internalEnabled = false;
        if ( m_disposed )
        {
            throw new IllegalStateException( "Cannot disable a disposed component " + getName() );
        }
        unregisterComponentId();
    }

    /**
     * Disposes off this component deactivating and disabling it first as
     * required. After disposing off the component, it may not be used anymore.
     * <p>
     * This method unlike the other state change methods immediately takes
     * action and disposes the component. The reason for this is, that this
     * method has to actually complete before other actions like bundle stopping
     * may continue.
     */
    final void disposeInternal( int reason )
    {
        log( LogService.LOG_DEBUG, "Disposing component (reason: " + reason + ")", null );
        doDeactivate( reason, true );
        clear();
    }
         
    final void doDeactivate( int reason, boolean disable )
    {
        try
        {
            if ( !unregisterService() )
            {
                log( LogService.LOG_DEBUG, "Component deactivation occuring on another thread", null );
            }
            obtainStateLock( "AbstractComponentManager.State.doDeactivate.1" );
            try
            {
                if ( m_activated )
                {
                    m_activated = false;
                    deleteComponent( reason );
                    deactivateDependencyManagers();
                    if ( disable )
                    {
                        disableDependencyManagers();
                    }
                    unsetDependenciesCollected();
                }
            }
            finally
            {
                releaseStateLock( "AbstractComponentManager.State.doDeactivate.1" );
            }
        }
        catch ( Throwable t )
        {
            log( LogService.LOG_WARNING, "Component deactivation threw an exception", t );
        }
    }

    final ServiceReference<S> getServiceReference()
    {
        ServiceRegistration<S> reg = getServiceRegistration();
        if (reg != null)
        {
            return reg.getReference();
        }
        return null;
    }

    //---------- Component handling methods ----------------------------------
    /**
     * Method is called by {@link State#activate(AbstractComponentManager)} in STATE_ACTIVATING or by
     * {@link DelayedComponentManager#getService(Bundle, ServiceRegistration)}
     * in STATE_REGISTERED.
     *
     * @return <code>true</code> if creation of the component succeeded. If
     *       <code>false</code> is returned, the cause should have been logged.
     */
    protected abstract boolean createComponent();

    protected abstract void deleteComponent( int reason );

    boolean getServiceInternal()
    {
        return false;
    }

    /**
     * All ComponentManagers are ServiceFactory instances
     *
     * @return this as a ServiceFactory.
     */
    private Object getService()
    {
        return this;
    }

    ComponentMethods getComponentMethods()
    {
        return m_componentMethods;
    }
    
    protected String[] getProvidedServices()
    {
        if ( getComponentMetadata().getServiceMetadata() != null )
        {
            String[] provides = getComponentMetadata().getServiceMetadata().getProvides();
            return provides;
        }
        return null;
        
    }

    private final RegistrationManager<ServiceRegistration<S>> registrationManager = new RegistrationManager<ServiceRegistration<S>>()
    {

        @Override
        ServiceRegistration<S> register(String[] services)
        {
            BundleContext bundleContext = getBundleContext();
            if (bundleContext == null) 
            {
                return null;
            }
            final Dictionary<String, Object> serviceProperties = getServiceProperties();
            ServiceRegistration<S> serviceRegistration = ( ServiceRegistration<S> ) bundleContext
                    .registerService( services, getService(), serviceProperties );
            return serviceRegistration;
        }

        @Override
        void unregister(ServiceRegistration<S> serviceRegistration)
        {
            serviceRegistration.unregister();
        }

        @Override
        void log(int level, String message, Object[] arguments, Throwable ex)
        {
            AbstractComponentManager.this.log(level, message, arguments, ex);
        }

        @Override
        long getTimeout()
        {
            return getLockTimeout();
        }

        @Override
        void reportTimeout()
        {
            dumpThreads();
        }
        
    };
    

    /**
     * Registers the service on behalf of the component.
     *
     */
    protected boolean registerService()
    {
        String[] services = getProvidedServices();
        if ( services != null )
        {
            return registrationManager.changeRegistration( RegistrationManager.RegState.registered, services);
        }
        return true;
    }

    protected boolean unregisterService()
    {
        String[] services = getProvidedServices();
        if ( services != null )
        {
            return registrationManager.changeRegistration( RegistrationManager.RegState.unregistered, services );
        }
        return true;
    }
    

    AtomicInteger getTrackingCount()
    {
        return m_trackingCount;
    }


    private void initDependencyManagers()
    {
        if ( m_dependencyManagersInitialized )
        {
            return;
        }
        final Bundle bundle = getBundle();
        if (bundle == null)
        {
            log( LogService.LOG_ERROR, "bundle shut down while trying to load implementation object class", null );
            throw new IllegalStateException("bundle shut down while trying to load implementation object class");
        }
        Class<?> implementationObjectClass;
        try
        {
            implementationObjectClass = bundle.loadClass(
                    getComponentMetadata().getImplementationClassName() );
        }
        catch ( ClassNotFoundException e )
        {
            log( LogService.LOG_ERROR, "Could not load implementation object class", e );
            throw new IllegalStateException("Could not load implementation object class");
        }
        m_componentMethods.initComponentMethods( m_componentMetadata, implementationObjectClass );

        for ( DependencyManager dependencyManager : m_dependencyManagers )
        {
            dependencyManager.initBindingMethods( m_componentMethods.getBindMethods( dependencyManager.getName() ) );
        }
        m_dependencyManagersInitialized = true;
    }

    /**
     * Collect and store in m_dependencies_map all the services for dependencies, outside of any locks.
     * Throwing IllegalStateException on failure to collect all the dependencies is needed so getService can
     * know to return null.
     *
     * @return true if this thread collected the dependencies;
     *   false if some other thread successfully collected the dependencies;
     * @throws IllegalStateException if some dependency is no longer available.
     */
    protected boolean collectDependencies() throws IllegalStateException
    {
        if ( m_dependenciesCollected)
        {
            log( LogService.LOG_DEBUG, "dependencies already collected, do not collect dependencies", null );
            return false;
        }
        initDependencyManagers();
        for ( DependencyManager<S, ?> dependencyManager : m_dependencyManagers )
        {
            if ( !dependencyManager.prebind() )
            {
                //not actually satisfied any longer
                deactivateDependencyManagers();
                log( LogService.LOG_DEBUG, "Could not get required dependency for dependency manager: {0}",
                        new Object[] {dependencyManager.getName()}, null );
                throw new IllegalStateException( "Missing dependencies, not satisfied" );
            }
        }
        m_dependenciesCollected = true;
        log( LogService.LOG_DEBUG, "This thread collected dependencies", null );
        return true;
    }

    protected void unsetDependenciesCollected()
    {
        m_dependenciesCollected = false;
    }

    abstract <T> void invokeUpdatedMethod( DependencyManager<S, T> dependencyManager, RefPair<T> refPair, int trackingCount );

    abstract <T> void invokeBindMethod( DependencyManager<S, T> dependencyManager, RefPair<T> refPair, int trackingCount );

    abstract <T> void invokeUnbindMethod( DependencyManager<S, T> dependencyManager, RefPair<T> oldRefPair, int trackingCount );

    //**********************************************************************************************************
    public BundleComponentActivator getActivator()
    {
        return m_activator;
    }


    boolean isActivatorActive()
    {
        BundleComponentActivator activator = getActivator();
        return activator != null && activator.isActive();
    }


    final ServiceRegistration<S> getServiceRegistration() 
    {
        return registrationManager.getServiceRegistration();
    }


    synchronized void clear()
    {
        // for some testing, the activator may be null
        if ( m_activator != null )
        {
            m_activator.unregisterComponentId( this );
        }
    }

    /**
     * Returns <code>true</code> if logging for the given level is enabled.
     */
    public boolean isLogEnabled( int level )
    {
        return Activator.isLogEnabled( level );
    }


    public void log( int level, String message, Throwable ex )
    {
        BundleComponentActivator activator = getActivator();
        if ( activator != null )
        {
            activator.log( level, message, getComponentMetadata(), m_componentId, ex );
        }
    }

    public void log( int level, String message, Object[] arguments, Throwable ex )
    {
        BundleComponentActivator activator = getActivator();
        if ( activator != null )
        {
            activator.log( level, message, arguments, getComponentMetadata(), m_componentId, ex );
        }
    }


    public String toString()
    {
        return "Component: " + getName() + " (" + getId() + ")";
    }


    private boolean hasServiceRegistrationPermissions()
    {
        boolean allowed = true;
        if ( System.getSecurityManager() != null )
        {
            final ServiceMetadata serviceMetadata = getComponentMetadata().getServiceMetadata();
            if ( serviceMetadata != null )
            {
                final String[] services = serviceMetadata.getProvides();
                if ( services != null && services.length > 0 )
                {
                    final Bundle bundle = getBundle();
                    for ( String service : services )
                    {
                        final Permission perm = new ServicePermission( service, ServicePermission.REGISTER );
                        if ( !bundle.hasPermission( perm ) )
                        {
                            log( LogService.LOG_DEBUG, "Permission to register service {0} is denied", new Object[]
                                    {service}, null );
                            allowed = false;
                        }
                    }
                }
            }
        }

        // no security manager or no services to register
        return allowed;
    }


    private List<DependencyManager<S, ?>> loadDependencyManagers( ComponentMetadata metadata )
    {
        List<DependencyManager<S, ?>> depMgrList = new ArrayList<DependencyManager<S, ?>>(metadata.getDependencies().size());

        // If this component has got dependencies, create dependency managers for each one of them.
        if ( metadata.getDependencies().size() != 0 )
        {
            int index = 0;
            for ( ReferenceMetadata currentdependency: metadata.getDependencies() )
            {
                DependencyManager<S, ?> depmanager = new DependencyManager( this, currentdependency, index++ );

                depMgrList.add( depmanager );
            }
        }

        return depMgrList;
    }

    final void updateTargets(Dictionary<String, Object> properties)
    {
        for ( DependencyManager<S, ?> dm: getDependencyManagers() )
        {
            dm.setTargetFilter( properties );
        }
    }

    protected boolean verifyDependencyManagers()
    {
        // indicates whether all dependencies are satisfied
        boolean satisfied = true;

        for ( DependencyManager<S, ?> dm: getDependencyManagers() )
        {

            if ( !dm.hasGetPermission() )
            {
                // bundle has no service get permission
                if ( dm.isOptional() )
                {
                    log( LogService.LOG_DEBUG, "No permission to get optional dependency: {0}; assuming satisfied",
                        new Object[]
                            { dm.getName() }, null );
                }
                else
                {
                    log( LogService.LOG_DEBUG, "No permission to get mandatory dependency: {0}; assuming unsatisfied",
                        new Object[]
                            { dm.getName() }, null );
                    satisfied = false;
                }
            }
            else if ( !dm.isSatisfied() )
            {
                // bundle would have permission but there are not enough services
                log( LogService.LOG_DEBUG, "Dependency not satisfied: {0}", new Object[]
                    { dm.getName() }, null );
                satisfied = false;
            }
        }

        return satisfied;
    }

    /**
     * Returns an iterator over the {@link DependencyManager} objects
     * representing the declared references in declaration order
     */
    List<DependencyManager<S, ?>> getDependencyManagers()
    {
        return m_dependencyManagers;
    }

    /**
     * Returns an iterator over the {@link DependencyManager} objects
     * representing the declared references in reversed declaration order
     */
    List<DependencyManager<S, ?>> getReversedDependencyManagers()
    {
        List<DependencyManager<S, ?>> list = new ArrayList<DependencyManager<S, ?>>( m_dependencyManagers );
        Collections.reverse( list );
        return list;
    }


    DependencyManager<S, ?> getDependencyManager(String name)
    {
        for ( DependencyManager<S, ?> dm: getDependencyManagers() )
        {
            if ( name.equals(dm.getName()) )
            {
                return dm;
            }
        }

        // not found
        return null;
    }

    private void deactivateDependencyManagers()
    {
        log( LogService.LOG_DEBUG, "Deactivating dependency managers", null);
        for ( DependencyManager<S, ?> dm: getDependencyManagers() )
        {
            dm.deactivate();
        }
    }

    private void disableDependencyManagers()
    {
        log( LogService.LOG_DEBUG, "Disabling dependency managers", null);
        AtomicInteger trackingCount = new AtomicInteger();
        for ( DependencyManager<S, ?> dm: getDependencyManagers() )
        {
            dm.unregisterServiceListener( trackingCount );
        }
    }

    public abstract boolean hasConfiguration();

    public abstract Dictionary<String, Object> getProperties();

    public abstract void setServiceProperties( Dictionary<String, Object> serviceProperties );

    /**
     * Returns the subset of component properties to be used as service
     * properties. These properties are all component properties where property
     * name does not start with dot (.), properties which are considered
     * private.
     */
    public Dictionary<String, Object> getServiceProperties()
    {
        return copyTo( null, getProperties(), false );
    }

    /**
     * Copies the properties from the <code>source</code> <code>Dictionary</code>
     * into the <code>target</code> <code>Dictionary</code>.
     *
     * @param target The <code>Dictionary</code> into which to copy the
     *      properties. If <code>null</code> a new <code>Hashtable</code> is
     *      created.
     * @param source The <code>Dictionary</code> providing the properties to
     *      copy. If <code>null</code> or empty, nothing is copied.
     *
     * @return The <code>target</code> is returned, which may be empty if
     *      <code>source</code> is <code>null</code> or empty and
     *      <code>target</code> was <code>null</code>.
     */
    protected static Dictionary<String, Object> copyTo( Dictionary<String, Object> target, Dictionary<String, Object> source )
    {
        return copyTo( target, source, true );
    }

    /**
     * Copies the properties from the <code>source</code> <code>Dictionary</code>
     * into the <code>target</code> <code>Dictionary</code> except for private
     * properties (whose name has a leading dot) which are only copied if the
     * <code>allProps</code> parameter is <code>true</code>.
     *
     * @param target    The <code>Dictionary</code> into which to copy the
     *                  properties. If <code>null</code> a new <code>Hashtable</code> is
     *                  created.
     * @param source    The <code>Dictionary</code> providing the properties to
     *                  copy. If <code>null</code> or empty, nothing is copied.
     * @param allProps  Whether all properties (<code>true</code>) or only the
     *                  public properties (<code>false</code>) are to be copied.
     *
     * @return The <code>target</code> is returned, which may be empty if
     *         <code>source</code> is <code>null</code> or empty and
     *         <code>target</code> was <code>null</code> or all properties are
     *         private and had not to be copied
     */
    protected static Dictionary<String, Object> copyTo( Dictionary<String, Object> target, final Dictionary<String, Object> source, final boolean allProps )
    {
        if ( target == null )
        {
            target = new Hashtable<String, Object>();
        }

        if ( source != null && !source.isEmpty() )
        {
            for ( Enumeration ce = source.keys(); ce.hasMoreElements(); )
            {
                // cast is save, because key must be a string as per the spec
                String key = ( String ) ce.nextElement();
                if ( allProps || key.charAt( 0 ) != '.' )
                {
                    target.put( key, source.get( key ) );
                }
            }
        }

        return target;
    }


    /**
     *
     */
    public ComponentMetadata getComponentMetadata()
    {
        return m_componentMetadata;
    }

    public int getState()
    {
        if (m_disposed)
        {
            return Component.STATE_DISPOSED;
        }
        if ( !m_internalEnabled)
        {
            return Component.STATE_DISABLED;
        }
        if ( getServiceRegistration() == null && (getProvidedServices() != null || !hasInstance()))
        {
            return Component.STATE_UNSATISFIED;
        }
        if ( isFactory() && !m_factoryInstance )
        {
            return Component.STATE_FACTORY;
        }
        if ( hasInstance() )
        {
            return Component.STATE_ACTIVE;
        }
        return Component.STATE_REGISTERED;
    }

    abstract boolean hasInstance();

    public void setServiceProperties( MethodResult methodResult )
    {
        if ( methodResult.hasResult() )
        {
            Dictionary<String, Object> serviceProps = ( methodResult.getResult() == null) ? null : new Hashtable<String, Object>( methodResult.getResult() );
            setServiceProperties(serviceProps );
        }
    }

    boolean isEnabled()
    {
        return m_internalEnabled;
    }
    
}

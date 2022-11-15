/*
 * Copyright 2021, 2022 Contributors to the Eclipse Foundation
 * Copyright (c) 1997, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.ejb.containers;

import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.logging.Level;

import jakarta.ejb.CreateException;
import jakarta.ejb.EJBException;
import jakarta.ejb.EJBObject;
import jakarta.ejb.RemoveException;
import jakarta.ejb.SessionBean;
import jakarta.transaction.Status;
import jakarta.transaction.Transaction;

import org.glassfish.api.invocation.ComponentInvocation;
import org.glassfish.ejb.config.EjbContainer;
import org.glassfish.ejb.deployment.descriptor.EjbDescriptor;
import org.glassfish.ejb.deployment.descriptor.runtime.IASEjbExtraDescriptors;

import com.sun.ejb.ComponentContext;
import com.sun.ejb.EjbInvocation;
import com.sun.ejb.containers.util.pool.AbstractPool;
import com.sun.ejb.containers.util.pool.NonBlockingPool;
import com.sun.ejb.containers.util.pool.ObjectFactory;
import com.sun.ejb.monitoring.stats.EjbMonitoringStatsProvider;
import com.sun.ejb.monitoring.stats.EjbPoolStatsProvider;
import com.sun.ejb.monitoring.stats.StatelessSessionBeanStatsProvider;
import com.sun.enterprise.admin.monitor.callflow.ComponentType;
import com.sun.enterprise.deployment.LifecycleCallbackDescriptor.CallbackType;
import com.sun.enterprise.deployment.runtime.BeanPoolDescriptor;
import com.sun.enterprise.security.SecurityManager;

/** This class provides container functionality specific to stateless SessionBeans.
 *  At deployment time, one instance of the StatelessSessionContainer is created
 *  for each stateless SessionBean type (i.e. deployment descriptor) in a JAR.
 * <P>
 * The 3 states of a Stateless EJB (an EJB can be in only 1 state at a time):
 * <ol>
 * <li> POOLED : ready for invocations, no transaction in progress
 * <li> INVOKING : processing an invocation
 * <li> DESTROYED : does not exist
 * </ol>
 * This container services invocations using a pool of EJB instances.
 * An instance is returned to the pool immediately after the invocation
 * completes, so the # of instances needed = # of concurrent invocations.
 * <P>
 * A Stateless Bean can hold open DB connections across invocations.
 * Its assumed that the Resource Manager can handle
 * multiple incomplete transactions on the same
 * connection.
 */
public class StatelessSessionContainer extends BaseContainer {
    private static final byte[] statelessInstanceKey = {0, 0, 0, 1};

    private final EjbContainer ejbContainer;

    // All stateless EJBs have the same instanceKey, since all stateless EJBs
    // are identical. Note: the first byte of instanceKey must be left empty.

    // All stateless EJB instances of a particular class (i.e. all bean
    // instances created by this container instance) have the same
    // EJBObject/EJBLocalObject instance since they are all identical.
    private EJBLocalObjectImpl theEJBLocalObjectImpl;
    private EJBLocalObjectImpl theEJBLocalBusinessObjectImpl;
    private EJBLocalObjectImpl theOptionalEJBLocalBusinessObjectImpl;

    // Data members for RemoteHome view
    private EJBObjectImpl theEJBObjectImpl;
    private EJBObject theEJBStub;

    // Data members for Remote business view. Any objects representing the
    // Remote business interface are not subtypes of EJBObject.
    private EJBObjectImpl theRemoteBusinessObjectImpl;

    protected AbstractPool pool;

    private IASEjbExtraDescriptors iased;
    private BeanPoolDescriptor beanPoolDes;

    private PoolProperties poolProp;

    /**
     * This constructor is called from the JarManager when a Jar is deployed.
     *
     * @exception Exception on error
     */
    StatelessSessionContainer(EjbDescriptor desc, ClassLoader loader, SecurityManager sm) throws Exception {
        this(ContainerType.STATELESS, desc, loader, sm);
    }


    protected StatelessSessionContainer(ContainerType conType, EjbDescriptor desc, ClassLoader loader,
        SecurityManager sm) throws Exception {
        super(conType, desc, loader, sm);
        ejbContainer = ejbContainerUtilImpl.getEjbContainer();
        super.createCallFlowAgent(ComponentType.SLSB);
    }


    public String getMonitorAttributeValues() {
        StringBuilder sbuf = new StringBuilder();
        sbuf.append("STATELESS ").append(ejbDescriptor.getName());
        sbuf.append(pool.getAllAttrValues());
        sbuf.append("]");
        return sbuf.toString();
    }


    @Override
    protected boolean suspendTransaction(EjbInvocation inv) throws Exception {
        // EJB2.0 section 7.5.7 says that ejbCreate/ejbRemove etc are called
        // without a Tx. So suspend the client's Tx if any.

        // Note: ejbRemove cannot be called when EJB is associated with
        // a Tx, according to EJB2.0 section 7.6.4. This check is done in
        // the container's implementation of removeBean().

        return !inv.invocationInfo.isBusinessMethod;
    }


    @Override
    protected boolean resumeTransaction(EjbInvocation inv) throws Exception {
        return !inv.invocationInfo.isBusinessMethod;
    }


    @Override
    public boolean scanForEjbCreateMethod() {
        return true;
    }


    @Override
    protected EjbMonitoringStatsProvider getMonitoringStatsProvider(String appName, String modName, String ejbName) {
        return new StatelessSessionBeanStatsProvider(this, getContainerId(), appName, modName, ejbName);
    }


    @Override
    protected void initializeHome() throws Exception {
        super.initializeHome();

        if (isRemote) {
            if (hasRemoteHomeView) {
                // Create theEJBObjectImpl
                theEJBObjectImpl = instantiateEJBObjectImpl();

                // connect the EJBObject to the ProtocolManager
                // (creates the stub
                // too). Note: cant do this in constructor above because
                // beanId is not set at that time.
                theEJBStub = (EJBObject) remoteHomeRefFactory.createRemoteReference(statelessInstanceKey);
                theEJBObjectImpl.setStub(theEJBStub);
            }

            if (hasRemoteBusinessView) {
                theRemoteBusinessObjectImpl = instantiateRemoteBusinessObjectImpl();
                for (RemoteBusinessIntfInfo next : remoteBusinessIntfInfo.values()) {
                    java.rmi.Remote stub = next.referenceFactory.createRemoteReference(statelessInstanceKey);
                    theRemoteBusinessObjectImpl.setStub(next.generatedRemoteIntf.getName(), stub);
                }
            }
        }

        if (isLocal) {
            if (hasLocalHomeView) {
                theEJBLocalObjectImpl = instantiateEJBLocalObjectImpl();
            }
            if (hasLocalBusinessView) {
                theEJBLocalBusinessObjectImpl = instantiateEJBLocalBusinessObjectImpl();
            }
            if (hasOptionalLocalBusinessView) {
                theOptionalEJBLocalBusinessObjectImpl = instantiateOptionalEJBLocalBusinessObjectImpl();
            }
        }

        createBeanPool();
        registerMonitorableComponents();
    }


    protected void createBeanPool() {
        ObjectFactory sessionCtxFactory = new SessionContextFactory();

        iased = ejbDescriptor.getIASEjbExtraDescriptors();
        if (iased != null) {
            beanPoolDes = iased.getBeanPool();
        }

        poolProp = new PoolProperties(ejbContainer, beanPoolDes);
        String val = ejbDescriptor.getEjbBundleDescriptor().getEnterpriseBeansProperty(SINGLETON_BEAN_POOL_PROP);
        pool = new NonBlockingPool(getContainerId(), ejbDescriptor.getName(), sessionCtxFactory,
            poolProp.steadyPoolSize, poolProp.poolResizeQuantity, poolProp.maxPoolSize,
            poolProp.poolIdleTimeoutInSeconds, loader, Boolean.parseBoolean(val));
    }


    @Override
    protected void registerMonitorableComponents() {
        super.registerMonitorableComponents();

        poolProbeListener = new EjbPoolStatsProvider(pool, getContainerId(), containerInfo.appName,
            containerInfo.modName, containerInfo.ejbName);
        poolProbeListener.register();

        _logger.log(Level.FINE, "[SLSB Container] registered monitorable");
    }


    @Override
    public void onReady() {
    }


    @Override
    public EJBObjectImpl createRemoteBusinessObjectImpl() throws CreateException, RemoteException {
        // No access check since this is an internal operation.
        ejbBeanCreatedEvent();
        return theRemoteBusinessObjectImpl;
    }


    private void ejbBeanCreatedEvent() {
        ejbProbeNotifier.ejbBeanCreatedEvent(getContainerId(), containerInfo.appName, containerInfo.modName,
            containerInfo.ejbName);
    }


    @Override
    public EJBObjectImpl createEJBObjectImpl() throws CreateException, RemoteException {
        // Need to do access control check here because BaseContainer.preInvoke
        // is not called for stateless sessionbean creates.
        authorizeRemoteMethod(EJBHome_create);
        ejbBeanCreatedEvent();

        // For stateless EJBs, EJB2.0 Section 7.8 says that
        // Home.create() need not do any real creation.
        // If necessary, a stateless bean is created below during getContext().
        return theEJBObjectImpl;
    }


    /**
     * Called during client creation request through EJB LocalHome view.
     */
    @Override
    public EJBLocalObjectImpl createEJBLocalObjectImpl() throws CreateException {
        // Need to do access control check here because BaseContainer.preInvoke
        // is not called for stateless sessionbean creates.
        authorizeLocalMethod(EJBLocalHome_create);
        ejbBeanCreatedEvent();

        // For stateless EJBs, EJB2.0 Section 7.8 says that
        // Home.create() need not do any real creation.
        // If necessary, a stateless bean is created below during getContext().
        return theEJBLocalObjectImpl;
    }


    /**
     * Called during internal creation of session bean
     */
    @Override
    public EJBLocalObjectImpl createEJBLocalBusinessObjectImpl(boolean localBeanView) throws CreateException {
        ejbBeanCreatedEvent();

        // No access checks needed because this is called as a result
        // of an internal creation, not a user-visible create method.
        return localBeanView ? theOptionalEJBLocalBusinessObjectImpl : theEJBLocalBusinessObjectImpl;
    }


    // Called from EJBObjectImpl.remove, EJBLocalObjectImpl.remove,
    // EJBHomeImpl.remove(Handle).
    @Override
    protected void removeBean(EJBLocalRemoteObject ejbo, Method removeMethod, boolean local)
        throws RemoveException, EJBException, RemoteException {
        if (local) {
            authorizeLocalMethod(BaseContainer.EJBLocalObject_remove);
        } else {
            authorizeRemoteMethod(BaseContainer.EJBObject_remove);
        }
        ejbProbeNotifier.ejbBeanDestroyedEvent(getContainerId(), containerInfo.appName, containerInfo.modName,
            containerInfo.ejbName);
    }


    /**
     * Force destroy the EJB. Called from postInvokeTx.
     * Note: EJB2.0 section 18.3.1 says that discarding an EJB
     * means that no methods other than finalize() should be invoked on it.
     */
    @Override
    protected void forceDestroyBean(EJBContextImpl sc) {
        if (sc.getState() == EJBContextImpl.BeanState.DESTROYED) {
            return;
        }

        // mark context as destroyed
        sc.setState(EJBContextImpl.BeanState.DESTROYED);

        // sessionCtxPool.destroyObject(sc);
        pool.destroyObject(sc);
    }


    /**
     * Called when a remote invocation arrives for an EJB.
     */
    @Override
    protected EJBObjectImpl getEJBObjectImpl(byte[] instanceKey) {
        return theEJBObjectImpl;
    }


    @Override
    EJBObjectImpl getEJBRemoteBusinessObjectImpl(byte[] instanceKey) {
        return theRemoteBusinessObjectImpl;
    }


    /**
     * Called from EJBLocalObjectImpl.getLocalObject() while deserializing
     * a local object reference.
     */
    @Override
    protected EJBLocalObjectImpl getEJBLocalObjectImpl(Object key) {
        return theEJBLocalObjectImpl;
    }


    /**
     * Called from EJBLocalObjectImpl.getLocalObject() while deserializing
     * a local business object reference.
     */
    @Override
    EJBLocalObjectImpl getEJBLocalBusinessObjectImpl(Object key) {
        return theEJBLocalBusinessObjectImpl;
    }


    /**
     * Called from EJBLocalObjectImpl.getLocalObject() while deserializing
     * a local business object reference.
     */
    @Override
    EJBLocalObjectImpl getOptionalEJBLocalBusinessObjectImpl(Object key) {
        return theOptionalEJBLocalBusinessObjectImpl;
    }


    /**
     * Called from preInvoke which is called from the EJBObject
     * for local and remote invocations.
     */
    @Override
    protected ComponentContext _getContext(EjbInvocation inv) {
        try {
            SessionContextImpl sessionCtx = (SessionContextImpl) pool.getObject(null);
            sessionCtx.setState(EJBContextImpl.BeanState.INVOKING);
            return sessionCtx;
        } catch (Exception ex) {
            throw new EJBException(ex);
        }
    }


    @Override
    protected EJBContextImpl _constructEJBContextImpl(Object instance) {
        return new SessionContextImpl(instance, this);
    }


    /**
     * called when an invocation arrives and there are no instances
     * left to deliver the invocation to.
     * Called from SessionContextFactory.create() !
     */
    private SessionContextImpl createStatelessEJB() throws CreateException {
        EjbInvocation ejbInv = null;
        final SessionContextImpl context;
        try {
            context = (SessionContextImpl) createEjbInstanceAndContext();
            Object ejb = context.getEJB();

            // this allows JNDI lookups from setSessionContext, ejbCreate
            ejbInv = super.createEjbInvocation(ejb, context);
            invocationManager.preInvoke(ejbInv);

            // setSessionContext will be called without a Tx as required
            // by the spec, because the EJBHome.create would have been called
            // after the container suspended any client Tx.
            // setSessionContext is also called before context.setEJBStub
            // because the bean is not allowed to do EJBContext.getEJBObject
            setSessionContext(ejb, context);

            // Perform injection right after where setSessionContext
            // would be called. This is important since injection methods
            // have the same "operations allowed" permissions as
            // setSessionContext.
            injectEjbInstance(context);

            if (isRemote) {
                if (hasRemoteHomeView) {
                    context.setEJBObjectImpl(theEJBObjectImpl);
                    context.setEJBStub(theEJBStub);
                }
                if (hasRemoteBusinessView) {
                    context.setEJBRemoteBusinessObjectImpl(theRemoteBusinessObjectImpl);
                }
            }
            if (isLocal) {
                if (hasLocalHomeView) {
                    context.setEJBLocalObjectImpl(theEJBLocalObjectImpl);
                }
                if (hasLocalBusinessView) {
                    context.setEJBLocalBusinessObjectImpl(theEJBLocalBusinessObjectImpl);
                }
                if (hasOptionalLocalBusinessView) {
                    context.setOptionalEJBLocalBusinessObjectImpl(theOptionalEJBLocalBusinessObjectImpl);
                }
            }

            // all stateless beans have the same id and same InstanceKey
            context.setInstanceKey(statelessInstanceKey);

            //Call ejbCreate() or @PostConstruct method
            intercept(CallbackType.POST_CONSTRUCT, context);

            // Set the state to POOLED after ejbCreate so that
            // EJBContext methods not allowed will throw exceptions
            context.setState(EJBContextImpl.BeanState.POOLED);
        } catch (Throwable th) {
            CreateException creEx = new CreateException("Could not create stateless EJB");
            creEx.initCause(th);
            throw creEx;
        } finally {
            if (ejbInv != null) {
                invocationManager.postInvoke(ejbInv);
            }
        }
        context.touch();
        return context;
    }


    private void setSessionContext(Object ejb, SessionContextImpl context) throws Exception {
        if (ejb instanceof SessionBean) {
            ((SessionBean) ejb).setSessionContext(context);
        }
    }


    @Override
    protected void doTimerInvocationInit(EjbInvocation inv, Object primaryKey) throws Exception {
        // TODO I don't understand this check. What is ejbObject used for?
        if (isRemote) {
            // TODO inv.ejbObject = theEJBObjectImpl;
            inv.isLocal = false;
        } else {
            inv.ejbObject = theEJBLocalObjectImpl;
            inv.isLocal = true;
        }
    }


    @Override
    public boolean userTransactionMethodsAllowed(ComponentInvocation inv) {
        if (isBeanManagedTran) {
            if (inv instanceof EjbInvocation) {
                EjbInvocation ejbInv = (EjbInvocation) inv;
                EJBContextImpl sc = (EJBContextImpl) ejbInv.context;
                // If Invocation, only ejbRemove not allowed.
                return !sc.isInEjbRemove();
            }
        }
        // This will prevent setSessionContext/ejbCreate access
        return false;
    }


    /**
     * Called from preInvoke which is called from the EJBObject
     * for local and remote invocations.
     */
    @Override
    public void releaseContext(EjbInvocation inv) {
        SessionContextImpl sc = (SessionContextImpl) inv.context;

        // check if the bean was destroyed
        if (sc.getState() == EJBContextImpl.BeanState.DESTROYED) {
            return;
        }

        sc.setState(EJBContextImpl.BeanState.POOLED);

        // Stateless beans cant have transactions across invocations
        sc.setTransaction(null);
        sc.touch();

        pool.returnObject(sc);
    }


    @Override
    protected boolean isIdentical(EJBObjectImpl ejbo, EJBObject other) throws RemoteException {
        if (other == ejbo.getStub()) {
            return true;
        }
        try {
            // other may be a stub for a remote object.
            // Although all stateless sessionbeans for a bean type
            // are identical, we dont know whether other is of the
            // same bean type as ejbo.
            return getProtocolManager().isIdentical(ejbo.getStub(), other);
        } catch (Exception ex) {
            if (_logger.isLoggable(Level.SEVERE)) {
                _logger.log(Level.SEVERE, "ejb.ejb_getstub_exception", containerInfo);
                _logger.log(Level.SEVERE, "", ex);
            }
            throw new RemoteException("Error during isIdentical.", ex);
        }
    }


    /**
     * Check if the given EJBObject/LocalObject has been removed.
     */
    @Override
    protected void checkExists(EJBLocalRemoteObject ejbObj) {
        // For stateless session beans, EJBObject/EJBLocalObj are never removed.
        // So do nothing.
    }


    @Override
    protected void afterBegin(EJBContextImpl context) {
        // Stateless SessionBeans cannot implement SessionSynchronization!!
        // EJB2.0 Spec 7.8.
    }


    @Override
    protected void beforeCompletion(EJBContextImpl context) {
        // Stateless SessionBeans cannot implement SessionSynchronization!!
        // EJB2.0 Spec 7.8.
    }


    @Override
    protected void afterCompletion(EJBContextImpl ctx, int status) {
        // Stateless SessionBeans cannot implement SessionSynchronization!!
        // EJB2.0 Spec 7.8.
    }


    /**
     * @return false
     */
    @Override
    public boolean passivateEJB(ComponentContext context) {
        return false;
    }

    /**
     * Doesn't do anything by default.
     */
    public void activateEJB(Object ctx, Object instanceKey) {
    }


    @Override
    protected void doConcreteContainerShutdown(boolean appBeingUndeployed) {
        try {
            if (hasRemoteHomeView) {
                // destroy EJBObject refs
                // XXX invocations still in progress will get exceptions ??
                remoteHomeRefFactory.destroyReference(theEJBObjectImpl.getStub(), theEJBObjectImpl.getEJBObject());
            }
            if (hasRemoteBusinessView) {
                for (RemoteBusinessIntfInfo next : remoteBusinessIntfInfo.values()) {
                    next.referenceFactory.destroyReference(
                        theRemoteBusinessObjectImpl.getStub(next.generatedRemoteIntf.getName()),
                        theRemoteBusinessObjectImpl.getEJBObject(next.generatedRemoteIntf.getName()));
                }
            }
            if (pool != null) {
                pool.close();
                poolProbeListener.unregister();
            }
        } catch(Throwable t) {
            _logger.log(Level.FINE, "Exception during conrete StatelessSessionBean cleanup", t);
        }
    }


    public long getMethodReadyCount() {
        return pool.getSize();
    }

    // Methods for StatelessSessionBeanStatsProvider
    public int getMaxPoolSize() {
        return poolProp.maxPoolSize <= 0 ? Integer.MAX_VALUE : poolProp.maxPoolSize;
    }

    public int getSteadyPoolSize() {
        return poolProp.steadyPoolSize <= 0 ? 0 : poolProp.steadyPoolSize;
    }


    protected class SessionContextFactory implements ObjectFactory {

        @Override
        public Object create(Object param) {
            try {
                return createStatelessEJB();
            } catch (CreateException ex) {
                throw new EJBException(ex);
            }
        }


        @Override
        public void destroy(Object obj) {
            SessionContextImpl sessionCtx = (SessionContextImpl) obj;
            // Note: stateless SessionBeans cannot have incomplete transactions
            // in progress. So it is ok to destroy the EJB.

            Object sb = sessionCtx.getEJB();
            if (sessionCtx.getState() == EJBContextImpl.BeanState.DESTROYED) {
                // Called from forceDestroyBean
                // So NO need to call ejb.ejbRemove()
                // mark the context's transaction for rollback
                Transaction tx = sessionCtx.getTransaction();
                try {
                    if (tx != null && tx.getStatus() != Status.STATUS_NO_TRANSACTION) {
                        tx.setRollbackOnly();
                    }
                } catch (Exception ex) {
                    _logger.log(Level.FINE, "forceDestroyBean exception", ex);
                }
            } else {
                // Called from pool implementation to reduce the pool size.
                // So need to call ejb.ejbRemove()
                // mark context as destroyed
                sessionCtx.setState(EJBContextImpl.BeanState.DESTROYED);

                EjbInvocation ejbInv = null;
                try {
                    // NOTE : Context class-loader is already set by Pool
                    ejbInv = createEjbInvocation(sb, sessionCtx);
                    invocationManager.preInvoke(ejbInv);
                    sessionCtx.setInEjbRemove(true);

                    intercept(CallbackType.PRE_DESTROY, sessionCtx);

                } catch (Throwable t) {
                    _logger.log(Level.FINE, "ejbRemove exception", t);
                } finally {

                    sessionCtx.setInEjbRemove(false);
                    if (ejbInv != null) {
                        invocationManager.postInvoke(ejbInv);
                    }
                }
            }

            cleanupInstance(sessionCtx);
            // tell the TM to release resources held by the bean
            transactionManager.componentDestroyed(sessionCtx);
            sessionCtx.setTransaction(null);
            sessionCtx.deleteAllReferences();
        }
    } // SessionContextFactory{}

    private static class PoolProperties {

        int maxPoolSize;
        int poolIdleTimeoutInSeconds;
        int poolResizeQuantity;
        int steadyPoolSize;

        public PoolProperties(EjbContainer ejbContainer, BeanPoolDescriptor beanPoolDes) {
            maxPoolSize = Integer.parseInt(ejbContainer.getMaxPoolSize());
            poolIdleTimeoutInSeconds = Integer.parseInt(ejbContainer.getPoolIdleTimeoutInSeconds());
            poolResizeQuantity = Integer.parseInt(ejbContainer.getPoolResizeQuantity());
            steadyPoolSize = Integer.parseInt(ejbContainer.getSteadyPoolSize());
            if (beanPoolDes != null) {
                int temp = 0;
                if ((temp = beanPoolDes.getMaxPoolSize()) != -1) {
                    maxPoolSize = temp;
                }
                if ((temp = beanPoolDes.getPoolIdleTimeoutInSeconds()) != -1) {
                    poolIdleTimeoutInSeconds = temp;
                }
                if ((temp = beanPoolDes.getPoolResizeQuantity()) != -1) {
                    poolResizeQuantity = temp;
                }
                if ((temp = beanPoolDes.getSteadyPoolSize()) != -1) {
                    steadyPoolSize = temp;
                }
            }
        }
    } // PoolProperties{}
} // StatelessSessionContainer.java


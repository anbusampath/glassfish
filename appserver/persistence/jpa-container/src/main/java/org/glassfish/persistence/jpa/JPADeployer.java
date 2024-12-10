/*
 * Copyright (c) 2022, 2024 Contributors to the Eclipse Foundation.
 * Copyright (c) 2008, 2020 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.persistence.jpa;

import com.sun.appserv.connectors.internal.api.ConnectorRuntime;
import com.sun.enterprise.deployment.Application;
import com.sun.enterprise.deployment.BundleDescriptor;
import com.sun.enterprise.deployment.PersistenceUnitDescriptor;
import com.sun.enterprise.deployment.PersistenceUnitsDescriptor;
import com.sun.enterprise.deployment.util.DOLUtils;
import com.sun.enterprise.module.bootstrap.StartupContext;
import com.sun.logging.LogDomains;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.api.deployment.DeployCommandParameters;
import org.glassfish.api.deployment.DeploymentContext;
import org.glassfish.api.deployment.MetaData;
import org.glassfish.api.deployment.OpsParams;
import org.glassfish.api.event.EventListener;
import org.glassfish.api.event.Events;
import org.glassfish.api.naming.SimpleJndiName;
import org.glassfish.deployment.common.DeploymentException;
import org.glassfish.deployment.common.RootDeploymentDescriptor;
import org.glassfish.deployment.common.SimpleDeployer;
import org.glassfish.hk2.api.PostConstruct;
import org.glassfish.internal.data.ApplicationInfo;
import org.glassfish.internal.data.ApplicationRegistry;
import org.glassfish.internal.deployment.Deployment;
import org.glassfish.internal.deployment.ExtendedDeploymentContext;
import org.glassfish.persistence.common.Java2DBProcessorHelper;
import org.glassfish.server.ServerEnvironmentImpl;
import org.jvnet.hk2.annotations.Service;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;
import static org.glassfish.internal.deployment.Deployment.APPLICATION_PREPARED;

/**
 * Deployer for JPA applications
 *
 * @author Mitesh Meswani
 */
@Service
public class JPADeployer extends SimpleDeployer<JPAContainer, JPApplicationContainer> implements PostConstruct, EventListener {

    private static Logger logger = LogDomains.getLogger(PersistenceUnitLoader.class, LogDomains.PERSISTENCE_LOGGER + ".jpadeployer");

    /** Key used to get/put emflists in transientAppMetadata */
    private static final String EMF_KEY = EntityManagerFactory.class.toString();

    @Inject
    private ConnectorRuntime connectorRuntime;

    @Inject
    private ServerEnvironmentImpl serverEnvironment;

    @Inject
    private volatile StartupContext startupContext;

    @Inject
    private Events events;

    @Inject
    private ApplicationRegistry applicationRegistry;

    @Override
    public void postConstruct() {
        events.register(this);
    }

    @Override
    public MetaData getMetaData() {
        return new MetaData(
            true /* invalidateCL */,
            null /* provides */,
            new Class[] { Application.class } /* requires Application from dol */);
    }

    @Override
    protected void generateArtifacts(DeploymentContext dc) throws DeploymentException {
        // Noting to generate yet!!
    }

    @Override
    protected void cleanArtifacts(DeploymentContext deploymentContext) throws DeploymentException {
        // Drop tables if needed on undeploy.
        OpsParams params = deploymentContext.getCommandParameters(OpsParams.class);
        if (params.origin.isUndeploy() && isDas()) {

            boolean hasScopedResource = false;
            String appName = params.name();
            ApplicationInfo appInfo = applicationRegistry.get(appName);
            Application application = appInfo.getMetaData(Application.class);
            Set<BundleDescriptor> bundles = application.getBundleDescriptors();

            // Iterate through all the bundles of the app and collect pu references in
            // referencedPus
            for (BundleDescriptor bundle : bundles) {
                Collection<? extends PersistenceUnitDescriptor> pusReferencedFromBundle = bundle.findReferencedPUs();
                for (PersistenceUnitDescriptor pud : pusReferencedFromBundle) {
                    hasScopedResource = hasScopedResource(pud);
                    if (hasScopedResource) {
                        break;
                    }
                }
            }

            // If there are scoped resources, deploy them so that they are accessible for
            // Java2DB to delete tables.
            if (hasScopedResource) {
                connectorRuntime.registerDataSourceDefinitions(application);
            }

            Java2DBProcessorHelper helper = new Java2DBProcessorHelper(deploymentContext);
            helper.init();
            helper.createOrDropTablesInDB(false, "JPA");

            // If there are scoped resources, undeploy them.
            if (hasScopedResource) {
                connectorRuntime.unRegisterDataSourceDefinitions(application);
            }
        }
    }

    @Override
    public <V> V loadMetaData(Class<V> type, DeploymentContext context) {
        return null;
    }

    /**
     * EntityManagerFactories for persistence units are created and stored in JPAApplication instance. The
     * JPAApplication instance is stored in given DeploymentContext to be retrieved
     * by load
     */
    @Override
    public boolean prepare(DeploymentContext deploymentContext) {
        boolean prepared = super.prepare(deploymentContext);
        if (prepared) {
            if (isEntityManagerFactoryCreationRequired(deploymentContext)) {
                createEntityManagerFactories(deploymentContext);
            }
        }
        return prepared;
    }

    @Override
    public JPApplicationContainer load(JPAContainer container, DeploymentContext context) {
        return new JPApplicationContainer();
    }

    @Override
    public void event(Event<?> event) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("JpaDeployer.event():" + event.name());
        }

        if (event.is(APPLICATION_PREPARED)) {
            ExtendedDeploymentContext context = (ExtendedDeploymentContext) event.hook();
            DeployCommandParameters deployCommandParameters = context.getCommandParameters(DeployCommandParameters.class);
            if (logger.isLoggable(FINE)) {
                logger.fine("JpaDeployer.event(): Handling APPLICATION_PREPARED origin is:" + deployCommandParameters.origin);
            }

            // When create-application-ref is called for an already deployed app,
            // APPLICATION_PREPARED will be sent on DAS
            // Obviously there is no new emf created for this event and we need not do
            // java2db also. Ignore the event
            // However, if target for create-application-ref is DAS => the app was deployed
            // on other instance but now
            // an application-ref is being created on DAS. Process the app
            if (!deployCommandParameters.origin.isCreateAppRef() || isTargetDas(deployCommandParameters)) {
                Map<String, ExtendedDeploymentContext> deploymentContexts = context.getModuleDeploymentContexts();
                for (DeploymentContext deploymentContext : deploymentContexts.values()) {
                    // bundle level persistence unit
                    iterateInitializedPUsAtApplicationPrepare(deploymentContext);
                }
                // app level persistence unit
                iterateInitializedPUsAtApplicationPrepare(context);
            }
        } else if (event.is(Deployment.APPLICATION_DISABLED)) {
            logger.fine("JpaDeployer.event(): APPLICATION_DISABLED");
            // APPLICATION_DISABLED will be generated when an app is
            // disabled/undeployed/appserver goes down.
            // close all the emfs created for this app
            closeEntityManagerFactories((ApplicationInfo) event.hook());
        }
    }

    /**
     * Returns unique identifier for this pu within application
     *
     * @param pud The given persistence unit
     * @return Absolute persistence unit root + persistence unit name
     */
    private static String getUniquePuIdentifier(PersistenceUnitDescriptor pud) {
        return pud.getAbsolutePuRoot() + pud.getName();
    }

    private static boolean isTargetDas(DeployCommandParameters deployCommandParameters) {
        // TODO discuss with Hong. This comparison should be encapsulated somewhere
        return "server".equals(deployCommandParameters.target);
    }

    private boolean isDas() {
        return serverEnvironment.isDas() || serverEnvironment.isEmbedded();
    }

    private void closeEntityManagerFactories(ApplicationInfo appInfo) {
        // Suppress warning required as there is no way to pass equivalent of
        // List<EMF>.class to the method
        @SuppressWarnings("unchecked")
        List<EntityManagerFactory> emfsCreatedForThisApp = appInfo.getTransientAppMetaData(EMF_KEY, List.class);
        if (emfsCreatedForThisApp != null) {

            // Events are always dispatched to all registered listeners.
            // emfsCreatedForThisApp will be null for an app that does not have PUs.

            for (EntityManagerFactory entityManagerFactory : emfsCreatedForThisApp) {
                entityManagerFactory.close();
            }

            // We no longer have the emfs in open state clear the list.
            // On app enable(after a disable), for a cluster, the deployment framework calls
            // prepare() for instances but not for DAS.
            // So on DAS, at a disable, the emfs will be closed and we will not attempt to
            // close emfs when appserver goes down even if the app is re-enabled.
            emfsCreatedForThisApp.clear();
        }
    }

    /**
     * CreateEMFs and save them in persistence
     *
     * @param deploymentContext
     */
    private void createEntityManagerFactories(DeploymentContext deploymentContext) {
        Application application = deploymentContext.getModuleMetaData(Application.class);

        // Iterate through all the bundles for the app and collect persistence unit references in
        // referencedPus
        boolean hasScopedResource = false;
        final List<PersistenceUnitDescriptor> referencedPersistenceUnits = new ArrayList<>();
        for (BundleDescriptor bundle : application.getBundleDescriptors()) {
            for (PersistenceUnitDescriptor persistenceUnit : bundle.findReferencedPUs()) {
                referencedPersistenceUnits.add(persistenceUnit);
                if (hasScopedResource(persistenceUnit)) {
                    hasScopedResource = true;
                }
            }
        }

        if (hasScopedResource) {
            // Scoped resources are registered by connector runtime after prepare(). That is
            // too late for Jakarta Persistence.
            // This is a hack to initialize connectorRuntime for scoped resources
            connectorRuntime.registerDataSourceDefinitions(application);
        }

        boolean usesCDI = true;

        // Iterate through all the PUDs for this bundle and if it is referenced, load
        // the corresponding pu
        PersistenceUnitDescriptorIterator pudIterator = new PersistenceUnitDescriptorIterator() {
            @Override
            void visitPUD(PersistenceUnitDescriptor persistenceUnitDescriptor, DeploymentContext deploymentContext) {
                // EntityManager etc can be injected using CDI as well. Since CDI has the ability to programmatically
                // look up beans, we can't check whether the persistence unit is actually referenced as referencedPus
                // optimised for.
                if (usesCDI || referencedPersistenceUnits.contains(persistenceUnitDescriptor)) {
                    boolean isDas = isDas();

                    // While running in embedded mode, it is not possible to guarantee that entity
                    // classes are not loaded by the app classloader before transformers are
                    // installed.
                    //
                    // If that happens, weaving will not take place and EclipseLink will throw up.
                    // Provide users an option to disable weaving by passing the flag.
                    // Note that we enable weaving if not explicitly disabled by user
                    boolean weavingEnabled = Boolean
                            .parseBoolean(startupContext.getArguments().getProperty("org.glassfish.persistence.embedded.weaving.enabled", "true"));

                    ProviderContainerContractInfo providerContainerContractInfo = weavingEnabled
                            ? new ServerProviderContainerContractInfo(deploymentContext, connectorRuntime, isDas)
                            : new EmbeddedProviderContainerContractInfo(deploymentContext, connectorRuntime, isDas);

                    try {
                        ((ExtendedDeploymentContext) deploymentContext).prepareScratchDirs();
                    } catch (IOException e) {
                        // There is no way to recover if we are not able to create the scratch dirs.
                        // Just rethrow the exception.
                        throw new RuntimeException(e);
                    }

                    // Store the persistenceUnitLoader in context. It is retrieved to execute java2db and to
                    // store the loaded emfs in a JPAApplicationContainer object for cleanup
                    deploymentContext.addTransientAppMetaData(
                        getUniquePuIdentifier(persistenceUnitDescriptor),
                        new PersistenceUnitLoader(persistenceUnitDescriptor, providerContainerContractInfo));
                }
            }
        };

        pudIterator.iteratePUDs(deploymentContext);
    }

    /**
     * @return true if given <code>pud</code> is using scoped resource
     */
    private boolean hasScopedResource(PersistenceUnitDescriptor persistenceUnitDescriptor) {
        boolean hasScopedResource = false;
        SimpleJndiName jtaDataSource = persistenceUnitDescriptor.getJtaDataSource();
        if (jtaDataSource != null && jtaDataSource.hasJavaPrefix()) {
            hasScopedResource = true;
        }

        return hasScopedResource;
    }

    /**
     * @param context
     * @return true if EntityManagerFactory creation is required false otherwise
     */
    private boolean isEntityManagerFactoryCreationRequired(DeploymentContext context) {
        /*
         * Here are various use cases that needs to be handled. This method handles
         * EntityManagerFactory creation part, APPLICATION_PREPARED event handle handles
         * java2db and closing of EntityManagerFactory
         *
         * To summarize, -Unconditionally create EMFs on DAS for java2db if it is
         * deploy. We will close this EMF in APPLICATION_PREPARED after java2db if
         * (target!= DAS || enable=false) -We will not create EMFs on instance if
         * application is not enabled
         *
         * -----------------------------------------------------------------------------
         * ------- Scenario Expected Behavior
         * -----------------------------------------------------------------------------
         * ------- deploy --target=server --enabled=true. DAS(EMF created, java2db, EMF
         * remains open) -restart DAS(EMF created, EMF remains open) -undeploy DAS(EMF
         * closed. Drop tables) -create-application-ref instance1 DAS(No action)
         * INSTANCE1(EMF created)
         *
         * deploy --target=server --enabled=false. DAS(EMF created,java2db, EMF closed
         * in APPLICATION_PREPARED) -restart DAS(No EMF created) -undeploy DAS(No EMF to
         * close, Drop tables)
         *
         * -enable DAS(EMF created) -undelpoy DAS(EMF closed, Drop tables)
         *
         * -create-application-ref instance1 DAS(No action) INSTANCE1(EMF created)
         *
         * deploy --target=instance1 --enabled=true DAS(EMF created, java2db, EMF closed
         * in APPLICATION_PREPARED) INSTANCE1(EMF created) -create-application-ref
         * instance2 INSTANCE2(EMF created) -restart DAS(No EMF created) INSTANCE1(EMF
         * created) INSTANCE2(EMF created) -undeploy DAS(No EMF to close, Drop tables)
         * INSTANCE1(EMF closed)
         *
         * -create-application-ref server DAS(EMF created) -delete-application-ref
         * server DAS(EMF closed) undeploy INSTANCE1(EMF closed)
         *
         *
         * deploy --target=instance --enabled=false. DAS(EMF created, java2db, EMF
         * closed in APPLICATION_PREPARED) INSTANCE1(No EMF created)
         * -create-application-ref instance2 DAS(No action) INSTANCE2(No Action)
         * -restart DAS(No EMF created) INSTANCE1(No EMF created) INSTANCE2(No EMF
         * created) -undeploy DAS(No EMF to close, Drop tables) INSTANCE1(No EMF to
         * close) INSTANCE2(No EMF to close)
         *
         * -enable --target=instance1 DAS(No EMF created) INSTANCE1(EMF created)
         *
         */

        boolean createEntityManagerFactories = false;
        DeployCommandParameters deployCommandParameters = context.getCommandParameters(DeployCommandParameters.class);
        boolean deploy = deployCommandParameters.origin.isDeploy();
        boolean enabled = deployCommandParameters.enabled;
        boolean isDas = isDas();

        if (logger.isLoggable(FINER)) {
            logger.finer("isEMFCreationRequired(): deploy: " + deploy + " enabled: " + enabled + " isDas: " + isDas);
        }

        if (isDas) {
            if (deploy) {
                // Always create emfs on DAS while deploying to take care of java2db and PU
                // validation on deploy
                createEntityManagerFactories = true;
            } else {
                // We reach here for (!deploy && das) => server restart or enabling a disabled
                // app on DAS
                boolean isTargetDas = isTargetDas(deployCommandParameters);
                if (logger.isLoggable(FINER)) {
                    logger.finer("isEMFCreationRequired(): isTargetDas: " + isTargetDas);
                }

                if (enabled && isTargetDas) {
                    createEntityManagerFactories = true;
                }
            }
        } else { // !das => on an instance
            if (enabled) {
                createEntityManagerFactories = true;
            }
        }

        if (logger.isLoggable(FINER)) {
            logger.finer("isEMFCreationRequired(): returning createEMFs:" + createEntityManagerFactories);
        }

        return createEntityManagerFactories;
    }

    /**
     * Does java2db on DAS and saves entity manager factories created during prepare to ApplicationInfo
     * maintained by DOL.
     *
     * ApplicationInfo is not available during prepare() so we can not directly use it there.
     *
     * @param context
     */
    private void iterateInitializedPUsAtApplicationPrepare(final DeploymentContext context) {

        final DeployCommandParameters deployCommandParameters = context.getCommandParameters(DeployCommandParameters.class);
        String appName = deployCommandParameters.name;
        final ApplicationInfo appInfo = applicationRegistry.get(appName);

        // iterate through all the PersistenceUnitDescriptor for this bundle.
        PersistenceUnitDescriptorIterator pudIterator = new PersistenceUnitDescriptorIterator() {
            @Override
            void visitPUD(PersistenceUnitDescriptor pud, DeploymentContext deploymentContext) {
                // PersistenceUnitsDescriptor corresponds to persistence.xml. A bundle can only
                // have one persitence.xml except
                // when the bundle is an application which can have multiple persitence.xml
                // under jars in root of ear and lib.
                PersistenceUnitLoader persistenceUnitLoader = deploymentContext.getTransientAppMetaData(getUniquePuIdentifier(pud), PersistenceUnitLoader.class);
                if (persistenceUnitLoader != null) { // We have initialized persistence unit
                    boolean saveEntityManagerFactory = true;
                    if (isDas()) { // We do validation and execute Java2DB only on DAS

                        // APPLICATION_PREPARED will be called for create-application-ref
                        // also. We should perform java2db only on first deploy
                        if (deployCommandParameters.origin.isDeploy()) {

                            // Create EntityManager to trigger validation on persistence unit
                            EntityManagerFactory entityManagerFactory = persistenceUnitLoader.getEMF();
                            try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
                                // Create entity manager to trigger any validations that are lazily performed by the
                                // provider
                                //
                                // Entity manager creation also triggers DDL generation by provider.
                            } catch (PersistenceException e) {
                                // Exception indicates something went wrong while performing validation. Clean
                                // up and rethrow to fail deployment
                                entityManagerFactory.close();

                                // Need to wrap exception in DeploymentException else deployment will not fail.
                                throw new DeploymentException(e);
                            }

                            persistenceUnitLoader.doJava2DB();

                            boolean enabled = deployCommandParameters.enabled;
                            boolean isTargetDas = isTargetDas(deployCommandParameters);
                            if (logger.isLoggable(FINER)) {
                                logger.finer("iterateInitializedPUsAtApplicationPrepare(): enabled: " + enabled + " isTargetDas: "
                                        + isTargetDas);
                            }

                            if (!isTargetDas || !enabled) {
                                // We are on DAS but target != das or app is not enabled on das => The EMF was
                                // just created for Java2Db. Close it.
                                persistenceUnitLoader.getEMF().close();
                                saveEntityManagerFactory = false; // Do not save EMF. We have already closed it
                            }
                        }
                    }

                    if (saveEntityManagerFactory) {
                        // Save EntityManagerFactory in ApplicationInfo so that it can be retrieved and closed for cleanup

                        // Suppress warning required as there is no way to pass equivalent of
                        // List<EMF>.class to the method
                        @SuppressWarnings("unchecked")
                        List<EntityManagerFactory> emfsCreatedForThisApp = appInfo.getTransientAppMetaData(EMF_KEY, List.class);
                        if (emfsCreatedForThisApp == null) {
                            // First EMF for this app, initialize
                            emfsCreatedForThisApp = new ArrayList<>();
                            appInfo.addTransientAppMetaData(EMF_KEY, emfsCreatedForThisApp);
                        }
                        emfsCreatedForThisApp.add(persistenceUnitLoader.getEMF());
                    } // if (saveEMF)
                } // if(puLoader != null)
            }
        };

        pudIterator.iteratePUDs(context);
    }

    /**
     * Helper class to centralize the code for loop that iterates through all the
     * PersistenceUnitDescriptor for a given DeploymentContext (and hence the
     * corresponding bundle)
     */
    private static abstract class PersistenceUnitDescriptorIterator {
        /**
         * Iterate through all the PersistenceUnitDescriptors for the given context (and
         * hence corresponding bundle) and call visitPUD for each of them
         *
         * @param context
         */
        void iteratePUDs(DeploymentContext context) {
            RootDeploymentDescriptor currentBundle = DOLUtils.getCurrentBundleForContext(context);
            if (currentBundle != null) { // it can be null for non-JavaEE type of application deployment. e.g., issue
                                         // 15869
                Collection<PersistenceUnitsDescriptor> pusDescriptorForThisBundle =
                    currentBundle.getExtensionsDescriptors(PersistenceUnitsDescriptor.class);

                for (PersistenceUnitsDescriptor persistenceUnitsDescriptor : pusDescriptorForThisBundle) {
                    for (PersistenceUnitDescriptor pud : persistenceUnitsDescriptor.getPersistenceUnitDescriptors()) {
                        visitPUD(pud, context);
                    }
                }
            }

        }

        /**
         * Called for each PersistenceUnitDescriptor visited by this iterator.
         */
        abstract void visitPUD(PersistenceUnitDescriptor pud, DeploymentContext context);

    }
}

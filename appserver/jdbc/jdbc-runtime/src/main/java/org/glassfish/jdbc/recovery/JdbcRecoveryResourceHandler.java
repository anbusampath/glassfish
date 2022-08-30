/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.glassfish.jdbc.recovery;

import com.sun.appserv.connectors.internal.api.ConnectorRuntime;
import com.sun.appserv.connectors.internal.api.ConnectorsUtil;
import com.sun.enterprise.config.serverbeans.Application;
import com.sun.enterprise.config.serverbeans.Applications;
import com.sun.enterprise.config.serverbeans.Config;
import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.Module;
import com.sun.enterprise.config.serverbeans.Resource;
import com.sun.enterprise.config.serverbeans.Resources;
import com.sun.enterprise.connectors.util.ResourcesUtil;
import com.sun.enterprise.transaction.api.XAResourceWrapper;
import com.sun.enterprise.transaction.config.TransactionService;
import com.sun.enterprise.transaction.spi.RecoveryResourceHandler;
import com.sun.logging.LogDomains;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.resource.ResourceException;
import jakarta.resource.spi.ManagedConnection;
import jakarta.resource.spi.ManagedConnectionFactory;
import jakarta.resource.spi.security.PasswordCredential;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.security.auth.Subject;
import javax.transaction.xa.XAResource;

import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.jdbc.config.JdbcConnectionPool;
import org.glassfish.jdbc.config.JdbcResource;
import org.glassfish.jdbc.util.JdbcResourcesUtil;
import org.glassfish.resourcebase.resources.api.PoolInfo;
import org.glassfish.resourcebase.resources.api.ResourceInfo;
import org.glassfish.security.common.UserNameAndPassword;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.types.Property;

/**
 * Recovery Handler for Jdbc Resources
 *
 * @author Jagadish Ramu
 */
@Service
public class JdbcRecoveryResourceHandler implements RecoveryResourceHandler {

    @Inject
    ServiceLocator habitat;

    private TransactionService txService;

    @Inject
    private Domain domain;

    @Inject
    private Applications applications;

    @Inject
    private Provider<ConnectorRuntime> connectorRuntimeProvider;

    private ResourcesUtil resourcesUtil = null;

    private static Logger _logger = LogDomains.getLogger(JdbcRecoveryResourceHandler.class, LogDomains.RSR_LOGGER);

    private void loadAllJdbcResources() {

        if (_logger.isLoggable(Level.FINEST)) {
            _logger.log(Level.FINEST, "loadAllJdbcResources start");
        }
        try {
            Collection<JdbcResource> jdbcResources = getAllJdbcResources();
            InitialContext ic = new InitialContext();
            for (Resource resource : jdbcResources) {
                JdbcResource jdbcResource = (JdbcResource) resource;
                if (getResourcesUtil().isEnabled(jdbcResource)) {
                    try {
                        ic.lookup(jdbcResource.getJndiName());
                    } catch (Exception ex) {
                        _logger.log(Level.SEVERE, "error.loading.jdbc.resources.during.recovery", jdbcResource.getJndiName());
                        if (_logger.isLoggable(Level.FINE)) {
                            _logger.log(Level.FINE, ex.toString(), ex);
                        }
                    }
                }
            }
        } catch (NamingException ne) {
            _logger.log(Level.SEVERE, "error.loading.jdbc.resources.during.recovery", ne.getMessage());
            if (_logger.isLoggable(Level.FINE)) {
                _logger.log(Level.FINE, ne.toString(), ne);
            }
        }
        if (_logger.isLoggable(Level.FINEST)) {
            _logger.log(Level.FINEST, "loadAllJdbcResources end");
        }
    }

    private Collection<JdbcResource> getAllJdbcResources() {
        Collection<JdbcResource> allResources = new ArrayList<>();
        Collection<JdbcResource> jdbcResources = domain.getResources().getResources(JdbcResource.class);
        allResources.addAll(jdbcResources);
        for (Application app : applications.getApplications()) {
            if (ResourcesUtil.createInstance().isEnabled(app)) {
                Resources appScopedResources = app.getResources();
                if (appScopedResources != null && appScopedResources.getResources() != null) {
                    allResources.addAll(appScopedResources.getResources(JdbcResource.class));
                }
                List<Module> modules = app.getModule();
                if (modules != null) {
                    for (Module module : modules) {
                        Resources msr = module.getResources();
                        if (msr != null && msr.getResources() != null) {
                            allResources.addAll(msr.getResources(JdbcResource.class));
                        }
                    }
                }
            }
        }
        return allResources;
    }

    private ResourcesUtil getResourcesUtil() {
        if (resourcesUtil == null) {
            resourcesUtil = ResourcesUtil.createInstance();
        }
        return resourcesUtil;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadXAResourcesAndItsConnections(List xaresList, List connList) {

        // Done so as to initialize connectors-runtime before loading jdbc-resources.
        // need a better way ?
        ConnectorRuntime crt = connectorRuntimeProvider.get();

        Collection<JdbcResource> jdbcResources = getAllJdbcResources();

        if (jdbcResources == null || jdbcResources.size() == 0) {
            if (_logger.isLoggable(Level.FINEST)) {
                _logger.finest("loadXAResourcesAndItsConnections : no resources");
            }
            return;
        }

        List<JdbcConnectionPool> jdbcPools = new ArrayList<>();

        for (Resource resource : jdbcResources) {
            JdbcResource jdbcResource = (JdbcResource) resource;
            if (getResourcesUtil().isEnabled(jdbcResource)) {
                ResourceInfo resourceInfo = ConnectorsUtil.getResourceInfo(jdbcResource);
                JdbcConnectionPool pool = JdbcResourcesUtil.createInstance().getJdbcConnectionPoolOfResource(resourceInfo);
                if (pool != null && "javax.sql.XADataSource".equals(pool.getResType())) {
                    jdbcPools.add(pool);
                }
                if (_logger.isLoggable(Level.FINE)) {
                    _logger.fine("JdbcRecoveryResourceHandler:: loadXAResourcesAndItsConnections :: " + "adding : "
                            + (jdbcResource.getPoolName()));
                }
            }
        }

        loadAllJdbcResources();
        // Read from the transaction-service , if the replacement of
        // Vendor XAResource class with our version required.
        // If yes, put the mapping in the xaresourcewrappers properties.
        Properties XAResourceWrappers = new Properties();

        XAResourceWrappers.put("oracle.jdbc.xa.client.OracleXADataSource", "com.sun.enterprise.transaction.jts.recovery.OracleXAResource");

        Config c = habitat.getService(Config.class, ServerEnvironment.DEFAULT_INSTANCE_NAME);
        txService = c.getExtensionByType(TransactionService.class);

        List<Property> properties = txService.getProperty();

        if (properties != null) {
            for (Property property : properties) {
                String name = property.getName();
                String value = property.getValue();
                if (name.equals("oracle-xa-recovery-workaround")) {
                    if ("false".equals(value)) {
                        XAResourceWrappers.remove("oracle.jdbc.xa.client.OracleXADataSource");
                    }
                } else if (name.equals("sybase-xa-recovery-workaround")) {
                    if (value.equals("true")) {
                        XAResourceWrappers.put("com.sybase.jdbc2.jdbc.SybXADataSource",
                                "com.sun.enterprise.transaction.jts.recovery.SybaseXAResource");
                    }
                }
            }
        }

        for (JdbcConnectionPool jdbcConnectionPool : jdbcPools) {
            if (jdbcConnectionPool.getResType() == null || jdbcConnectionPool.getName() == null
                    || !jdbcConnectionPool.getResType().equals("javax.sql.XADataSource")) {
                if (_logger.isLoggable(Level.FINEST)) {
                    _logger.finest("skipping pool : " + jdbcConnectionPool.getName());
                }
                continue;
            }
            if (_logger.isLoggable(Level.FINEST)) {
                _logger.finest(" using pool : " + jdbcConnectionPool.getName());
            }

            PoolInfo poolInfo = ConnectorsUtil.getPoolInfo(jdbcConnectionPool);
            try {

                String[] dbUserPassword = getdbUserPasswordOfJdbcConnectionPool(jdbcConnectionPool);
                String dbUser = dbUserPassword[0];
                String dbPassword = dbUserPassword[1];
                if (dbPassword == null) {
                    dbPassword = "";
                    if (_logger.isLoggable(Level.FINEST)) {
                        _logger.log(Level.FINEST, "datasource.xadatasource_nullpassword_error", poolInfo);
                    }
                }
                if (dbUser == null) {
                    dbUser = "";
                    if (_logger.isLoggable(Level.FINEST)) {
                        _logger.log(Level.FINEST, "datasource.xadatasource_nulluser_error", poolInfo);
                    }
                }

                ManagedConnectionFactory fac = crt.obtainManagedConnectionFactory(poolInfo);
                Subject subject = new Subject();
                PasswordCredential pc = new PasswordCredential(dbUser, dbPassword.toCharArray());
                pc.setManagedConnectionFactory(fac);
                Principal prin = new UserNameAndPassword(dbUser, dbPassword);
                subject.getPrincipals().add(prin);
                subject.getPrivateCredentials().add(pc);
                ManagedConnection mc = fac.createManagedConnection(subject, null);
                connList.add(mc);
                try {
                    XAResource xares = mc.getXAResource();
                    if (xares != null) {

                        // See if a wrapper class for the vendor XADataSource is
                        // specified if yes, replace the XAResouce class of database
                        // vendor with our own version

                        String datasourceClassname = jdbcConnectionPool.getDatasourceClassname();
                        String wrapperclass = (String) XAResourceWrappers.get(datasourceClassname);
                        if (wrapperclass != null) {
                            // need to load wrapper class provided by "transactions" module.
                            // Using connector-class-loader so as to get access to "transaction" module.
                            XAResourceWrapper xaresWrapper = null;
                            xaresWrapper = (XAResourceWrapper) crt.getConnectorClassLoader().loadClass(wrapperclass).newInstance();
                            xaresWrapper.init(mc, subject);
                            if (_logger.isLoggable(Level.FINEST)) {
                                _logger.finest("adding resource " + poolInfo + " -- " + xaresWrapper);
                            }
                            xaresList.add(xaresWrapper);
                        } else {
                            if (_logger.isLoggable(Level.FINEST)) {
                                _logger.finest("adding resource " + poolInfo + " -- " + xares);
                            }
                            xaresList.add(xares);
                        }
                    }
                } catch (ResourceException ex) {
                    _logger.log(Level.WARNING, "datasource.xadatasource_error", poolInfo);
                    if (_logger.isLoggable(Level.FINE)) {
                        _logger.log(Level.FINE, "datasource.xadatasource_error_excp", ex);
                    }
                    // ignored. Not at XA_TRANSACTION level
                }
            } catch (Exception ex) {
                _logger.log(Level.WARNING, "datasource.xadatasource_error", poolInfo);
                if (_logger.isLoggable(Level.FINE)) {
                    _logger.log(Level.FINE, "datasource.xadatasource_error_excp", ex);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void closeConnections(List connList) {
        for (Object obj : connList) {
            try {
                ManagedConnection con = (ManagedConnection) obj;
                con.destroy();
            } catch (Exception ex) {
                _logger.log(Level.WARNING, "recovery.jdbc-resource.destroy-error", ex);
            }
        }
    }

    /**
     * gets the user-name & password for the jdbc-connection-pool
     *
     * @param jdbcConnectionPool connection pool
     * @return user, password
     */
    public String[] getdbUserPasswordOfJdbcConnectionPool(JdbcConnectionPool jdbcConnectionPool) {

        String[] userPassword = new String[2];
        userPassword[0] = null;
        userPassword[1] = null;
        List<Property> properties = jdbcConnectionPool.getProperty();
        if (properties != null && properties.size() > 0) {
            for (Property property : properties) {
                String prop = property.getName().toUpperCase(Locale.getDefault());
                if ("USERNAME".equals(prop) || "USER".equals(prop)) {
                    userPassword[0] = property.getValue();
                } else if ("PASSWORD".equals(prop)) {
                    userPassword[1] = property.getValue();
                }
            }
        } else {
            return userPassword;
        }
        return userPassword;
    }
}

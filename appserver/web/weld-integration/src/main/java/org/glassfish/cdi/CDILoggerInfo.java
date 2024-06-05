/*
 * Copyright (c) 2022, 2022 Contributors to the Eclipse Foundation.
 * Copyright (c) 2013, 2020 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.cdi;

import java.util.logging.Logger;

import org.glassfish.logging.annotation.LogMessageInfo;
import org.glassfish.logging.annotation.LogMessagesResourceBundle;
import org.glassfish.logging.annotation.LoggerInfo;

/**
 * @author <a href="mailto:j.j.snyder@oracle.com">JJ Snyder</a>
 */
public class CDILoggerInfo {
    public static final String CDI_LOGMSG_PREFIX = "AS-CDI";

    @LogMessagesResourceBundle
    public static final String CDI_WELD_LOGMESSAGE_RESOURCE = "org.glassfish.cdi.LogMessages";

    @LoggerInfo(subsystem = "AS-CDI", description = "CDI", publish = true)
    public static final String CDI_LOGGER_SUBSYSTEM_NAME = "jakarta.enterprise.inject.spi";
    private static final Logger CDI_LOGGER = Logger.getLogger(CDI_LOGGER_SUBSYSTEM_NAME, CDI_WELD_LOGMESSAGE_RESOURCE);

    public static Logger getLogger() {
        return CDI_LOGGER;
    }

    @LogMessageInfo(message = "Setting Context Class Loader for {0} to {1}.", level = "FINER")
    public static final String SETTING_CONTEXT_CLASS_LOADER = CDI_LOGMSG_PREFIX + "-00001";

    @LogMessageInfo(message = "BeanDeploymentArchiveImpl::addBeanClass - adding {0} to {1}.", level = "FINE")
    public static final String ADD_BEAN_CLASS = CDI_LOGMSG_PREFIX + "-00002";

    @LogMessageInfo(message = "{0} not added to beanClasses", level = "FINE")
    public static final String ADD_BEAN_CLASS_ERROR = CDI_LOGMSG_PREFIX + "-00003";

    @LogMessageInfo(message = "Processing {0} as it has {1} or {2}.", level = "FINE")
    public static final String PROCESSING_BEANS_XML = CDI_LOGMSG_PREFIX + "-00004";

    @LogMessageInfo(message = "Error while trying to load Bean Class {0} : {1}.", level = "WARNING")
    public static final String ERROR_LOADING_BEAN_CLASS = CDI_LOGMSG_PREFIX + "-00005";

    @LogMessageInfo(message = "Processing WEB-INF/lib in {0}.", level = "FINE")
    public static final String PROCESSING_WEB_INF_LIB = CDI_LOGMSG_PREFIX + "-00006";

    @LogMessageInfo(message = "WEB-INF/lib: considering {0} as a bean archive and hence added another BDA for it.", level = "FINE")
    public static final String WEB_INF_LIB_CONSIDERING_BEAN_ARCHIVE = CDI_LOGMSG_PREFIX + "-00007";

    @LogMessageInfo(message = "WEB-INF/lib: skipping {0} as it doesn't have beans.xml.", level = "FINE")
    public static final String WEB_INF_LIB_SKIPPING_BEAN_ARCHIVE = CDI_LOGMSG_PREFIX + "-00008";

    @LogMessageInfo(message = "JAR processing: {0} as a Bean archive jar since it has META-INF/beans.xml.", level = "FINE")
    public static final String PROCESSING_BDA_JAR = CDI_LOGMSG_PREFIX + "-00009";

    @LogMessageInfo(message = "Ensure {0} is associated with {1}", level = "FINE")
    public static final String ENSURE_ASSOCIATION = CDI_LOGMSG_PREFIX + "-00010";

    @LogMessageInfo(message = "Ensure web lib jar visibility.  Updating {0}.", level = "FINE")
    public static final String ENSURE_WEB_LIB_JAR_VISIBILITY = CDI_LOGMSG_PREFIX + "-00011";

    @LogMessageInfo(message = "Ensure web lib jar visibility.  Updating {0} to include {1}.", level = "FINE")
    public static final String ENSURE_WEB_LIB_JAR_VISIBILITY_2 = CDI_LOGMSG_PREFIX + "-00012";

    @LogMessageInfo(message = "Collecting jar info for {0}.", level = "FINE")
    public static final String COLLECTING_JAR_INFO = CDI_LOGMSG_PREFIX + "-00013";

    @LogMessageInfo(message = "Error reading archive.  {0}", level = "FINE")
    public static final String ERROR_READING_ARCHIVE = CDI_LOGMSG_PREFIX + "-00014";

    @LogMessageInfo(message = "Collecting rar info for {0}.", level = "FINE")
    public static final String COLLECTING_RAR_INFO = CDI_LOGMSG_PREFIX + "-00015";

    @LogMessageInfo(message = "Using Context Class Loader.", level = "FINE")
    public static final String USING_CONTEXT_CLASS_LOADER = CDI_LOGMSG_PREFIX + "-00016";

    @LogMessageInfo(message = "Context Class Loader is null. Using DeploymentImpl's classloader.", level = "FINE")
    public static final String CONTEXT_CLASS_LOADER_NULL = CDI_LOGMSG_PREFIX + "-00017";

    @LogMessageInfo(message = "Creating deployment for archive: {0}.", level = "FINE")
    public static final String CREATING_DEPLOYMENT_ARCHIVE = CDI_LOGMSG_PREFIX + "-00018";

    @LogMessageInfo(message = "getBeanDeploymentArchives returning {0}.", level = "FINE")
    public static final String GET_BEAN_DEPLOYMENT_ARCHIVES = CDI_LOGMSG_PREFIX + "-00019";

    @LogMessageInfo(message = "loadBeanDeploymentArchive for beanClass {0}", level = "FINE")
    public static final String LOAD_BEAN_DEPLOYMENT_ARCHIVE = CDI_LOGMSG_PREFIX + "-00020";

    @LogMessageInfo(message = "loadBeanDeploymentArchive checking for {0} in root BDA {0}.", level = "FINE")
    public static final String LOAD_BEAN_DEPLOYMENT_ARCHIVE_CHECKING = CDI_LOGMSG_PREFIX + "-00021";

    @LogMessageInfo(message = "loadBeanDeploymentArchive An existing BDA has this class {0}.  "
            + "Adding this class as a bean class it to existing bda: {1}.", level = "FINE")
    public static final String LOAD_BEAN_DEPLOYMENT_ARCHIVE_ADD_TO_EXISTING = CDI_LOGMSG_PREFIX + "-00022";

    @LogMessageInfo(message = "loadBeanDeploymentArchive checking for {0} in subBDA {1}.", level = "FINE")
    public static final String LOAD_BEAN_DEPLOYMENT_ARCHIVE_CHECKING_SUBBDA = CDI_LOGMSG_PREFIX + "-00023";

    @LogMessageInfo(message = "loadBeanDeploymentArchive beanClass {0} not found in the BDAs of this deployment. "
            + "Creating a new BDA.", level = "FINE")
    public static final String LOAD_BEAN_DEPLOYMENT_ARCHIVE_CREATE_NEW_BDA = CDI_LOGMSG_PREFIX + "-00024";

    @LogMessageInfo(message = "loadBeanDeploymentArchive new BDA {0} created. Adding new BDA to all root BDAs of this deployment.", level = "FINE")
    public static final String LOAD_BEAN_DEPLOYMENT_ARCHIVE_ADD_NEW_BDA_TO_ROOTS = CDI_LOGMSG_PREFIX + "-00025";

    @LogMessageInfo(message = "loadBeanDeploymentArchive for beanClass {0} returning the newly created BDA {1}", level = "FINE")
    public static final String LOAD_BEAN_DEPLOYMENT_ARCHIVE_RETURNING_NEWLY_CREATED_BDA = CDI_LOGMSG_PREFIX + "-00026";

    @LogMessageInfo(message = "Exception thrown while scanning for library jars. {0}", level = "FINE")
    public static final String EXCEPTION_SCANNING_JARS = CDI_LOGMSG_PREFIX + "-00027";

    @LogMessageInfo(message = "Processing {0} as it has one or more qualified CDI-annotated beans", level = "FINE")
    public static final String PROCESSING_CDI_ENABLED_ARCHIVE = CDI_LOGMSG_PREFIX + "-00028";

    @LogMessageInfo(message = "JAR processing: {0} since it contains one or more classes with a scope annotation", level = "FINE")
    public static final String PROCESSING_BECAUSE_SCOPE_ANNOTATION = CDI_LOGMSG_PREFIX + "-00029";

    @LogMessageInfo(message = "BDAImpl::ensureWebLibJarVisibility - {0} being associated with {1}", level = "FINE")
    public static final String ENSURE_WEB_LIB_JAR_VISIBILITY_ASSOCIATION = CDI_LOGMSG_PREFIX + "-00030";

    @LogMessageInfo(message = "BDAImpl::ensureWebLibJarVisibility - updating {0}", level = "FINE")
    public static final String ENSURE_WEB_LIB_JAR_VISIBILITY_ASSOCIATION_UPDATING = CDI_LOGMSG_PREFIX + "-00031";

    @LogMessageInfo(message = "BDAImpl::ensureWebLibJarVisibility - updating {0} to include {1}", level = "FINE")
    public static final String ENSURE_WEB_LIB_JAR_VISIBILITY_ASSOCIATION_INCLUDING = CDI_LOGMSG_PREFIX + "-00032";

    @LogMessageInfo(message = "Error reading archive : {0}", level = "SEVERE", cause = "MalformedURLException reading entry from the archive.", action = "Verify the archive is not corrupt.")
    public static final String SEVERE_ERROR_READING_ARCHIVE = CDI_LOGMSG_PREFIX + "-00033";

    @LogMessageInfo(message = "TCL is null. Using BeanDeploymentArchiveImpl's classloader", level = "FINE")
    public static final String TCL_NULL = CDI_LOGMSG_PREFIX + "-00034";

    @LogMessageInfo(message = "Could not create WeldELContextListener instance. {0}", level = "WARNING")
    public static final String CDI_COULD_NOT_CREATE_WELDELCONTEXTlISTENER = CDI_LOGMSG_PREFIX + "-00035";

    @LogMessageInfo(message = "Exception in WeldBootstrap.shutdown.", level = "WARNING")
    public static final String WELD_BOOTSTRAP_SHUTDOWN_EXCEPTION = CDI_LOGMSG_PREFIX + "-00036";

    @LogMessageInfo(message = "jakarta.jms.MessageListener Class available, so need to fire PIT events to MDBs", level = "FINE")
    public static final String JMS_MESSAGElISTENER_AVAILABLE = CDI_LOGMSG_PREFIX + "-00037";

    @LogMessageInfo(message = "{0} is an MDB and so need to fire a PIT event to it.", level = "FINE")
    public static final String MDB_PIT_EVENT = CDI_LOGMSG_PREFIX + "-00038";

    @LogMessageInfo(message = "WeldDeployer adding injectionServices {0} for {1}.", level = "FINE")
    public static final String ADDING_INJECTION_SERVICES = CDI_LOGMSG_PREFIX + "-00039";

    @LogMessageInfo(message = "Unable to create URI for URL: {0}.  Exception: {1}", level = "SEVERE", cause = "URL for META-INF/services/faces-config.xml is invalid.", action = "Verify META-INF/services/faces-config.xml exists.")
    public static final String SEVERE_ERROR_CREATING_URI_FOR_FACES_CONFIG_XML = CDI_LOGMSG_PREFIX + "-00040";

    @LogMessageInfo(message = "Trying to register interceptor: {0}", level = "FINE")
    public static final String TRYING_TO_REGISTER_INTERCEPTOR = CDI_LOGMSG_PREFIX + "-00041";

    @LogMessageInfo(message = "Adding interceptor: {0} for EJB: {1}.", level = "FINE")
    public static final String ADDING_INTERCEPTOR_FOR_EJB = CDI_LOGMSG_PREFIX + "-00042";

    @LogMessageInfo(message = "getBDAForBeanClass -- search in {0} for {1}", level = "FINE")
    public static final String GET_BDA_FOR_BEAN_CLASS_SEARCH = CDI_LOGMSG_PREFIX + "-00043";

    @LogMessageInfo(message = "CDIServiceImpl.getBDAForBeanClass: TopLevelBDA {0} contains beanClassName: {1}.", level = "FINE")
    public static final String TOP_LEVEL_BDA_CONTAINS_BEAN_CLASS_NAME = CDI_LOGMSG_PREFIX + "-00044";

    @LogMessageInfo(message = "CDIServiceImpl.getBDAForBeanClass: subBDA {0} contains beanClassName: {1}.", level = "FINE")
    public static final String SUB_BDA_CONTAINS_BEAN_CLASS_NAME = CDI_LOGMSG_PREFIX + "-00045";

}

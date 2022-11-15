/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 * Copyright (c) 2010, 2020 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.resources.admin.cli;

import com.sun.enterprise.config.serverbeans.Resource;
import com.sun.enterprise.config.serverbeans.Resources;
import com.sun.enterprise.config.serverbeans.ServerTags;
import com.sun.enterprise.util.LocalStringManagerImpl;

import jakarta.inject.Inject;
import jakarta.resource.ResourceException;

import java.beans.PropertyVetoException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.glassfish.api.I18n;
import org.glassfish.config.support.CommandTarget;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.resourcebase.resources.admin.cli.ResourceUtil;
import org.glassfish.resourcebase.resources.api.ResourceStatus;
import org.glassfish.resourcebase.resources.util.BindableResourcesHelper;
import org.glassfish.resources.config.ExternalJndiResource;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.SingleConfigCode;
import org.jvnet.hk2.config.TransactionFailure;
import org.jvnet.hk2.config.types.Property;

import static org.glassfish.resources.admin.cli.ResourceConstants.ENABLED;
import static org.glassfish.resources.admin.cli.ResourceConstants.FACTORY_CLASS;
import static org.glassfish.resources.admin.cli.ResourceConstants.JNDI_LOOKUP;
import static org.glassfish.resources.admin.cli.ResourceConstants.JNDI_NAME;
import static org.glassfish.resources.admin.cli.ResourceConstants.RES_TYPE;


@Service(name = ServerTags.EXTERNAL_JNDI_RESOURCE)
@PerLookup
@I18n("create.jndi.resource")
public class JndiResourceManager implements ResourceManager {
    final private static LocalStringManagerImpl localStrings =
            new LocalStringManagerImpl(JndiResourceManager.class);
    private static final String DESCRIPTION = ServerTags.DESCRIPTION;

    @Inject
    private ResourceUtil resourceUtil;

    @Inject
    private BindableResourcesHelper resourcesHelper;

    private String resType;
    private String factoryClass;
    private String jndiLookupName;
    private String enabled;
    private String enabledValueForTarget;
    private String description;
    private String jndiName;

    @Override
    public String getResourceType() {
        return ServerTags.EXTERNAL_JNDI_RESOURCE;
    }

    @Override
    public ResourceStatus create(Resources resources, HashMap attributes, final Properties properties,
                                 String target) throws Exception {
        setAttributes(attributes, target);

        ResourceStatus validationStatus = isValid(resources, true, target);
        if (validationStatus.getStatus() == ResourceStatus.FAILURE) {
            return validationStatus;
        }
        try {
            SingleConfigCode<Resources> configCode = param -> createResource(param, properties);
            ConfigSupport.apply(configCode, resources);
            if (!CommandTarget.TARGET_DOMAIN.equals(target)) {
                resourceUtil.createResourceRef(jndiName, enabledValueForTarget, target);
            }
        } catch (TransactionFailure tfe) {
            String msg = localStrings.getLocalString(
                    "create.jndi.resource.fail",
                    "Unable to create jndi resource {0}.", jndiName) +
                    " " + tfe.getLocalizedMessage();
            return new ResourceStatus(ResourceStatus.FAILURE, msg, true);
        }

        String msg = localStrings.getLocalString(
                "create.jndi.resource.success",
                "jndi resource {0} created.", jndiName);
        return new ResourceStatus(ResourceStatus.SUCCESS, msg, true);
    }

    private ResourceStatus isValid(Resources resources, boolean validateResourceRef, String target) {
        ResourceStatus status ;
        if (resType == null) {
            String msg = localStrings.getLocalString(
                    "create.jndi.resource.noResType",
                    "No type defined for JNDI resource.");
            return new ResourceStatus(ResourceStatus.FAILURE, msg, true);
        }

        if (factoryClass == null) {
            String msg = localStrings.getLocalString(
                    "create.jndi.resource.noFactoryClassName",
                    "No Factory class name defined for JNDI resource.");
            return new ResourceStatus(ResourceStatus.FAILURE, msg, true);
        }

        if (jndiLookupName == null) {
            String msg = localStrings.getLocalString(
                    "create.jndi.resource.noJndiLookupName",
                    "No Jndi Lookup name defined for JNDI resource.");
            return new ResourceStatus(ResourceStatus.FAILURE, msg, true);
        }

        status = resourcesHelper.validateBindableResourceForDuplicates(resources, jndiName, validateResourceRef,
                target, ExternalJndiResource.class);
        if(status.getStatus() == ResourceStatus.FAILURE){
            return status;
        }

        return status;
    }

    private void setAttributes(HashMap attributes, String target) {
        jndiName = (String) attributes.get(JNDI_NAME);
        jndiLookupName = (String) attributes.get(JNDI_LOOKUP);
        resType = (String) attributes.get(RES_TYPE);
        factoryClass = (String) attributes.get(FACTORY_CLASS);

        if (target != null) {
            enabled = resourceUtil.computeEnabledValueForResourceBasedOnTarget((String) attributes.get(ENABLED), target);
        } else {
            enabled = (String) attributes.get(ENABLED);
        }
        enabledValueForTarget = (String) attributes.get(ENABLED);
        description = (String) attributes.get(DESCRIPTION);
    }

    private Object createResource(Resources param, Properties properties) throws PropertyVetoException,
            TransactionFailure {
        ExternalJndiResource newResource = createConfigBean(param, properties);
        param.getResources().add(newResource);
        return newResource;
    }

    private ExternalJndiResource createConfigBean(Resources param, Properties properties) throws PropertyVetoException,
            TransactionFailure {
        ExternalJndiResource newResource = param.createChild(ExternalJndiResource.class);
        newResource.setJndiName(jndiName);
        newResource.setFactoryClass(factoryClass);
        newResource.setJndiLookupName(jndiLookupName);
        newResource.setResType(resType);
        newResource.setEnabled(enabled);
        if (description != null) {
            newResource.setDescription(description);
        }
        if (properties != null) {
            for (Map.Entry e : properties.entrySet()) {
                Property prop = newResource.createChild(Property.class);
                prop.setName((String) e.getKey());
                prop.setValue((String) e.getValue());
                newResource.getProperty().add(prop);
            }
        }
        return newResource;
    }

    @Override
    public Resource createConfigBean(Resources resources, HashMap attributes, Properties properties, boolean validate)
            throws Exception {
        setAttributes(attributes, null);
        ResourceStatus status = null;
        if (!validate) {
            status = new ResourceStatus(ResourceStatus.SUCCESS, "");
        } else {
            status = isValid(resources, false, null);
        }
        if (status.getStatus() == ResourceStatus.SUCCESS) {
            return createConfigBean(resources, properties);
        } else {
            throw new ResourceException(status.getMessage());
        }
    }
}

/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.deployment.node.runtime.application.gf;

import com.sun.enterprise.deployment.Application;
import com.sun.enterprise.deployment.BundleDescriptor;
import com.sun.enterprise.deployment.node.ApplicationNode;
import com.sun.enterprise.deployment.node.XMLElement;
import com.sun.enterprise.deployment.node.runtime.EjbRefNode;
import com.sun.enterprise.deployment.node.runtime.MessageDestinationRefNode;
import com.sun.enterprise.deployment.node.runtime.MessageDestinationRuntimeNode;
import com.sun.enterprise.deployment.node.runtime.ResourceEnvRefNode;
import com.sun.enterprise.deployment.node.runtime.ResourceRefNode;
import com.sun.enterprise.deployment.node.runtime.RuntimeBundleNode;
import com.sun.enterprise.deployment.node.runtime.RuntimeDescriptorNode;
import com.sun.enterprise.deployment.node.runtime.ServiceRefNode;
import com.sun.enterprise.deployment.node.runtime.common.SecurityRoleMappingNode;
import com.sun.enterprise.deployment.runtime.common.PrincipalNameDescriptor;
import com.sun.enterprise.deployment.runtime.common.SecurityRoleMapping;
import com.sun.enterprise.deployment.util.DOLUtils;
import com.sun.enterprise.deployment.xml.DTDRegistry;
import com.sun.enterprise.deployment.xml.RuntimeTagNames;
import com.sun.enterprise.deployment.xml.TagNames;
import com.sun.enterprise.deployment.xml.WebServicesTagNames;

import java.util.List;
import java.util.Map;

import org.glassfish.deployment.common.ModuleDescriptor;
import org.glassfish.deployment.common.SecurityRoleMapper;
import org.glassfish.security.common.Group;
import org.glassfish.security.common.Role;
import org.w3c.dom.Node;

/**
 * This node handles all runtime-information pertinent to applications
 * The reading needs to be backward compatible with J2EE 1.2 and 1.3
 * where all runtime information was saved at the .ear file level in an
 * unique sun-ri.xml file. In J2EE 1.4, each sub archivist is responsible
 * for saving its runtime-info at his level.
 *
 * @author Jerome Dochez
 */
public class ApplicationRuntimeNode extends RuntimeBundleNode<Application> {

    private String currentWebUri;

    public ApplicationRuntimeNode(Application descriptor) {
        super(descriptor);
        //trigger registration in standard node, if it hasn't happened
        serviceLocator.getService(ApplicationNode.class);
    }

    /**
     * Initialize the child handlers
     */
    @Override
    protected void init() {
        super.init();
        registerElementHandler(new XMLElement(RuntimeTagNames.SECURITY_ROLE_MAPPING), SecurityRoleMappingNode.class);
        registerElementHandler(new XMLElement(TagNames.RESOURCE_REFERENCE), ResourceRefNode.class);
        registerElementHandler(new XMLElement(TagNames.EJB_REFERENCE), EjbRefNode.class);
        registerElementHandler(new XMLElement(TagNames.RESOURCE_ENV_REFERENCE), ResourceEnvRefNode.class);
        registerElementHandler(new XMLElement(TagNames.MESSAGE_DESTINATION_REFERENCE), MessageDestinationRefNode.class);
        registerElementHandler(new XMLElement(RuntimeTagNames.MESSAGE_DESTINATION), MessageDestinationRuntimeNode.class);
        registerElementHandler(new XMLElement(WebServicesTagNames.SERVICE_REF), ServiceRefNode.class);
    }


    /**
     * register this node as a root node capable of loading entire DD files
     *
     * @param publicIDToDTD is a mapping between xml Public-ID to DTD
     * @return the doctype tag name
     */
    public static String registerBundle(Map<String, String> publicIDToDTD, Map<String, List<Class<?>>> versionUpgrades) {
        publicIDToDTD.put(DTDRegistry.SUN_APPLICATION_130_DTD_PUBLIC_ID, DTDRegistry.SUN_APPLICATION_130_DTD_SYSTEM_ID);
        publicIDToDTD.put(DTDRegistry.SUN_APPLICATION_140_DTD_PUBLIC_ID, DTDRegistry.SUN_APPLICATION_140_DTD_SYSTEM_ID);
        publicIDToDTD.put(DTDRegistry.SUN_APPLICATION_141_DTD_PUBLIC_ID, DTDRegistry.SUN_APPLICATION_141_DTD_SYSTEM_ID);
        publicIDToDTD.put(DTDRegistry.SUN_APPLICATION_500_DTD_PUBLIC_ID, DTDRegistry.SUN_APPLICATION_500_DTD_SYSTEM_ID);
        publicIDToDTD.put(DTDRegistry.SUN_APPLICATION_600_DTD_PUBLIC_ID, DTDRegistry.SUN_APPLICATION_600_DTD_SYSTEM_ID);
        if (!restrictDTDDeclarations()) {
            publicIDToDTD.put(DTDRegistry.SUN_APPLICATION_140beta_DTD_PUBLIC_ID,
                DTDRegistry.SUN_APPLICATION_140beta_DTD_SYSTEM_ID);
        }
        return RuntimeTagNames.S1AS_APPLICATION_RUNTIME_TAG;
    }


    /**
     * @return the XML tag associated with this XMLNode
     */
    @Override
    protected XMLElement getXMLRootTag() {
        return new XMLElement(RuntimeTagNames.S1AS_APPLICATION_RUNTIME_TAG);
    }


    /**
     * @return the DOCTYPE that should be written to the XML file
     */
    @Override
    public String getDocType() {
        return DTDRegistry.SUN_APPLICATION_600_DTD_PUBLIC_ID;
    }


    /**
     * @return the SystemID of the XML file
     */
    @Override
    public String getSystemID() {
        return DTDRegistry.SUN_APPLICATION_600_DTD_SYSTEM_ID;
    }


    /**
     * @return NULL for all runtime nodes.
     */
    @Override
    public List<String> getSystemIDs() {
        return null;
    }


    /**
     * all sub-implementation of this class can use a dispatch table to map xml element to
     * method name on the descriptor class for setting the element value.
     *
     * @return the map with the element name as a key, the setter method as a value
     */
    @Override
    protected Map<String, String> getDispatchTable() {
        Map<String, String> table = super.getDispatchTable();
        table.put(RuntimeTagNames.REALM, "setRealm");
        return table;
    }


    /**
     * receives notification of the value for a particular tag
     *
     * @param element the xml element
     * @param value it's associated value
     */
    @Override
    public void setElementValue(XMLElement element, String value) {
        if (element.getQName().equals(RuntimeTagNames.PASS_BY_REFERENCE)) {
            descriptor.setPassByReference("true".equalsIgnoreCase(value));
        } else if (element.getQName().equals(RuntimeTagNames.UNIQUE_ID)) {
            DOLUtils.getDefaultLogger().finer("Ignoring unique id");
            return;
        } else if (element.getQName().equals(RuntimeTagNames.ARCHIVE_NAME)) {
            descriptor.setAppName(value);
            descriptor.setArchiveName(value);
        } else if (element.getQName().equals(RuntimeTagNames.COMPATIBILITY)) {
            descriptor.setCompatibility(value);
        } else if (element.getQName().equals(RuntimeTagNames.WEB_URI)) {
            currentWebUri = value;
        } else if (element.getQName().equals(RuntimeTagNames.CONTEXT_ROOT)) {
            if (currentWebUri != null) {
                ModuleDescriptor<BundleDescriptor> md = descriptor.getModuleDescriptorByUri(currentWebUri);
                if (md == null) {
                    throw new RuntimeException("No bundle in application with uri " + currentWebUri);
                }
                currentWebUri = null;
                if (md.getModuleType().equals(DOLUtils.warType())) {
                    md.setContextRoot(value);
                } else {
                    throw new RuntimeException(currentWebUri + " uri does not point to a web bundle");
                }
            } else {
                throw new RuntimeException("No uri provided for this context-root " + value);
            }
        } else if (element.getQName().equals(RuntimeTagNames.KEEP_STATE)) {
            descriptor.setKeepState(value);
        } else if (element.getQName().equals(RuntimeTagNames.VERSION_IDENTIFIER)) {
        } else {
            super.setElementValue(element, value);
        }
    }


    /**
     * Adds a new DOL descriptor instance to the descriptor instance associated with
     * this XMLNode
     *
     * @param newDescriptor the new descriptor
     */
    @Override
    public void addDescriptor(Object newDescriptor) {
        if (newDescriptor instanceof SecurityRoleMapping) {
            SecurityRoleMapping roleMap = (SecurityRoleMapping) newDescriptor;
            if (descriptor != null && !descriptor.isVirtual()) {
                descriptor.addSecurityRoleMapping(roleMap);
                Role role = new Role(roleMap.getRoleName());
                SecurityRoleMapper rm = descriptor.getRoleMapper();
                if (rm != null) {
                    List<PrincipalNameDescriptor> principals = roleMap.getPrincipalNames();
                    for (PrincipalNameDescriptor principal : principals) {
                        rm.assignRole(principal.toPrincipal(), role, descriptor);
                    }
                    List<String> groups = roleMap.getGroupNames();
                    for (String group : groups) {
                        rm.assignRole(new Group(group), role, descriptor);
                    }
                }
            }
        }
    }


    /**
     * write the descriptor class to a DOM tree and return it
     *
     * @param parent node for the DOM tree
     * @param nodeName the node name
     * @param application the descriptor to write
     * @return the DOM tree top node
     */
    @Override
    public Node writeDescriptor(Node parent, String nodeName, Application application) {
        Node appNode = super.writeDescriptor(parent, nodeName, application);

        // web*
        for (ModuleDescriptor<BundleDescriptor> module : application.getModules()) {
            if (module.getModuleType().equals(DOLUtils.warType())) {
                Node web = appendChild(appNode, RuntimeTagNames.WEB);
                appendTextChild(web, RuntimeTagNames.WEB_URI, module.getArchiveUri());
                appendTextChild(web, RuntimeTagNames.CONTEXT_ROOT, module.getContextRoot());
            }
        }

        // pass-by-reference ?
        if (application.isPassByReferenceDefined()) {
            appendTextChild(appNode, RuntimeTagNames.PASS_BY_REFERENCE,
                String.valueOf(application.getPassByReference()));
        }

        // NOTE : unique-id is no longer written out to sun-ejb-jar.xml. It is persisted via
        // domain.xml deployment context properties instead.

        // security-role-mapping*
        List<SecurityRoleMapping> roleMappings = application.getSecurityRoleMappings();
        for (SecurityRoleMapping roleMapping : roleMappings) {
            SecurityRoleMappingNode srmn = new SecurityRoleMappingNode();
            srmn.writeDescriptor(appNode, RuntimeTagNames.SECURITY_ROLE_MAPPING, roleMapping);
        }

        // realm?
        appendTextChild(appNode, RuntimeTagNames.REALM, application.getRealm());

        // references
        RuntimeDescriptorNode.writeCommonComponentInfo(appNode, application);
        RuntimeDescriptorNode.writeMessageDestinationInfo(appNode, application);

        // archive-name
        appendTextChild(appNode, RuntimeTagNames.ARCHIVE_NAME, application.getArchiveName());

        // compatibility
        appendTextChild(appNode, RuntimeTagNames.COMPATIBILITY, application.getCompatibility());

        // keep-state
        appendTextChild(appNode, RuntimeTagNames.KEEP_STATE, String.valueOf(application.getKeepState()));

        return appNode;
    }
}

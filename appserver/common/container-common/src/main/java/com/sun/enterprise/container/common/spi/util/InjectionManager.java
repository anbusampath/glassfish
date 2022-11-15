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

package com.sun.enterprise.container.common.spi.util;

import com.sun.enterprise.deployment.JndiNameEnvironment;

import org.glassfish.api.naming.SimpleJndiName;
import org.jvnet.hk2.annotations.Contract;

/**
 * InjectionManager provides runtime resource injection(@Resource, @EJB, etc.)
 * and generic callback(PostConstruct/PreDestroy) services.
 * <p>
 * It performs the actual injection into the fields and methods of designated
 * JEE component instances and managed class instances. The decision
 * as to when injection takes place is determined by the caller.
 *
 * @author Kenneth Saks
 */
@Contract
public interface InjectionManager {

    /**
     * Inject the given object instance with the resources from its
     * component environment. The applicable component naming environment
     * information will be retrieved from the current invocation context.
     * Any @PostConstruct methods on the instance's class(and super-classes)
     * will be invoked after injection.
     *
     * @exception InjectionException Thrown if an error occurs during injection
     */
    void injectInstance(Object instance) throws InjectionException;

    /**
     * Inject the given object instance with the resources from its
     * component environment. The applicable component naming environment
     * information will be retrieved from the current invocation context.
     * If invokePostConstruct is true, any @PostConstruct methods on the
     * instance's class(and super-classes) will be invoked after injection.
     *
     * @exception InjectionException Thrown if an error occurs during injection
     */
    void injectInstance(Object instance, boolean invokePostConstruct) throws InjectionException;

    /**
     * Inject the injectable resources from the given component environment
     * into an object instance. The specified componentEnv must match the
     * environment that is associated with the component on top of the
     * invocation stack at the time this method is invoked.
     * Any @PostConstruct methods on the instance's class(and super-classes)
     * will be invoked after injection.
     *
     * @exception InjectionException Thrown if an error occurs during injection
     */
    void injectInstance(Object instance, JndiNameEnvironment componentEnv) throws InjectionException;

    /**
     * Inject the injectable resources from the given component environment
     * into an object instance. The specified componentEnv must match the
     * environment that is associated with the component on top of the
     * invocation stack at the time this method is invoked.
     *
     * @param invokePostConstruct if true, invoke any @PostConstruct methods
     *            on the instance's class(and super-classes) after injection.
     * @exception InjectionException Thrown if an error occurs during injection
     */
    void injectInstance(Object instance, JndiNameEnvironment componentEnv, boolean invokePostConstruct)
        throws InjectionException;

    /**
     * Inject the injectable resources for the given component id
     * into an object instance.
     *
     * @param invokePostConstruct if true, invoke any @PostConstruct methods
     *            on the instance's class(and super-classes) after injection.
     * @exception InjectionException Thrown if an error occurs during injection
     */
    void injectInstance(Object instance, SimpleJndiName globalJndiName, boolean invokePostConstruct)
        throws InjectionException;

    /**
     * Inject the injectable resources from the given component id
     * into a Class instance. Only class-level(static) fields/methods are
     * supported. E.g., this injection operation would be used for the
     * Application Client Container main class.
     *
     * @param invokePostConstruct if true, invoke any @PostConstruct methods
     *            on the class(and super-classes) after injection.
     * @exception InjectionException Thrown if an error occurs during injection
     */
    void injectClass(Class<?> clazz, SimpleJndiName jndiName, boolean invokePostConstruct) throws InjectionException;

    /**
     * Inject the injectable resources from the given component environment
     * into a Class instance. Only class-level(static) fields/methods are
     * supported. E.g., this injection operation would be used for the
     * Application Client Container main class.
     * Any @PostConstruct methods on the class(and super-classes)
     * will be invoked after injection.
     *
     * @exception InjectionException Thrown if an error occurs during injection
     */
    void injectClass(Class<?> clazz, JndiNameEnvironment componentEnv) throws InjectionException;

    /**
     * Inject the injectable resources from the given component environment
     * into a Class instance. Only class-level(static) fields/methods are
     * supported. E.g., this injection operation would be used for the
     * Application Client Container main class.
     *
     * @param invokePostConstruct if true, invoke any @PostConstruct methods
     *            on the class(and super-classes) after injection.
     * @exception InjectionException Thrown if an error occurs during injection
     */
    void injectClass(Class<?> clazz, JndiNameEnvironment componentEnv, boolean invokePostConstruct)
        throws InjectionException;

    /**
     * Invoke any @PreDestroy methods defined on the instance's class
     * (and super-classes). Invocation information will be retrieved from
     * the current component invocation context.
     *
     * @exception InjectionException Thrown if an error occurs
     */
    void invokeInstancePreDestroy(Object instance) throws InjectionException;

    /**
     * Invoke any @PreDestroy methods defined on the instance's class
     * (and super-classes). Invocation information will be retrieved from
     * the current component invocation context.
     *
     * @param validate if false, do nothing if the instance is not registered
     * @exception InjectionException Thrown if an error occurs
     */
    void invokeInstancePreDestroy(Object instance, boolean validate) throws InjectionException;

    /**
     * Invoke any @PreDestroy methods defined on the instance's class
     * (and super-classes). The specified componentEnv must match the
     * environment that is associated with the component on top of the
     * invocation stack at the time this method is invoked.
     *
     * @exception InjectionException Thrown if an error occurs
     */
    void invokeInstancePreDestroy(Object instance, JndiNameEnvironment componentEnv) throws InjectionException;

    /**
     * Invoke any @PostConstruct methods defined on the instance's class
     * (and super-classes). The specified componentEnv must match the
     * environment that is associated with the component on top of the
     * invocation stack at the time this method is invoked.
     *
     * @exception InjectionException Thrown if an error occurs
     */
    void invokeInstancePostConstruct(Object instance, JndiNameEnvironment componentEnv) throws InjectionException;

    /**
     * Invoke any static @PreDestroy methods defined on the class
     * (and super-classes). The specified componentEnv must match the
     * environment that is associated with the component on top of the
     * invocation stack at the time this method is invoked.
     *
     * @exception InjectionException Thrown if an error occurs
     */
    void invokeClassPreDestroy(Class<?> clazz, JndiNameEnvironment componentEnv) throws InjectionException;

    /**
     * Create a managed object for the given class. The object will be
     * injected and any PostConstruct methods will be called. The returned
     * object can be cast to the clazz type but is not necessarily a direct
     * reference to the managed instance. All invocations on the returned
     * object should be on its public methods.
     * It is the responsibility of the caller to destroy the returned object
     * by calling destroyManagedObject(Object managedObject).
     *
     * @param clazz Class to be instantiated
     * @return managed object
     * @throws InjectionException
     */
    <T> T createManagedObject(Class<T> clazz) throws InjectionException;

    /**
     * Create a managed object for the given class. The object will be
     * injected and if invokePostConstruct is true, any @PostConstruct
     * methods on the instance's class(and super-classes) will be invoked
     * after injection. The returned
     * object can be cast to the clazz type but is not necessarily a direct
     * reference to the managed instance. All invocations on the returned
     * object should be on its public methods.
     * It is the responsibility of the caller to destroy the returned object
     * by calling destroyManagedObject(Object managedObject).
     *
     * @param clazz Class to be instantiated
     * @param invokePostConstruct if true, invoke any @PostConstruct methods
     *            on the instance's class(and super-classes) after injection.
     * @return managed object
     * @throws InjectionException
     */
    <T> T createManagedObject(Class<T> clazz, boolean invokePostConstruct) throws InjectionException;

    /**
     * Destroy a managed object that was created via createManagedObject. Any
     * PreDestroy methods will be called.
     *
     * @param managedObject
     * @throws InjectionException
     */
    void destroyManagedObject(Object managedObject) throws InjectionException;

    /**
     * Destroy a managed object that may have been created via createManagedObject. Any
     * PreDestroy methods will be called.
     *
     * @param managedObject
     * @param validate if false the object might not been created by createManagedObject() call
     * @throws InjectionException
     */
    void destroyManagedObject(Object managedObject, boolean validate) throws InjectionException;

    /**
     * Perform injection.
     *
     * @param clazz The class of the instance to perform injection. This is needed b/c of static injection.
     * @param instance The instance on which to perform injection.
     * @param envDescriptor The descriptor containing the injection information.
     * @param componentId The id of the component in whose jndi environment injection actually
     *            occurs. Null indicates the current jndi environment.
     * @param invokePostConstruct if true, invoke any @PostConstruct methods
     * @throws InjectionException
     */
    <T> void inject(final Class<? extends T> clazz, final T instance, JndiNameEnvironment envDescriptor,
        String componentId, boolean invokePostConstruct) throws InjectionException;
}

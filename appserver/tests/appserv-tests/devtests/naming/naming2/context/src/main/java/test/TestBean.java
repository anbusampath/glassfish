/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package test;

import jakarta.annotation.Resource;
import jakarta.ejb.EJB;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;


import java.util.Properties;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

@Singleton @Startup
public class TestBean {
    @EJB private TestBean testBean;
    @Resource(lookup="java:comp/InAppClientContainer")
    private Boolean isInAppClientContainer;
    public String hello() {
        return "Hello from " + this;
    }

    public <T> T lookupWithWLInitialContextFactory(String name) throws NamingException {
        Properties props = new Properties();
        props.put(Context.PROVIDER_URL, "t3://localhost:3700,localhost:3701");
        props.put(Context.INITIAL_CONTEXT_FACTORY, "weblogic.jndi.WLInitialContextFactory");
        InitialContext ic = new InitialContext(props);
        return (T) ic.lookup(name);
    }

    public NamingEnumeration<NameClassPair> listEmptyString() throws NamingException {
        Context context = new InitialContext();
        return context.list("");
    }

    public NamingEnumeration<Binding> listBindingsEmptyString() throws NamingException {
        Context context = new InitialContext();
        return context.listBindings("");
    }

    public NamingEnumeration<NameClassPair> listGlobal() throws NamingException {
        Context context = new InitialContext();
        return context.list("java:global");
    }

    public NamingEnumeration<Binding> listBindingsGlobal() throws NamingException {
        Context context = new InitialContext();
        return context.listBindings("java:global");
    }

    public NamingEnumeration<NameClassPair> listJavaComp() throws NamingException {
        Context context = (Context) new InitialContext().lookup("java:comp/");
        return context.list("env");
    }

    public NamingEnumeration<Binding> listBindingsJavaComp() throws NamingException {
        Context context = (Context) new InitialContext().lookup("java:comp/");
        return context.listBindings("env");
    }

    public NamingEnumeration<NameClassPair> listJavaModule() throws NamingException {
        Context context = (Context) new InitialContext().lookup("java:module/");
        return context.list("");
    }

    public NamingEnumeration<Binding> listBindingsJavaModule() throws NamingException {
        Context context = (Context) new InitialContext().lookup("java:module/");
        return context.listBindings("");
    }

    public NamingEnumeration<NameClassPair> listJavaApp() throws NamingException {
        Context context = (Context) new InitialContext().lookup("java:app/");
        return context.list("");
    }

    public NamingEnumeration<Binding> listBindingsJavaApp() throws NamingException {
        Context context = (Context) new InitialContext().lookup("java:app/");
        return context.listBindings("");
    }

    public void closeNamingEnumerations() throws NamingException {
        listEmptyString().close();
        listBindingsEmptyString().close();

        listJavaComp().close();
        listBindingsJavaComp().close();

        listJavaModule().close();
        listBindingsJavaModule().close();

        listJavaApp().close();
        listBindingsJavaApp().close();
    }

        public Boolean getIsInAppClientContainer() {
                return isInAppClientContainer;
        }
}

/*
 * Copyright (c) 2023, 2024 Contributors to the Eclipse Foundation.
 * Copyright (c) 1997-2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.catalina.authenticator;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.Principal;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;

import org.apache.catalina.HttpRequest;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LogFacade;
import org.apache.catalina.Logger;
import org.apache.catalina.Request;
import org.apache.catalina.Response;
import org.apache.catalina.Session;
import org.apache.catalina.SessionEvent;
import org.apache.catalina.SessionListener;
import org.apache.catalina.valves.ValveBase;

import static com.sun.logging.LogCleanerUtil.neutralizeForLog;
import static org.apache.catalina.LogFacade.ASSOCIATE_SSO_WITH_SESSION_INFO;
import static org.apache.catalina.LogFacade.START_COMPONENT_INFO;
import static org.apache.catalina.LogFacade.STOP_COMPONENT_INFO;

/**
 * A <strong>Valve</strong> that supports a "single sign on" user experience, where the security identity of a user who
 * successfully authenticates to one web application is propagated to other web applications in the same security
 * domain. For successful use, the following requirements must be met:
 * <ul>
 * <li>This Valve must be configured on the Container that represents a virtual host (typically an implementation of
 * <code>Host</code>).</li>
 * <li>The <code>Realm</code> that contains the shared user and role information must be configured on the same
 * Container (or a higher one), and not overridden at the web application level.</li>
 * <li>The web applications themselves must use one of the standard Authenticators found in the
 * <code>org.apache.catalina.authenticator</code> package.</li>
 * </ul>
 *
 * @author Craig R. McClanahan
 * @version $Revision: 1.7 $ $Date: 2007/05/05 05:31:53 $
 */

public class SingleSignOn extends ValveBase implements SessionListener {

    // ----------------------------------------------------- Static Variables

    private static final java.util.logging.Logger log = LogFacade.getLogger();
    private static final ResourceBundle rb = log.getResourceBundle();

    /**
     * Descriptive information about this Valve implementation.
     */
    protected static final String info = "org.apache.catalina.authenticator.SingleSignOn";

    // ----------------------------------------------------- Instance Variables

    /**
     * The cache of SingleSignOnEntry instances for authenticated Principals, keyed by the cookie value that is used to
     * select them.
     */
    protected Map<String, SingleSignOnEntry> cache = new HashMap<>();

    // ------------------------------------------------------------- Properties

    /**
     * Return the debugging detail level.
     */
    @Override
    public int getDebug() {
        return debug;
    }

    /**
     * Set the debugging detail level.
     *
     * @param debug The new debugging detail level
     */
    @Override
    public void setDebug(int debug) {
        this.debug = debug;
    }

    // ------------------------------------------------------ Lifecycle Methods


    /**
     * Prepare for the beginning of active use of the public methods of this component. This method should be called after
     * <code>configure()</code>, and before any of the public methods of the component are utilized.
     *
     * @exception LifecycleException if this component detects a fatal error that prevents this component from being used
     */
    @Override
    public void start() throws LifecycleException {
        if (started) { // Ignore multiple starts
            return;
        }

        super.start();

        if (debug >= 1) {
            log(rb.getString(START_COMPONENT_INFO));
        }
    }

    /**
     * Gracefully terminate the active use of the public methods of this component. This method should be the last one
     * called on a given instance of this component.
     *
     * @exception LifecycleException if this component detects a fatal error that needs to be reported
     */
    @Override
    public void stop() throws LifecycleException {
        if (!started) { // Ignore stop if not started
            return;
        }

        if (debug >= 1) {
            log(rb.getString(STOP_COMPONENT_INFO));
        }

        super.stop();
    }

    // ------------------------------------------------ SessionListener Methods

    /**
     * Acknowledge the occurrence of the specified event.
     *
     * @param event SessionEvent that has occurred
     */
    @Override
    public void sessionEvent(SessionEvent event) {

        // We only care about session destroyed events
        if (!Session.SESSION_DESTROYED_EVENT.equals(event.getType())) {
            return;
        }

        // Look up the single session id associated with this session (if any)
        Session session = event.getSession();
        if (debug >= 1) {
            String msg = MessageFormat.format(rb.getString(LogFacade.PROCESS_SESSION_DESTROYED_INFO), session);
            log(msg);
        }

        String ssoId = session.getSsoId();
        if (ssoId == null) {
            return;
        }

        deregister(ssoId, session);
    }

    // ---------------------------------------------------------- Valve Methods

    /**
     * Return descriptive information about this Valve implementation.
     */
    @Override
    public String getInfo() {
        return info;
    }

    /**
     * Perform single-sign-on support processing for this request.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    @Override
    public int invoke(Request request, Response response) throws IOException, ServletException {
        HttpServletRequest hreq = (HttpServletRequest) request.getRequest();
        HttpServletResponse hres = (HttpServletResponse) response.getResponse();
        request.removeNote(Constants.REQ_SSOID_NOTE);
        request.removeNote(Constants.REQ_SSO_VERSION_NOTE);

        // Has a valid user already been authenticated?
        if (debug >= 1) {
            String msg = MessageFormat.format(rb.getString(LogFacade.PROCESS_REQUEST_INFO), hreq.getRequestURI());
            log(msg);
        }
        if (hreq.getUserPrincipal() != null) {
            if (debug >= 1) {
                String msg = MessageFormat.format(rb.getString(LogFacade.PRINCIPAL_BEEN_AUTHENTICATED_INFO), hreq.getUserPrincipal());
                log(msg);
            }
            return END_PIPELINE;
        }

        // Check for the single sign on cookie
        if (debug >= 1) {
            log(rb.getString(LogFacade.CHECK_SSO_COOKIE_INFO));
        }
        Cookie cookie = null;
        Cookie versionCookie = null;
        Cookie cookies[] = hreq.getCookies();
        if (cookies == null) {
            cookies = new Cookie[0];
        }
        for (Cookie element : cookies) {
            if (Constants.SINGLE_SIGN_ON_COOKIE.equals(element.getName())) {
                cookie = element;
            } else if (Constants.SINGLE_SIGN_ON_VERSION_COOKIE.equals(element.getName())) {
                versionCookie = element;
            }

            if (cookie != null && versionCookie != null) {
                break;
            }
        }
        if (cookie == null) {
            if (debug >= 1) {
                log(rb.getString(LogFacade.SSO_COOKIE_NOT_PRESENT_INFO));
            }
            return INVOKE_NEXT;
        }

        // Look up the cached Principal associated with this cookie value
        if (debug >= 1) {
            String msg = MessageFormat.format(rb.getString(LogFacade.CHECK_CACHED_PRINCIPAL_INFO), cookie.getValue());
            log(msg);
        }
        long version = 0;
        if (isVersioningSupported() && versionCookie != null) {
            version = Long.parseLong(versionCookie.getValue());
        }
        SingleSignOnEntry entry = lookup(cookie.getValue(), version, request.getContext().getLoader().getClassLoader());
        if (entry != null) {
            if (debug >= 1) {
                String msg = MessageFormat.format(rb.getString(LogFacade.FOUND_CACHED_PRINCIPAL_AUTH_TYPE_INFO),
                        new Object[] { entry.getPrincipal().getName(), entry.getAuthType() });
                log(msg);
            }
            request.setNote(Constants.REQ_SSOID_NOTE, cookie.getValue());
            if (isVersioningSupported()) {
                long ver = entry.incrementAndGetVersion();
                request.setNote(Constants.REQ_SSO_VERSION_NOTE, Long.valueOf(ver));
            }

            ((HttpRequest) request).setAuthType(entry.getAuthType());
            ((HttpRequest) request).setUserPrincipal(entry.getPrincipal());
        } else {
            if (debug >= 1) {
                log(rb.getString(LogFacade.NO_CACHED_PRINCIPAL_FOUND_INFO));
            }
            cookie.setMaxAge(0);
            hres.addCookie(cookie);
        }

        // Invoke the next Valve in our pipeline
        return INVOKE_NEXT;
    }

    // --------------------------------------------------------- Public Methods

    /**
     * Return a String rendering of this object.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SingleSignOn[");
        if (container == null) {
            sb.append("Container is null");
        } else {
            sb.append(container.getName());
        }
        sb.append("]");

        return (sb.toString());
    }

    // -------------------------------------------------------- Package Methods

    /**
     * Associate the specified single sign on identifier with the specified Session.
     *
     * @param ssoId Single sign on identifier
     * @param ssoVersion Single sign on version
     * @param session Session to be associated
     */
    public void associate(String ssoId, long ssoVersion, Session session) {
        if (!started) {
            return;
        }

        if (debug >= 1) {
            log(rb.getString(ASSOCIATE_SSO_WITH_SESSION_INFO));
        }

        SingleSignOnEntry sso = lookup(ssoId, ssoVersion, null);
        if (sso != null) {
            session.setSsoId(ssoId);
            session.setSsoVersion(ssoVersion);
            sso.addSession(this, session);
        }
    }

    /**
     * Deregister the specified session. If it is the last session, then also get rid of the single sign on identifier
     *
     * @param ssoId Single sign on identifier
     * @param session Session to be deregistered
     */
    protected void deregister(String ssoId, Session session) {
        SingleSignOnEntry sso = lookup(ssoId);
        if (sso == null) {
            return;
        }

        session.setSsoId(null);
        session.setSsoVersion(0L);
        sso.removeSession(session);

        // see if we are the last session, if so blow away ssoId
        if (sso.isEmpty()) {
            synchronized (cache) {
                cache.remove(ssoId);
            }
        }
    }

    /**
     * Register the specified Principal as being associated with the specified value for the single sign on identifier.
     *
     * @param ssoId Single sign on identifier to register
     * @param principal Associated user principal that is identified
     * @param authType Authentication type used to authenticate this user principal
     * @param username Username used to authenticate this user
     * @param password Password used to authenticate this user
     */
    protected void register(String ssoId, Principal principal, String authType, String username, char[] password, String realmName) {
        if (debug >= 1) {
            log(MessageFormat.format(rb.getString(LogFacade.REGISTERING_SSO_INFO), new Object[] { ssoId, principal.getName(), authType }));
        }
        synchronized (cache) {
            cache.put(ssoId, new SingleSignOnEntry(ssoId, 0L, principal, authType, username, realmName));
        }

    }

    // ------------------------------------------------------ Protected Methods

    /**
     * Log a message on the Logger associated with our Container (if any).
     *
     * @param message Message to be logged
     */
    protected void log(String message) {
        message = neutralizeForLog(message);
        Logger logger = container.getLogger();
        if (logger != null) {
            logger.log(this.toString() + ": " + message);
        } else if (log.isLoggable(Level.INFO)) {
            log.log(Level.INFO, this.toString() + ": " + message);
        }
    }

    /**
     * Log a message on the Logger associated with our Container (if any).
     *
     * @param message Message to be logged
     * @param t Associated exception
     */
    protected void log(String message, Throwable t) {
        message = neutralizeForLog(message);
        Logger logger = container.getLogger();
        if (logger != null) {
            logger.log(this.toString() + ": " + message, t, Logger.WARNING);
        } else {
            log.log(Level.WARNING, this.toString() + ": " + message, t);
        }
    }

    /**
     * Look up and return the cached SingleSignOn entry associated with this sso id value, if there is one; otherwise return
     * <code>null</code>.
     *
     * @param ssoId Single sign on identifier to look up
     */
    protected SingleSignOnEntry lookup(String ssoId) {
        synchronized (cache) {
            return cache.get(ssoId);
        }
    }

    /**
     * Look up and return the cached SingleSignOn entry associated with this sso id value, if there is one; otherwise return
     * <code>null</code>.
     *
     * @param ssoId Single sign on identifier to look up
     * @param ssoVersion Single sign on version to look up
     */
    protected SingleSignOnEntry lookup(String ssoId, long ssoVersion, ClassLoader appClassLoader) {
        return lookup(ssoId);
    }

    /**
     * Return a boolean to indicate whether the sso id version is supported or not.
     */
    public boolean isVersioningSupported() {
        return false;
    }
}

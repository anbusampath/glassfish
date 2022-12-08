/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.admin.cli.cluster;

import com.sun.enterprise.admin.cli.CLICommand;
import com.sun.enterprise.admin.cli.remote.RemoteCLICommand;
import com.sun.enterprise.util.HostAndPort;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

import org.glassfish.api.Param;
import org.glassfish.api.admin.CommandException;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.api.ServiceLocator;
import org.jvnet.hk2.annotations.Service;

/**
 * @author Byron Nevins
 */
@Service(name = "restart-local-instance")
@PerLookup
public class RestartLocalInstanceCommand extends StopLocalInstanceCommand {

    @Param(name = "debug", optional = true)
    private Boolean debug;

    @Inject
    private ServiceLocator habitat;

    @Override
    protected final void doCommand() throws CommandException {
        // see StopLocalInstance for comments.  These 2 lines can be refactored.
        setLocalPassword();
        programOpts.setInteractive(false);

        if (!isRestartable()) {
            throw new CommandException(Strings.get("restart.notRestartable"));
        }

        // Save old values before executing restart
        final int oldPid = getServerPid();
        final HostAndPort adminAddress = getAdminAddress(getServerDirs().getServerName());
        // run the remote restart-instance command and throw away the output
        RemoteCLICommand cmd = new RemoteCLICommand("_restart-instance", programOpts, env);
        if (debug == null) {
            cmd.executeAndReturnOutput("_restart-instance");
        } else {
            cmd.executeAndReturnOutput("_restart-instance", "--debug", debug.toString());
        }

        waitForRestart(oldPid, adminAddress, adminAddress);
        logger.info("Successfully restarted the instance.");
    }

    @Override
    protected int instanceNotRunning() throws CommandException {
        logger.warning(Strings.get("restart.instanceNotRunning"));
        CLICommand cmd = habitat.getService(CLICommand.class, "start-local-instance");
        /*
         * Collect the arguments that also apply to start-instance-domain.
         * The start-local-instance CLICommand object will already have the
         * ProgramOptions injected into it so we don't need to worry
         * about them here.
         *
         * Usage: asadmin [asadmin-utility-options] start-local-instance
         *    [--verbose[=<verbose(default:false)>]]
         *    [--debug[=<debug(default:false)>]] [--sync <sync(default:normal)>]
         *    [--nodedir <nodedir>] [--node <node>]
         *    [-?|--help[=<help(default:false)>]] [instance_name]
         *
         * Only --debug, --nodedir, -node, and the operand apply here.
         */
        List<String> opts = new ArrayList<>();
        opts.add("start-local-instance");
        if (debug != null) {
            opts.add("--debug");
            opts.add(debug.toString());
        }
        if (nodeDir != null) {
            opts.add("--nodedir");
            opts.add(nodeDir);
        }
        if (node != null) {
            opts.add("--node");
            opts.add(node);
        }
        if (instanceName != null) {
            opts.add(instanceName);
        }

        return cmd.execute(opts.toArray(new String[opts.size()]));
    }
}

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

package org.glassfish.jdbc.pool.monitor;

import com.sun.enterprise.connectors.ConnectorRuntime;
import com.sun.enterprise.resource.pool.PoolLifeCycleListenerRegistry;
import com.sun.enterprise.resource.pool.PoolStatus;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.api.naming.SimpleJndiName;
import org.glassfish.external.probe.provider.annotations.ProbeListener;
import org.glassfish.external.probe.provider.annotations.ProbeParam;
import org.glassfish.external.statistics.CountStatistic;
import org.glassfish.external.statistics.RangeStatistic;
import org.glassfish.external.statistics.annotations.Reset;
import org.glassfish.external.statistics.impl.CountStatisticImpl;
import org.glassfish.external.statistics.impl.RangeStatisticImpl;
import org.glassfish.external.statistics.impl.StatisticImpl;
import org.glassfish.gmbal.AMXMetadata;
import org.glassfish.gmbal.Description;
import org.glassfish.gmbal.ManagedAttribute;
import org.glassfish.gmbal.ManagedObject;
import org.glassfish.resourcebase.resources.api.PoolInfo;

/**
 * StatsProvider object for Jdbc pool monitoring.
 *
 * Implements various events related to jdbc pool monitoring and provides
 * objects to the calling modules that retrieve monitoring information.
 *
 * @author Shalini M
 */
@AMXMetadata(type="jdbc-connection-pool-mon", group="monitoring")
@ManagedObject
@Description("JDBC Statistics")
public class JdbcConnPoolStatsProvider {

    private final PoolInfo poolInfo;
    private final Logger logger;

    //Registry that stores all listeners to this object
    private PoolLifeCycleListenerRegistry poolRegistry;


    //Objects that are exposed by this telemetry
    private final CountStatisticImpl numConnFailedValidation = new CountStatisticImpl(
            "NumConnFailedValidation", StatisticImpl.UNIT_COUNT,
            "The total number of connections in the connection pool that failed " +
            "validation from the start time until the last sample time.");

    private final CountStatisticImpl numConnTimedOut = new CountStatisticImpl(
            "NumConnTimedOut", StatisticImpl.UNIT_COUNT, "The total number of " +
            "connections in the pool that timed out between the start time and the last sample time.");

    private final RangeStatisticImpl numConnFree = new RangeStatisticImpl(
            0, 0, 0,
            "NumConnFree", StatisticImpl.UNIT_COUNT, "The total number of free " +
            "connections in the pool as of the last sampling.",
            System.currentTimeMillis(), System.currentTimeMillis());

    private final RangeStatisticImpl numConnUsed = new RangeStatisticImpl(
            0, 0, 0,
            "NumConnUsed", StatisticImpl.UNIT_COUNT, "Provides connection usage " +
            "statistics. The total number of connections that are currently being " +
            "used, as well as information about the maximum number of connections " +
            "that were used (the high water mark).",
            System.currentTimeMillis(), System.currentTimeMillis());

    private final RangeStatisticImpl connRequestWaitTime = new RangeStatisticImpl(
            0, 0, 0,
            "ConnRequestWaitTime", StatisticImpl.UNIT_MILLISECOND,
            "The longest and shortest wait times of connection requests. The " +
            "current value indicates the wait time of the last request that was " +
            "serviced by the pool.",
            System.currentTimeMillis(), System.currentTimeMillis());

    private final CountStatisticImpl numConnDestroyed = new CountStatisticImpl(
            "NumConnDestroyed", StatisticImpl.UNIT_COUNT,
            "Number of physical connections that were destroyed since the last reset.");

    private final CountStatisticImpl numConnAcquired = new CountStatisticImpl(
            "NumConnAcquired", StatisticImpl.UNIT_COUNT, "Number of logical " +
            "connections acquired from the pool.");

    private final CountStatisticImpl numConnReleased = new CountStatisticImpl(
            "NumConnReleased", StatisticImpl.UNIT_COUNT, "Number of logical " +
            "connections released to the pool.");
    private final CountStatisticImpl numConnCreated = new CountStatisticImpl(
            "NumConnCreated", StatisticImpl.UNIT_COUNT,
            "The number of physical connections that were created since the last reset.");
    private final CountStatisticImpl numPotentialConnLeak = new CountStatisticImpl(
            "NumPotentialConnLeak", StatisticImpl.UNIT_COUNT,
            "Number of potential connection leaks");
    private final CountStatisticImpl numConnSuccessfullyMatched = new CountStatisticImpl(
            "NumConnSuccessfullyMatched", StatisticImpl.UNIT_COUNT,
            "Number of connections succesfully matched");
    private final CountStatisticImpl numConnNotSuccessfullyMatched = new CountStatisticImpl(
            "NumConnNotSuccessfullyMatched", StatisticImpl.UNIT_COUNT,
            "Number of connections rejected during matching");
    private final CountStatisticImpl totalConnRequestWaitTime = new CountStatisticImpl(
            "TotalConnRequestWaitTime", StatisticImpl.UNIT_MILLISECOND,
            "Total wait time per successful connection request");
    private final CountStatisticImpl averageConnWaitTime = new CountStatisticImpl(
            "AverageConnWaitTime", StatisticImpl.UNIT_MILLISECOND,
            "Average wait-time-duration per successful connection request");
    private final CountStatisticImpl waitQueueLength = new CountStatisticImpl(
            "WaitQueueLength", StatisticImpl.UNIT_COUNT,
            "Number of connection requests in the queue waiting to be serviced.");
    private static final String JDBC_PROBE_LISTENER = "glassfish:jdbc:connection-pool:";


    public JdbcConnPoolStatsProvider(PoolInfo poolInfo, Logger logger) {
        this.poolInfo = poolInfo;
        this.logger = logger;
    }

    /**
     * Whenever connection leak happens, increment numPotentialConnLeak
     * @param pool JdbcConnectionPool that got a connLeakEvent
     */
    @ProbeListener(JDBC_PROBE_LISTENER + "potentialConnLeakEvent")
    public void potentialConnLeakEvent(@ProbeParam("poolName") String poolName,
                                       @ProbeParam("appName") String appName,
                                       @ProbeParam("moduleName") String moduleName
                                       ) {
        // handle the conn leak probe event
        PoolInfo poolInfo = new PoolInfo(SimpleJndiName.of(poolName), appName, moduleName);
        if(this.poolInfo.equals(poolInfo)) {
            if(logger.isLoggable(Level.FINEST)) {
                logger.finest("Connection Leak event received - poolName = " +
                             poolName);
            }
            //TODO V3: Checking if this is a valid event
            //Increment counter
            numPotentialConnLeak.increment();
        }
    }

    /**
     * Whenever connection timed-out event occurs, increment numConnTimedOut
     * @param pool JdbcConnectionPool that got a connTimedOutEvent
     */
    @ProbeListener(JDBC_PROBE_LISTENER + "connectionTimedOutEvent")
    public void connectionTimedOutEvent(@ProbeParam("poolName") String poolName,
                                        @ProbeParam("appName") String appName,
                                        @ProbeParam("moduleName") String moduleName
                                        ) {
        // handle the conn timed out probe event
        PoolInfo poolInfo = new PoolInfo(SimpleJndiName.of(poolName), appName, moduleName);
        if(this.poolInfo.equals(poolInfo)) {
            if(logger.isLoggable(Level.FINEST)) {
                logger.finest("Connection Timed-out event received - poolName = " +
                             poolName);
            }
            //Increment counter
            numConnTimedOut.increment();
        }
    }

    /**
     * Decrement numconnfree event
     * @param poolName
     * @param steadyPoolSize
     */
    @ProbeListener(JDBC_PROBE_LISTENER + "decrementNumConnFreeEvent")
    public void decrementNumConnFreeEvent(
            @ProbeParam("poolName") String poolName,
            @ProbeParam("appName") String appName,
            @ProbeParam("moduleName") String moduleName
            ) {
        // handle the num conn free decrement event
        PoolInfo poolInfo = new PoolInfo(SimpleJndiName.of(poolName), appName, moduleName);
        if(this.poolInfo.equals(poolInfo)) {
            if(logger.isLoggable(Level.FINEST)) {
                logger.finest("Decrement Num Connections Free event received - poolName = " +
                             poolName);
            }
            //Decrement counter
            synchronized(numConnFree) {
                numConnFree.setCurrent(numConnFree.getCurrent() - 1);
            }
        }
    }

    /**
     * Increment numconnfree event
     * @param poolName
     * @param steadyPoolSize
     */
    @ProbeListener(JDBC_PROBE_LISTENER + "incrementNumConnFreeEvent")
    public void incrementNumConnFreeEvent(
            @ProbeParam("poolName") String poolName,
            @ProbeParam("appName") String appName,
            @ProbeParam("moduleName") String moduleName,
            @ProbeParam("beingDestroyed") boolean beingDestroyed,
            @ProbeParam("steadyPoolSize") int steadyPoolSize) {
        // handle the num conn free increment event
        PoolInfo poolInfo = new PoolInfo(SimpleJndiName.of(poolName), appName, moduleName);
        if (this.poolInfo.equals(poolInfo)) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("Increment Num Connections Free event received - poolName = " + poolName);
            }
            if (beingDestroyed) {
                // if pruned by resizer thread
                synchronized (numConnFree) {
                    synchronized (numConnUsed) {
                        if (numConnFree.getCurrent() + numConnUsed.getCurrent() < steadyPoolSize) {
                            numConnFree.setCurrent(numConnFree.getCurrent() + 1);
                        }
                    }
                }
            } else {
                synchronized (numConnFree) {
                    numConnFree.setCurrent(numConnFree.getCurrent() + 1);
                }
            }
        }
    }

    /**
     * Decrement numConnUsed event
     * @param poolName
     * @param beingDestroyed if the connection is destroyed due to error
     * @param steadyPoolSize
     */
    @ProbeListener(JDBC_PROBE_LISTENER + "decrementConnectionUsedEvent")
    public void decrementConnectionUsedEvent(@ProbeParam("poolName") String poolName, @ProbeParam("appName") String appName,
            @ProbeParam("moduleName") String moduleName) {

        // handle the num conn used decrement event
        PoolInfo poolInfo = new PoolInfo(SimpleJndiName.of(poolName), appName, moduleName);
        if (this.poolInfo.equals(poolInfo)) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("Decrement Num Connections Used event received - poolName = " + poolName);
            }
            // Decrement numConnUsed counter
            synchronized (numConnUsed) {
                numConnUsed.setCurrent(numConnUsed.getCurrent() - 1);
            }
        }
    }

    /**
     * Connections freed event
     * @param poolName
     * @param count number of connections freed to the pool
     */
    @ProbeListener(JDBC_PROBE_LISTENER + "connectionsFreedEvent")
    public void connectionsFreedEvent(
            @ProbeParam("poolName") String poolName,
            @ProbeParam("appName") String appName,
            @ProbeParam("moduleName") String moduleName,
            @ProbeParam("count") int count) {
        // handle the connections freed event
        PoolInfo poolInfo = new PoolInfo(SimpleJndiName.of(poolName), appName, moduleName);
        if (this.poolInfo.equals(poolInfo)) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("Connections Freed event received - poolName = " + poolName);
                logger.finest("numConnUsed =" + numConnUsed.getCurrent() + " numConnFree=" + numConnFree.getCurrent()
                        + " Number of connections freed =" + count);
            }
            // set numConnFree to the count value
            synchronized (numConnFree) {
                numConnFree.setCurrent(count);
            }
        }
    }

    /**
     * Connection used event
     * @param poolName
     */
    @ProbeListener(JDBC_PROBE_LISTENER + "connectionUsedEvent")
    public void connectionUsedEvent(@ProbeParam("poolName") String poolName, @ProbeParam("appName") String appName,
            @ProbeParam("moduleName") String moduleName) {

        // handle the connection used event
        PoolInfo poolInfo = new PoolInfo(SimpleJndiName.of(poolName), appName, moduleName);
        if (this.poolInfo.equals(poolInfo)) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("Connection Used event received - poolName = " + poolName);
            }
            // increment numConnUsed
            synchronized (numConnUsed) {
                numConnUsed.setCurrent(numConnUsed.getCurrent() + 1);
            }
        }
    }

    /**
     * Whenever connection leak happens, increment numConnFailedValidation
     * @param pool JdbcConnectionPool that got a failed validation event
     */
    @ProbeListener(JDBC_PROBE_LISTENER + "connectionValidationFailedEvent")
    public void connectionValidationFailedEvent(
            @ProbeParam("poolName") String poolName,
            @ProbeParam("appName") String appName,
            @ProbeParam("moduleName") String moduleName,
            @ProbeParam("increment") int increment) {

        PoolInfo poolInfo = new PoolInfo(SimpleJndiName.of(poolName), appName, moduleName);
        if (this.poolInfo.equals(poolInfo)) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("Connection Validation Failed event received - " + "poolName = " + poolName);
            }

            // TODO V3 : add support in CounterImpl for addAndGet(increment)
            numConnFailedValidation.increment(increment);
        }

    }

    /**
     * Event that a connection request is served in timeTakenInMillis.
     *
     * @param poolName
     * @param timeTakenInMillis
     */
    @ProbeListener(JDBC_PROBE_LISTENER + "connectionRequestServedEvent")
    public void connectionRequestServedEvent(
            @ProbeParam("poolName") String poolName,
            @ProbeParam("appName") String appName,
            @ProbeParam("moduleName") String moduleName,
            @ProbeParam("timeTakenInMillis") long timeTakenInMillis) {

        PoolInfo poolInfo = new PoolInfo(SimpleJndiName.of(poolName), appName, moduleName);
        if(this.poolInfo.equals(poolInfo)) {
            if(logger.isLoggable(Level.FINEST)) {
                logger.finest("Connection request served event received - " +
                    "poolName = " + poolName);
            }
            connRequestWaitTime.setCurrent(timeTakenInMillis);
            totalConnRequestWaitTime.increment(timeTakenInMillis);
        }
    }

    /**
     * When connection destroyed event is got increment numConnDestroyed.
     */
    @ProbeListener(JDBC_PROBE_LISTENER + "connectionDestroyedEvent")
    public void connectionDestroyedEvent(
            @ProbeParam("poolName") String poolName,
            @ProbeParam("appName") String appName,
            @ProbeParam("moduleName") String moduleName
            ) {

        PoolInfo poolInfo = new PoolInfo(SimpleJndiName.of(poolName), appName, moduleName);
        if(this.poolInfo.equals(poolInfo)) {
            if(logger.isLoggable(Level.FINEST)) {
                logger.finest("Connection destroyed event received - " +
                    "poolName = " + poolName);
            }
            numConnDestroyed.increment();
        }
    }

    /**
     * When a connection is acquired increment counter
     */
    @ProbeListener(JDBC_PROBE_LISTENER + "connectionAcquiredEvent")
    public void connectionAcquiredEvent(
            @ProbeParam("poolName") String poolName,
            @ProbeParam("appName") String appName,
            @ProbeParam("moduleName") String moduleName
            ) {

        PoolInfo poolInfo = new PoolInfo(SimpleJndiName.of(poolName), appName, moduleName);
        if(this.poolInfo.equals(poolInfo)) {
            if(logger.isLoggable(Level.FINEST)) {
                logger.finest("Connection acquired event received - " +
                    "poolName = " + poolName);
            }
            numConnAcquired.increment();
        }
    }

    /**
     * When a connection is released increment counter
     */
    @ProbeListener(JDBC_PROBE_LISTENER + "connectionReleasedEvent")
    public void connectionReleasedEvent(
            @ProbeParam("poolName") String poolName,
            @ProbeParam("appName") String appName,
            @ProbeParam("moduleName") String moduleName
            ) {

        PoolInfo poolInfo = new PoolInfo(SimpleJndiName.of(poolName), appName, moduleName);
        if(this.poolInfo.equals(poolInfo)) {
            if(logger.isLoggable(Level.FINEST)) {
                logger.finest("Connection released event received - " +
                    "poolName = " + poolName);
            }
            numConnReleased.increment();
        }
    }

    /**
     * When a connection is created increment counter
     */
    @ProbeListener(JDBC_PROBE_LISTENER + "connectionCreatedEvent")
    public void connectionCreatedEvent(
            @ProbeParam("poolName") String poolName,
            @ProbeParam("appName") String appName,
            @ProbeParam("moduleName") String moduleName
            ) {

        PoolInfo poolInfo = new PoolInfo(SimpleJndiName.of(poolName), appName, moduleName);
        if(this.poolInfo.equals(poolInfo)) {
            if(logger.isLoggable(Level.FINEST)) {
                logger.finest("Connection created event received - " +
                    "poolName = " + poolName);
            }
            numConnCreated.increment();
        }
    }

    /**
     * Reset pool statistics.
     * When annotated with @Reset, this method is invoked whenever monitoring
     * is turned to HIGH from OFF, thereby setting the statistics to
     * appropriate values.
     */
    @Reset
    public void reset() {
        if(logger.isLoggable(Level.FINEST)) {
            logger.finest("Reset event received - poolInfo = " + poolInfo);
        }
        PoolStatus status = ConnectorRuntime.getRuntime().getPoolManager().getPoolStatus(poolInfo);
        numConnUsed.setCurrent(status.getNumConnUsed());
        numConnFree.setCurrent(status.getNumConnFree());
        numConnCreated.reset();
        numConnDestroyed.reset();
        numConnFailedValidation.reset();
        numConnTimedOut.reset();
        numConnAcquired.reset();
        numConnReleased.reset();
        connRequestWaitTime.reset();
        numConnSuccessfullyMatched.reset();
        numConnNotSuccessfullyMatched.reset();
        numPotentialConnLeak.reset();
        averageConnWaitTime.reset();
        totalConnRequestWaitTime.reset();
        waitQueueLength.reset();
    }

    /**
     * When connection under test matches the current request ,
     * increment numConnSuccessfullyMatched.
     */
    @ProbeListener(JDBC_PROBE_LISTENER + "connectionMatchedEvent")
    public void connectionMatchedEvent(
            @ProbeParam("poolName") String poolName,
            @ProbeParam("appName") String appName,
            @ProbeParam("moduleName") String moduleName
            ) {

        PoolInfo poolInfo = new PoolInfo(SimpleJndiName.of(poolName), appName, moduleName);
        if(this.poolInfo.equals(poolInfo)) {
            if(logger.isLoggable(Level.FINEST)) {
                logger.finest("Connection matched event received - " +
                    "poolName = " + poolName);
            }
            numConnSuccessfullyMatched.increment();
        }
    }

    /**
     * When a connection under test does not match the current request,
     * increment numConnNotSuccessfullyMatched.
     */
    @ProbeListener(JDBC_PROBE_LISTENER + "connectionNotMatchedEvent")
    public void connectionNotMatchedEvent(
            @ProbeParam("poolName") String poolName,
            @ProbeParam("appName") String appName,
            @ProbeParam("moduleName") String moduleName
            ) {

        PoolInfo poolInfo = new PoolInfo(SimpleJndiName.of(poolName), appName, moduleName);
        if(this.poolInfo.equals(poolInfo)) {
            if(logger.isLoggable(Level.FINEST)) {
                logger.finest("Connection not matched event received - " +
                    "poolName = " + poolName);
            }
            numConnNotSuccessfullyMatched.increment();
        }
    }

    /**
     * When an object is added to wait queue, increment the waitQueueLength.
     */
    @ProbeListener(JDBC_PROBE_LISTENER + "connectionRequestQueuedEvent")
    public void connectionRequestQueuedEvent(
            @ProbeParam("poolName") String poolName,
            @ProbeParam("appName") String appName,
            @ProbeParam("moduleName") String moduleName
            ) {

        PoolInfo poolInfo = new PoolInfo(SimpleJndiName.of(poolName), appName, moduleName);
        if(this.poolInfo.equals(poolInfo)) {
            if(logger.isLoggable(Level.FINEST)) {
                logger.finest("Wait Queue length modified event received - " +
                    "poolName = " + poolName);
            }
            waitQueueLength.increment();
        }
    }

    /**
     * When an object is removed from the wait queue, decrement the waitQueueLength.
     */
    @ProbeListener(JDBC_PROBE_LISTENER + "connectionRequestDequeuedEvent")
    public void connectionRequestDequeuedEvent(
            @ProbeParam("poolName") String poolName,
            @ProbeParam("appName") String appName,
            @ProbeParam("moduleName") String moduleName
            ) {

        PoolInfo poolInfo = new PoolInfo(SimpleJndiName.of(poolName), appName, moduleName);
        if(this.poolInfo.equals(poolInfo)) {
            if(logger.isLoggable(Level.FINEST)) {
                logger.finest("Wait Queue length modified event received - " +
                    "poolName = " + poolName);
            }
            waitQueueLength.decrement();
        }
    }

    public PoolInfo getPoolInfo() {
        return poolInfo;
    }

    public void setPoolRegistry(PoolLifeCycleListenerRegistry poolRegistry) {
        this.poolRegistry = poolRegistry;
    }

    public PoolLifeCycleListenerRegistry getPoolRegistry() {
        return poolRegistry;
    }

    @ManagedAttribute(id="numpotentialconnleak")
    public CountStatistic getNumPotentialConnLeakCount() {
        return numPotentialConnLeak;
    }

    @ManagedAttribute(id="numconnfailedvalidation")
    public CountStatistic getNumConnFailedValidation() {
        return numConnFailedValidation;
    }

    @ManagedAttribute(id="numconntimedout")
    public CountStatistic getNumConnTimedOut() {
        return numConnTimedOut;
    }

    @ManagedAttribute(id="numconnused")
    public RangeStatistic getNumConnUsed() {
        return numConnUsed;
    }

    @ManagedAttribute(id="numconnfree")
    public RangeStatistic getNumConnFree() {
        return numConnFree;
    }

    @ManagedAttribute(id="connrequestwaittime")
    public RangeStatistic getConnRequestWaitTime() {
        return connRequestWaitTime;
    }

    @ManagedAttribute(id="numconndestroyed")
    public CountStatistic getNumConnDestroyed() {
        return numConnDestroyed;
    }

    @ManagedAttribute(id="numconnacquired")
    public CountStatistic getNumConnAcquired() {
        return numConnAcquired;
    }

    @ManagedAttribute(id="numconncreated")
    public CountStatistic getNumConnCreated() {
        return numConnCreated;
    }

    @ManagedAttribute(id="numconnreleased")
    public CountStatistic getNumConnReleased() {
        return numConnReleased;
    }

    @ManagedAttribute(id="numconnsuccessfullymatched")
    public CountStatistic getNumConnSuccessfullyMatched() {
        return numConnSuccessfullyMatched;
    }

    @ManagedAttribute(id="numconnnotsuccessfullymatched")
    public CountStatistic getNumConnNotSuccessfullyMatched() {
        return numConnNotSuccessfullyMatched;
    }

    @ManagedAttribute(id="averageconnwaittime")
    public CountStatistic getAverageConnWaitTime() {
       // Time taken by all connection requests divided by total number of
       // connections acquired in the sampling period.
       long count = numConnAcquired.getCount();
       long averageWaitTime = count == 0 ? 0 : totalConnRequestWaitTime.getCount() / count;

       averageConnWaitTime.setCount(averageWaitTime);
       return averageConnWaitTime;
    }

    @ManagedAttribute(id="waitqueuelength")
    public CountStatistic getWaitQueueLength() {
        return waitQueueLength;
    }
}

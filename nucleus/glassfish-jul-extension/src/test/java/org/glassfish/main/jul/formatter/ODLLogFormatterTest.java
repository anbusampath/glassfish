/*
 * Copyright (c) 2022 Eclipse Foundation and/or its affiliates. All rights reserved.
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

package org.glassfish.main.jul.formatter;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.glassfish.main.jul.env.LoggingSystemEnvironment;
import org.glassfish.main.jul.formatter.ExcludeFieldsSupport.SupplementalAttribute;
import org.glassfish.main.jul.record.GlassFishLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.glassfish.main.jul.formatter.LogFormatDetector.P_LEVEL_NAME;
import static org.glassfish.main.jul.formatter.LogFormatDetector.P_LEVEL_VALUE;
import static org.glassfish.main.jul.formatter.LogFormatDetector.P_LOGGER_NAME;
import static org.glassfish.main.jul.formatter.LogFormatDetector.P_MESSAGE_KEY;
import static org.glassfish.main.jul.formatter.LogFormatDetector.P_PRODUCT_ID;
import static org.glassfish.main.jul.formatter.LogFormatDetector.P_TIME;
import static org.glassfish.main.jul.formatter.LogFormatDetector.P_TIMESTAMP;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author David Matejcek
 */
public class ODLLogFormatterTest {
    private static final Pattern PATTERN_MULTILINE = Pattern.compile(
        "\\[" + P_TIMESTAMP + "\\]"
        + " \\[" + P_PRODUCT_ID + "\\]"
        + " \\[" + P_LEVEL_NAME + "\\]"
        + " \\[" + P_MESSAGE_KEY + "\\]"
        + " \\[" + P_LOGGER_NAME + "\\]"
        + " \\[tid: _ThreadID=[0-9] _ThreadName=main\\]"
        + " \\[levelValue: " + P_LEVEL_VALUE + "\\]"
        + " \\[\\["
    );
    private static final Pattern PATTERN_SINGLELINE = Pattern.compile(
        "\\[" + P_TIMESTAMP + "\\]"
        + " \\[" + P_PRODUCT_ID + "\\]"
        + " \\[" + P_LEVEL_NAME + "\\]"
        + " \\[" + P_MESSAGE_KEY + "\\]"
        + " \\[" + P_LOGGER_NAME + "\\]"
        + " \\[tid: _ThreadID=[0-9] _ThreadName=main\\]"
        + " \\[levelValue: " + P_LEVEL_VALUE + "\\]"
        + " .+"
    );

    private String backupProductId;

    @BeforeEach
    public void initProductId() {
        this.backupProductId = LoggingSystemEnvironment.getProductId();
    }


    @AfterEach
    public void resetProductId() {
        LoggingSystemEnvironment.setProductId(backupProductId);
    }


    @Test
    public void nullRecord() {
        assertEquals("", new ODLLogFormatter().format(null));
    }


    @Test
    public void nullMessage() {
        final LogRecord record = new LogRecord(Level.INFO, null);
        final String log = new ODLLogFormatter().format(record);
        assertEquals("", log);
    }


    @Test
    public void simpleLogRecordMultiLineEnabled() {
        final String message = "Ok, this works!";
        final LogRecord record = new LogRecord(Level.INFO, message);
        final ODLLogFormatter formatter = new ODLLogFormatter();
        final String log = formatter.format(record);
        assertNotNull(log, "log");
        final String[] lines = log.split("\r?\n");
        assertAll(
            () -> assertThat(lines, arrayWithSize(2)),
            () -> assertThat(lines[0], matchesPattern(PATTERN_MULTILINE)),
            () -> assertThat(lines[0],
                stringContainsInOrder("] [] [INFO] [] [] [tid", "main] ", "] [levelValue: 800] [[")),
            () -> assertThat(lines[1], stringContainsInOrder(message, "]]"))
        );
    }

    @Test
    public void simpleLogRecordMultiLineMessage() {
        final String message = "Ok!\nThis works!";
        final LogRecord record = new LogRecord(Level.INFO, message);
        final ODLLogFormatter formatter = new ODLLogFormatter();
        formatter.setMultiline(false);
        final String log = formatter.format(record);
        assertNotNull(log, "log");
        final String[] lines = log.split("\r?\n");
        assertAll(
            () -> assertThat(lines, arrayWithSize(3)),
            () -> assertThat(lines[0], matchesPattern(PATTERN_MULTILINE)),
            () -> assertThat(lines[0],
                stringContainsInOrder("] [] [INFO] [] [] [tid", "main] ", "] [levelValue: 800] [[")),
            () -> assertThat(lines[1], equalTo("  Ok!")),
            () -> assertThat(lines[2], equalTo("This works!]]"))
        );
    }


    @Test
    public void fullLogRecordSingleLine() {
        LoggingSystemEnvironment.setProductId("GLASSFISH TEST");
        final String message = "Ok, this works!";
        final LogRecord record = new LogRecord(Level.INFO, message);
        record.setLoggerName("the.test.logger");
        record.setSourceClassName("org.acme.FakeClass");
        record.setSourceMethodName("fakeMethod");
        final ODLLogFormatter formatter = new ODLLogFormatter();
        formatter.setMultiline(false);
        formatter.setPrintSequenceNumber(true);
        formatter.setPrintSource(true);

        final String log = formatter.format(record);
        assertNotNull(log, "log");
        final String[] lines = log.split("\r?\n");
        assertAll(
            () -> assertThat(lines, arrayWithSize(1)),
            () -> assertThat(lines[0], matchesPattern(PATTERN_SINGLELINE)),
            () -> assertThat(lines[0], stringContainsInOrder("] [GLASSFISH TEST] [INFO] [] [the.test.logger] [tid",
                "main] ", "] [levelValue: 800] [RECORDNUMBER: ", "]"
                    + " [CLASSNAME: org.acme.FakeClass] [METHODNAME: fakeMethod] " + message))
        );
    }


    @Test
    public void exception() {
        final GlassFishLogRecord record = new GlassFishLogRecord(Level.SEVERE, "Failure!", false);
        record.setThrown(new RuntimeException("Ooops!"));
        final ODLLogFormatter formatter = new ODLLogFormatter();
        final String log = formatter.format(record);
        assertNotNull(log, "log");
        final String[] lines = log.split("\r?\n");
        assertAll(
            () -> assertThat(lines, arrayWithSize(greaterThan(20))),
            () -> assertThat(lines[0],
                stringContainsInOrder("] [] [SEVERE] [] [] [tid", "main] [levelValue: 1000] [[")),
            () -> assertThat(lines[1], equalTo("  Failure!")),
            () -> assertThat(lines[2], equalTo("java.lang.RuntimeException: Ooops!"))
        );
    }


    @Test
    public void exclusionsAndCustomTimestampFormat() {
        final LogRecord record = new LogRecord(Level.INFO, "This is a message.");
        final ODLLogFormatter formatter = new ODLLogFormatter();
        formatter.setTimestampFormatter("HH:mm:ss.SSS");
        formatter.setMultiline(false);
        formatter.setExcludeFields(Arrays.stream(SupplementalAttribute.values()).map(SupplementalAttribute::getId)
            .collect(Collectors.joining(",")));
        final String log = formatter.format(record);
        assertNotNull(log, "log");

        final Pattern pattern = Pattern.compile(
            "\\[" + P_TIME + "\\]"
            + " \\[\\]"
            + " \\[INFO\\]"
            + " \\[\\]"
            + " \\[\\]"
            + " This is a message\\.+\\r?\\n\\r?\\n"
        );
        assertThat(log, matchesPattern(pattern));
    }
}

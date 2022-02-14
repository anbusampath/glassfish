/*
 * Copyright (c) 2022 Contributors to Eclipse Foundation.
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
package org.glassfish.microprofile.config.tck.client;

import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.asset.ArchiveAsset;
import org.jboss.shrinkwrap.api.asset.UrlAsset;

import java.net.URL;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static java.lang.String.format;

/**
 * This extension replaces beans.xml files with ones declaring the 'all' bean discovery type.
 * This is because version 3.0.1 of the TCK still deploys an empty beans.xml due to a faulty assumption that
 * CDI < 4 is still defaulting to the 'all' type.
 */
public class BeansXmlTransformer implements ApplicationArchiveProcessor {

    private static final Logger LOGGER = Logger.getLogger(BeansXmlTransformer.class.getName());

    private static final String BEANS_XML_PATH = format("/META-INF%sbeans.xml", ArchivePath.SEPARATOR);
    private static final String LIB_DIR_PATH = format("WEB-INF%slib", ArchivePath.SEPARATOR);

    private final URL beansXmlResource;

    public BeansXmlTransformer() {
        this.beansXmlResource = getClass().getClassLoader().getResource("beans.xml");
        if (beansXmlResource == null) {
            throw new IllegalStateException("Unable to find beans.xml resource in test dir");
        }
    }

    @Override
    public void process(Archive<?> archive, TestClass testClass) {
        replaceBeansXml(archive);
    }

    private void replaceBeansXml(Archive<?> archive) {
        final var beansXml = archive.get(BEANS_XML_PATH);
        if (beansXml != null) {
            LOGGER.info(() -> format("Replacing beans.xml in archive [%s]", archive.getName()));
            archive.add(new UrlAsset(beansXmlResource), BEANS_XML_PATH);
        }
        processLibraries(archive, this::replaceBeansXml);
    }

    private static void processLibraries(Archive<?> archive, Consumer<Archive<?>> consumer) {
        final var libDir = archive.get(LIB_DIR_PATH);

        if (libDir != null) {
            for (var node : libDir.getChildren()) {
                final var asset = node.getAsset();
                if (asset instanceof ArchiveAsset) {
                    LOGGER.info(() -> format("Processing subarchive [%s]", node.getPath()));
                    consumer.accept(((ArchiveAsset) asset).getArchive());
                }
            }
        }
    }
}

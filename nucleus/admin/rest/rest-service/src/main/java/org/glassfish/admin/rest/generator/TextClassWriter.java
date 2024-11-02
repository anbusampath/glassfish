/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
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

package org.glassfish.admin.rest.generator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.logging.Level;

import org.glassfish.admin.rest.RestLogging;
import org.glassfish.admin.rest.utils.ResourceUtil;
import org.glassfish.hk2.api.ServiceLocator;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author Mitesh Meswani
 */
public class TextClassWriter implements ClassWriter {

    Writer writer;
    ServiceLocator habitat;

    /**
     * @param className Name of class to be generated
     * @param generationDir Absolute location where it needs to be generated
     * @param baseClassName
     * @param resourcePath
     */
    public TextClassWriter(ServiceLocator habitat, File generationDir, String className, String baseClassName, String resourcePath)
            throws IOException {
        this.habitat = habitat;
        File file = new File(generationDir, className + ".java");
        boolean success = file.createNewFile();
        if (!success) {
            RestLogging.restLogger.log(Level.FINE, "Error creating file: {0} in {1}",
                    new String[] { className + ".java", generationDir.getAbsolutePath() });
        }
        FileWriter fstream = new FileWriter(file, UTF_8);
        writer = new BufferedWriter(fstream);

        writeCopyRightHeader();
        writePackageHeader();
        writeImportStatements();

        if (resourcePath != null) {
            writer.write("@Path(\"/" + resourcePath + "/\")\n");
        }

        writer.write("public class " + className + " extends " + baseClassName + "  {\n\n");

    }

    private void writePackageHeader() throws IOException {
        writer.write("package org.glassfish.admin.rest.resources.generated;\n");
    }

    private void writeCopyRightHeader() throws IOException {
        writer.write("/*\n");
        writer.write(" * Copyright (c) 2024 Contributors to the Eclipse Foundation.\n");
        writer.write(" * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.\n");
        writer.write(" *\n");
        writer.write(" * This program and the accompanying materials are made available under the\n");
        writer.write(" * terms of the Eclipse Public License v. 2.0, which is available at\n");
        writer.write(" * http://www.eclipse.org/legal/epl-2.0.\n");
        writer.write(" *\n");
        writer.write(" * This Source Code may also be made available under the following Secondary\n");
        writer.write(" * Licenses when the conditions for such availability set forth in the\n");
        writer.write(" * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,\n");
        writer.write(" * version 2 with the GNU Classpath Exception, which is available at\n");
        writer.write(" * https://www.gnu.org/software/classpath/license.html.\n");
        writer.write(" *\n");
        writer.write(" * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0\n");
        writer.write(" */\n");
        writer.write("\n");
    }

    private void writeImportStatements() throws IOException {
        writer.write("import jakarta.ws.rs.Path;\n");
        writer.write("import jakarta.ws.rs.PathParam;\n");
        writer.write("import org.glassfish.admin.rest.resources.*;\n");
        writer.write("import org.glassfish.admin.rest.resources.custom.*;\n");
    }

    @Override
    public void createCommandResourceConstructor(String commandResourceClassName, String commandName, String httpMethod,
            boolean linkedToParent, CommandResourceMetaData.ParameterMetaData[] commandParams, String commandDisplayName,
            String commandAction) {
        try {
            writer.write("   public " + commandResourceClassName + "() {\n");
            writer.write("       super(\n");
            writer.write("          \"" + commandResourceClassName + "\",\n");
            writer.write("          \"" + commandName + "\",\n");
            writer.write("          \"" + httpMethod + "\",\n");
            if (!httpMethod.equals("GET")) {
                writer.write("          \"" + commandAction + "\",\n");
                writer.write("          \"" + commandDisplayName + "\",\n");
            }

            writer.write("          " + linkedToParent + ");\n");
            writer.write("    }\n");

            if (commandParams != null) {
                writer.write("@Override\n");
                writer.write("protected java.util.HashMap<String, String> getCommandParams() {\n");
                writer.write("\tjava.util.HashMap<String, String> hm = new java.util.HashMap<String, String>();\n");
                for (CommandResourceMetaData.ParameterMetaData commandParam : commandParams) {
                    writer.write("\thm.put(\"" + commandParam.name + "\",\"" + commandParam.value + "\");\n");
                }

                writer.write("\treturn hm;\n");
                writer.write("}\n");
            }

        } catch (IOException e) {
            throw new GeneratorException(e);
        }
    }

    @Override
    public void createGetCommandResource(String commandResourceClassName, String resourcePath) {
        //define method with @Path in resource- resourceName
        try {
            writer.write("@Path(\"" + resourcePath + "/\")\n");
            writer.write("public " + commandResourceClassName + " get" + commandResourceClassName + "() {\n");
            writer.write(commandResourceClassName + " resource = injector.inject(" + commandResourceClassName + ".class);\n");
            writer.write("return resource;\n");
            writer.write("}\n\n");
        } catch (IOException e) {
            throw new GeneratorException(e);
        }
    }

    @Override
    public void createCustomResourceMapping(String resourceClassName, String mappingPath) {
        try {
            writer.write("\n");
            writer.write("\t@Path(\"" + mappingPath + "/\")\n");
            writer.write("\tpublic " + resourceClassName + " get" + resourceClassName + "() {\n");
            writer.write("\t\t" + resourceClassName + " resource = injector.inject(" + resourceClassName + ".class);\n");
            writer.write("\t\tresource.setEntity(getEntity());\n");
            writer.write("\t\treturn resource;\n");
            writer.write("\t}\n\n");
        } catch (IOException e) {
            throw new GeneratorException(e);
        }
    }

    @Override
    public void createGetCommandResourcePaths(List<CommandResourceMetaData> commandMetaData) {
        assert commandMetaData.size() > 0 : "It is assumed that this method is called only if commandMetaData.size() > 0";

        try {
            writer.write("@Override\n");
            writer.write("public String[][] getCommandResourcesPaths() {\n");

            StringBuilder commandResourcesPaths = new StringBuilder();
            for (CommandResourceMetaData metaData : commandMetaData) {
                if (ResourceUtil.commandIsPresent(habitat, metaData.command)) {
                    if (commandResourcesPaths.length() > 0) {
                        commandResourcesPaths = commandResourcesPaths.append(", ");
                    }

                    commandResourcesPaths = commandResourcesPaths.append("{").append('"').append(metaData.resourcePath).append("\", ")
                            .append('"').append(metaData.httpMethod).append("\", ").append('"').append(metaData.command).append("\"} ");
                }
            }

            writer.write("return new String[][] {" + commandResourcesPaths + "};\n");
            writer.write("}\n\n");
        } catch (IOException e) {
            throw new GeneratorException(e);
        }
    }

    @Override
    public void createGetDeleteCommand(String commandName) {
        try {
            writer.write("@Override\n");
            writer.write("public String getDeleteCommand() {\n");
            writer.write("\treturn \"" + commandName + "\";\n");
            writer.write("}\n");
        } catch (IOException e) {
            throw new GeneratorException(e);
        }
    }

    @Override
    public void createGetPostCommand(String commandName) {
        //TODO this method and createGetDeleteCommand method may be merged to single method createGetXXXCommand(String commandName, String httpMethod)
        try {
            writer.write("@Override\n");
            writer.write("public String getPostCommand() {\n");
            writer.write("\treturn \"" + commandName + "\";\n");
            writer.write("}\n");
        } catch (IOException e) {
            throw new GeneratorException(e);
        }
    }

    @Override
    public void createGetChildResource(String path, String childResourceClassName) {
        try {
            writer.write("\t@Path(\"" + path + "/\")\n");
            writer.write("\tpublic " + childResourceClassName + " get" + childResourceClassName + "() {\n");

            writer.write("\t\t" + childResourceClassName + " resource = injector.inject(" + childResourceClassName + ".class);\n");
            writer.write("\t\tresource.setParentAndTagName(getEntity() , \"" + path + "\");\n");
            writer.write("\t\treturn resource;\n");
            writer.write("\t}\n");
        } catch (IOException e) {
            throw new GeneratorException(e);
        }
    }

    @Override
    public void createGetChildResourceForListResources(String keyAttributeName, String childResourceClassName) {
        try {
            writer.write("\n");
            writer.write("\t@Path(\"{" + keyAttributeName + "}/\")\n");
            writer.write("\tpublic " + childResourceClassName + " get" + childResourceClassName + "(@PathParam(\"" + keyAttributeName
                    + "\") String id) {\n");
            writer.write("\t\t" + childResourceClassName + " resource = injector.inject(" + childResourceClassName + ".class);\n");
            writer.write("\t\tresource.setBeanByKey(entity, id, tagName);\n");
            writer.write("\t\treturn resource;\n");
            writer.write("\t}\n\n");
        } catch (IOException e) {
            throw new GeneratorException(e);
        }
    }

    @Override
    public void createGetPostCommandForCollectionLeafResource(String postCommandName) {
        try {
            writer.write("@Override\n");
            writer.write("protected String getPostCommand(){\n");
            writer.write("return \"" + postCommandName + "\";\n");
            writer.write("}\n");
        } catch (IOException e) {
            throw new GeneratorException(e);
        }
    }

    @Override
    public void createGetDeleteCommandForCollectionLeafResource(String deleteCommandName) {
        try {
            writer.write("@Override\n");
            writer.write("protected String getDeleteCommand(){\n");
            writer.write("return \"" + deleteCommandName + "\";\n");
            writer.write("}\n");
        } catch (IOException e) {
            throw new GeneratorException(e);
        }
    }

    @Override
    public void createGetDisplayNameForCollectionLeafResource(String displayName) {
        try {
            writer.write("@Override\n");
            writer.write("protected String getName(){\n");
            writer.write("return \"" + displayName + "\";\n");
            writer.write("}\n");
        } catch (IOException e) {
            throw new GeneratorException(e);
        }
    }

    @Override
    public void done() {
        try {
            writer.write("}\n");
            writer.close();
        } catch (IOException e) {
            throw new GeneratorException(e);
        }
    }
}

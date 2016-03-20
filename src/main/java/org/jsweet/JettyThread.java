package org.jsweet;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.metadata.Plugin;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.repository.ComponentDependency;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.URLResource;
import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.eclipse.jetty.webapp.WebAppContext;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/*

   Copyright 2016 Eric Ponthiaux -/- ponthiaux.eric@gmail.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

*/


/* @author EPOTH -/- ponthiaux.e@sfeir.com -/- ponthiaux.eric@gmail.com */


public class JettyThread extends TickThread {

    private Server server;

    private ProcessBuilder processBuilder;

    private Process currentJettyProcess ;

    public JettyThread(AbstractJSweetMojo mojo) {

        super(mojo);

    }

    private File prepareJettyInstall(List<Artifact> pluginDependencies) throws IOException {

        getLog().info("- validating jetty install ");

        File baseInstallDirectory = new File(getMojo().getMavenProject().getBuild().getDirectory() + "/.jetty/");

        if (!baseInstallDirectory.exists()) {

            baseInstallDirectory.mkdir();

            getLog().info("- create " + baseInstallDirectory.getCanonicalPath());

        }

        for (Artifact dependency : pluginDependencies) {

            StringBuilder stringBuilder = new StringBuilder();

            stringBuilder.append(getMojo().getMavenSession().getLocalRepository().getBasedir());

            stringBuilder.append("/");

            stringBuilder.append(getMojo().getMavenSession().getLocalRepository().pathOf(dependency));

            File targetFile = new File(stringBuilder.toString());

            File destinationFile = new File(baseInstallDirectory.getCanonicalPath() + "/" + targetFile.getName());

            if (!destinationFile.exists()) {

                getLog().info("- copy " + targetFile.getName() + " to " + destinationFile.getCanonicalPath());

                FileUtils.copyFile(
                        targetFile,
                        destinationFile
                );

            }

        }

        getLog().info("- Jetty install validated");

        return baseInstallDirectory;

    }

    @Override
    public void onRun() {

        getLog().info("Jetty thread started ... ");

        List<Artifact> pluginDependencies = getMojo().getPluginDescriptor().getArtifacts();

        List<Artifact> webAppDependencies = getMojo().getMavenProject().getCompileArtifacts();

        File baseJettyPath = null;

        try {

            baseJettyPath = prepareJettyInstall(pluginDependencies);

        } catch (IOException ioException) {

            getLog().info(ioException);

        }

        /* */

        processBuilder = new ProcessBuilder(

                getJavaExecutablePath(),

                "-jar"

                ,

                getJsweetMavenPluginJar(pluginDependencies)

                ,

                "-webappCp"

                ,

                buildDependenciesClassPath(webAppDependencies)

        );

        processBuilder.directory(baseJettyPath);

        /* add webapp dependencies */

        getLog().info("- building webapp dependencies");

        processBuilder.environment().put("WEBAPP_DEPENDENCIES", buildDependenciesClassPath(webAppDependencies));

        /* */

        getLog().info("- building webapp resource base");

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(getMojo().getMavenProject().getBasedir());

        stringBuilder.append("/");

        stringBuilder.append("src/main/webapp");

        processBuilder.environment().put("RESOURCE_BASE", stringBuilder.toString());

        /* */

        getLog().info("- building webapp server classes base");

        stringBuilder.delete(0, stringBuilder.length());

        stringBuilder.append(getMojo().getMavenProject().getBuild().getDirectory());

        stringBuilder.append("/");

        stringBuilder.append(getMojo().getMavenProject().getBuild().getFinalName());

        stringBuilder.append("/WEB-INF/classes");

        processBuilder.environment().put("SERVER_CLASSES", stringBuilder.toString());

        /* to resolve source maps */

        getLog().info("- building source maps resolver");

        stringBuilder.delete(0, stringBuilder.length());

        stringBuilder.append(getMojo().getMavenProject().getBasedir());

        stringBuilder.append("/");

        stringBuilder.append("src/main/java");

        getLog().info("Source maps resource base [" + stringBuilder.toString() + "]");

        processBuilder.environment().put("ADDITIONAL_RESOURCE_BASE", stringBuilder.toString());

        try {

            getLog().info("- calling jetty");

            currentJettyProcess = processBuilder.inheritIO().start();

        } catch (IOException ioException) {

            getLog().info(ioException);

        }

    }

    private String getJsweetMavenPluginJar(List<Artifact> artifacts) {

        for (Artifact dependency : artifacts) {

            if (dependency.getArtifactId().indexOf("jsweet-maven-plugin") != -1) {

                StringBuilder stringBuilder = new StringBuilder();

                stringBuilder.append(getMojo().getMavenSession().getLocalRepository().getBasedir());

                stringBuilder.append("/");

                stringBuilder.append(getMojo().getMavenSession().getLocalRepository().pathOf(dependency));

                return new File(stringBuilder.toString()).getName();

            }

        }

        return "*";

    }

    private String getJavaExecutablePath() {

        return System.getProperty("java.home") + "/bin/java";

    }

    private String buildDependenciesClassPath(List<Artifact> artifacts) {

        StringBuilder stringBuilder = new StringBuilder();

        for (Artifact dependency : artifacts) {

            stringBuilder.append(getMojo().getMavenSession().getLocalRepository().getBasedir());

            stringBuilder.append("/");

            stringBuilder.append(getMojo().getMavenSession().getLocalRepository().pathOf(dependency));

            stringBuilder.append(";");

        }

        return stringBuilder.toString();

    }

    @Override
    public void execute() {

        currentJettyProcess.destroy();

        try {

            compile();

        } catch (Exception exception) {

            return;

        }

        try {

            currentJettyProcess =  processBuilder.inheritIO().start();

        } catch (Exception exception) {

            return;

        }


    }

    private void compile() throws MojoExecutionException {

        executeMojo(

                plugin(
                        groupId("org.apache.maven.plugins"),
                        artifactId("maven-compiler-plugin"),
                        version("3.5.1")
                )

                ,

                goal("compile")

                ,

                configuration(

                        element(name("outputDirectory"),

                                getMojo().getMavenProject().getBuild().getDirectory()
                                        + "/"
                                        + getMojo().getMavenProject().getBuild().getFinalName()
                                        + "/WEB-INF/classes"

                        ),
                        element(name("source"), "8"),
                        element(name("target"), "8")

                )

                ,

                executionEnvironment(
                        getMojo().getMavenProject(),
                        getMojo().getMavenSession(),
                        getMojo().getPluginManager()
                )

        );

    }

}

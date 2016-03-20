package org.jsweet;

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

    public JettyThread(AbstractJSweetMojo mojo) {

        super(mojo);

    }

    @Override
    public void onRun() {

        /* */

        ArrayList<String> commands = new ArrayList<String>();

        commands.add(createCommand());

        /* */

        ProcessBuilder processBuilder = new ProcessBuilder(commands);

        /* */

        getLog().info("Jetty thread started ... ");

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(getMojo().getMavenProject().getBasedir());

        stringBuilder.append("/");

        stringBuilder.append("src/main/webapp");

        processBuilder.environment().put("RESOURCE_BASE", stringBuilder.toString());

        stringBuilder.delete(0, stringBuilder.length());

        /* */

        getLog().info("Server resource base [" + stringBuilder.toString() + "]");

        /* */

        stringBuilder.append(getMojo().getMavenProject().getBuild().getDirectory());

        stringBuilder.append("/");

        stringBuilder.append(getMojo().getMavenProject().getBuild().getFinalName());

        stringBuilder.append("/WEB-INF/classes");

        stringBuilder.append(";");

        /* */

        List<Artifact> dependencies = getMojo().getMavenProject().getCompileArtifacts();

        processBuilder.environment().put("SERVER_CLASSPATH", buildDependenciesClassPath(dependencies));

        /* to resolve source maps */

        stringBuilder.delete(0, stringBuilder.length());

        stringBuilder.append(getMojo().getMavenProject().getBasedir());

        stringBuilder.append("/");

        stringBuilder.append("src/main/java");

        getLog().info("Source maps resource base [" + stringBuilder.toString() + "]");

        processBuilder.environment().put("ADDITIONAL_RESOURCE_BASE", stringBuilder.toString());

        try {

            processBuilder.start();

        } catch (IOException ioException ) {

            getLog().info(ioException);

        }

    }

    private String createCommand() {

        List<Artifact> pluginDependencies = getMojo().getPluginDescriptor().getArtifacts();

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(System.getProperty("java.home"));

        stringBuilder.append("/bin/");

        stringBuilder.append("java ");

        stringBuilder.append("-cp \"");

        stringBuilder.append(buildDependenciesClassPath(pluginDependencies)).append("\"");

        stringBuilder.append(" ").append(ExternalJettyProcess.class.getName());

        System.out.println(stringBuilder.toString());

        return stringBuilder.toString();
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

        try {

            compile();

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

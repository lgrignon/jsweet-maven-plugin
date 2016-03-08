package org.jsweet;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.eclipse.jetty.server.Server;
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
import java.util.List;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/*
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

/**
 * @author EPOTH -/- ponthiaux.e@sfeir.com -/- ponthiaux.eric@gmail.com
 */

public class JettyThread extends TickThread {

    private Server server;

    public JettyThread(AbstractJSweetMojo mojo) {

        super(mojo);

    }

    @Override
    public void onRun() {

        getLog().info("Jetty thread started ... ");

        server = new Server(8080);

        WebAppContext webAppContext = new WebAppContext();

        webAppContext.setContextPath("/");

        /* */

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(getMojo().getMavenProject().getBuild().getDirectory());

        stringBuilder.append("/");

        stringBuilder.append(getMojo().getMavenProject().getBuild().getFinalName());

        /* */

        getLog().info("Server resource base [" + stringBuilder.toString() + "]");

        /* */

        webAppContext.setResourceBase(stringBuilder.toString());

        /* */

        ArrayList<URL> classLoaderUrls = new ArrayList<>();

        List<Artifact> dependencies = getMojo().getMavenProject().getCompileArtifacts();

        /* */

        for ( Artifact dependency : dependencies ) {

            try {

                StringBuilder urlBuilder = new StringBuilder();

                urlBuilder.append("file://");

                urlBuilder.append(getMojo().getMavenSession().getLocalRepository().getBasedir());

                urlBuilder.append("/");

                urlBuilder.append(getMojo().getMavenSession().getLocalRepository().pathOf(dependency));

                getLog().info(urlBuilder.toString());

                classLoaderUrls.add(Paths.get(urlBuilder.toString()).toUri().toURL());

            } catch (MalformedURLException malFormedURLException) {

                getLog().info(malFormedURLException);

            }

        }

        URLClassLoader urlClassLoader = new URLClassLoader(classLoaderUrls.toArray(new URL[classLoaderUrls.size()]),this.getContextClassLoader());

        WebAppClassLoader webAppClassLoader = null;


        try {

            webAppClassLoader = new WebAppClassLoader(
                    urlClassLoader,
                    webAppContext
            );

        } catch (IOException ioException) {

            getLog().error("When creating Web App classLoader ", ioException);

            return;

        }

         webAppContext.setClassLoader(webAppClassLoader);

        server.setHandler(webAppContext);

        /* */

        try {

            server.start();

        } catch (Exception exception) {

            getLog().info(exception);

        }

    }

    @Override
    public void execute() {

        try {

            server.stop();

            getLog().info("Stopping jetty ...");

            while (server.isRunning()) {

                Thread.yield();

            }

            getLog().info("Jetty stopped");

        } catch (Exception exception) {

            getLog().info(exception);

        }

        /* */

        try {

            compile();

        } catch (Exception exception) {

            return;

        }

        /* */

        try {

            getLog().info("Jetty is restarting ...");

            server.start();

        } catch (Exception exception) {

            getLog().info(exception);

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
                                +"WEB-INF/classes"

                        )

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

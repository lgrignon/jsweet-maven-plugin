package org.jsweet;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
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

        getLog().info("Jetty thread started ... ");

        server = new Server(8080);


        /* Mount the application */


        WebAppContext webAppContext = new WebAppContext();

        webAppContext.setContextPath("/");

        WebAppClassLoader webAppClassLoader = null;

        try {

            webAppClassLoader = new WebAppClassLoader(webAppContext);

        } catch (IOException ioException) {

            getLog().error("When creating Web App classLoader ", ioException);

            return;

        }

        /* */

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(getMojo().getMavenProject().getBasedir());

        stringBuilder.append("/");

        stringBuilder.append("src/main/webapp");

        /* */

        getLog().info("Server resource base [" + stringBuilder.toString() + "]");

        /* */

        webAppContext.setResourceBase(stringBuilder.toString());

        /* */

        stringBuilder.delete(0, stringBuilder.length());

        stringBuilder.append(getMojo().getMavenProject().getBuild().getDirectory());

        stringBuilder.append("/");

        stringBuilder.append(getMojo().getMavenProject().getBuild().getFinalName());

        stringBuilder.append("/WEB-INF/classes");

        /* */

        try {

            getLog().info("WebApp classes directory [" + stringBuilder.toString() + "]");

            Resource classesResource = Resource.newResource(stringBuilder.toString());

            webAppClassLoader.addClassPath(classesResource);

        } catch (MalformedURLException malformedURLException) {

            getLog().info(stringBuilder.toString(), malformedURLException);

            return;

        } catch (IOException ioException) {

            getLog().info(stringBuilder.toString(), ioException);

            return;

        }

        /* */

        List<Artifact> dependencies = getMojo().getMavenProject().getCompileArtifacts();

        /* */

        StringBuilder urlBuilder = new StringBuilder();

        for (Artifact dependency : dependencies) {

            try {

                urlBuilder.append(getMojo().getMavenSession().getLocalRepository().getBasedir());

                urlBuilder.append("/");

                urlBuilder.append(getMojo().getMavenSession().getLocalRepository().pathOf(dependency));

                getLog().info("Add to webapp classpath [" + urlBuilder.toString() + "]");

                Resource lib = Resource.newResource(urlBuilder.toString());

                webAppClassLoader.addClassPath(lib);

                urlBuilder.delete(0,urlBuilder.length());

            } catch (MalformedURLException malFormedURLException) {

                getLog().info(malFormedURLException);

            } catch (IOException ioException) {

                getLog().info(urlBuilder.toString(),ioException);

                return;

            }

        }

        webAppContext.setClassLoader(webAppClassLoader);



        /* to resolve source maps */



        stringBuilder.delete(0,stringBuilder.length());

        stringBuilder.append(getMojo().getMavenProject().getBasedir());

        stringBuilder.append("/");

        stringBuilder.append("src/main/java");

        getLog().info("Source maps resource base [" + stringBuilder.toString() + "]");

        WebAppContext javaSourcesContext = new WebAppContext();

        javaSourcesContext.setContextPath("/java");

        javaSourcesContext.setResourceBase(stringBuilder.toString());



        /* Mount all the context */



        ArrayList<Handler> handlers = new ArrayList<>();


        handlers.add(webAppContext);

        handlers.add(javaSourcesContext);


        ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();

        contextHandlerCollection.setHandlers(handlers.toArray(new Handler[handlers.size()]));

        server.setHandler(contextHandlerCollection);



        /* start the server */



        try {

            server.start();

            while(!server.isRunning())
            {
                Thread.yield();
            }

            getLog().info("Jetty has started");

        } catch (Exception exception) {

            getLog().info(exception);

            return;

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

            return;

        }

        /* */

        try {

            compile();

        } catch (Exception exception) {

            return;

        }

        /* */

        try {

            getLog().info("Jetty is starting ...");

            server.start();

            while (!server.isRunning()) {

                Thread.yield();

            }

            getLog().info("Jetty has started ...");

        } catch (Exception exception) {

            getLog().info(exception);

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
                        element(name("source"),"8"),
                        element(name("target"),"8")

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

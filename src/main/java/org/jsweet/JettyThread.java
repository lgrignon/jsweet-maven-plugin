package org.jsweet;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.jetty.server.Server;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Scanner;

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

    private ProcessBuilder processBuilder;

    private Process currentJettyProcess;

    private IOThread processIOThread;

    public JettyThread(AbstractJSweetMojo mojo) {

        super(mojo);

    }

    private File prepareJettyInstall(List<Artifact> pluginDependencies) throws IOException {

        getLog().info("- validating jetty install ");

        File baseInstallDirectory = new File(getMojo().getMavenProject().getBuild().getDirectory() + "/.jetty/");

        if (!baseInstallDirectory.exists()) {

            baseInstallDirectory.mkdir();

            getLog().debug("- create " + baseInstallDirectory.getCanonicalPath());

        }

        for (Artifact dependency : pluginDependencies) {

            StringBuilder stringBuilder = new StringBuilder();

            stringBuilder.append(getMojo().getMavenSession().getLocalRepository().getBasedir());

            stringBuilder.append("/");

            stringBuilder.append(getMojo().getMavenSession().getLocalRepository().pathOf(dependency));

            File targetFile = new File(stringBuilder.toString());

            File destinationFile = new File(baseInstallDirectory.getCanonicalPath() + "/" + targetFile.getName());

            if (!destinationFile.exists()) {

                getLog().debug("- copy " + targetFile.getName() + " to " + destinationFile.getCanonicalPath());

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

        getLog().info("- Starting jetty ... ");

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


        );

        processBuilder.directory(baseJettyPath);

        /* add webapp dependencies */

        getLog().debug("- Building webapp dependencies");

        processBuilder.environment().put("WEBAPP_DEPENDENCIES", buildDependenciesClassPath(webAppDependencies));

        /* */

        getLog().debug("- building webapp resource base");

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(getMojo().getMavenProject().getBasedir());

        stringBuilder.append("/");

        stringBuilder.append("src/main/webapp");

        processBuilder.environment().put("RESOURCE_BASE", stringBuilder.toString());

        /* */

        getLog().debug("- building webapp server classes base");

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

        getLog().debug("Source maps resource base [" + stringBuilder.toString() + "]");

        processBuilder.environment().put("ADDITIONAL_RESOURCE_BASE", stringBuilder.toString());

        try {

            getLog().debug("- calling jetty");

            currentJettyProcess = processBuilder.start();

            inheritIO(currentJettyProcess.getInputStream(), currentJettyProcess.getErrorStream());

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

    private void inheritIO(final InputStream inputStream, final InputStream errorStream) {

        if (processIOThread != null && processIOThread.isAlive()) {

            processIOThread.kill();

            while (processIOThread.isAlive()) {

                Thread.yield();
            }

        }

        processIOThread = new IOThread(inputStream, errorStream);

        processIOThread.start();

    }

    private class IOThread extends Thread {

        private InputStream stream;

        private InputStream errorStream;

        private boolean run = true;

        public IOThread(InputStream stream, InputStream errorStream) {

            this.stream = stream;

            this.errorStream = errorStream;
        }

        public void kill() {

            this.run = false;

        }

        public void run() {

            while (this.run) {

                Scanner scs = new Scanner(this.stream);

                while (scs.hasNextLine()) {

                    getLog().info(scs.nextLine());

                    Thread.yield();

                }

                Scanner sce = new Scanner(this.errorStream);

                while (sce.hasNextLine()) {

                    getLog().info(sce.nextLine());

                    Thread.yield();

                }

                Thread.yield();

            }

        }

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

        getLog().info("- Stopping jetty");

        currentJettyProcess.destroy();

        getLog().info("- Jetty stopped");

        try {

            compile();

        } catch (Exception exception) {

            return;

        }

        try {

            getLog().info("- Starting jetty");

            currentJettyProcess = processBuilder.start();

            inheritIO(currentJettyProcess.getInputStream(), currentJettyProcess.getErrorStream());

        } catch (IOException ioException) {

            getLog().info(ioException);

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
                        element(name("source"), "1.8"),
                        element(name("target"), "1.8"),
                        element("useIncrementalCompilation", "true")

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

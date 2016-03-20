package org.jsweet;


import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.*;
import java.util.List;

import static java.nio.file.StandardWatchEventKinds.*;

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

@Mojo(name = "jetty-watch", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.COMPILE)
public class JSweetJettyWatchMojo extends AbstractJSweetMojo {


    private TranspilerThread transpilerThread;

    private JettyThread jettyThread;

    public void execute() throws MojoFailureException, MojoExecutionException {

        System.setProperty("org.eclipse.jetty.io.LEVEL", "ALL");

        MavenProject project = getMavenProject();

        System.setErr(new PrintStream(new StdErrLogStream(getLog())));

        setOutDir(project.getBasedir() + "/src/main/webapp/" + getRelativeOutDir());

        getLog().info("Starting transpiler thread ...");

        getLog().info("Transpilator output directory " + getOutDir());

        transpilerThread = new TranspilerThread(this, createJSweetTranspiler(project));

        transpilerThread.start();

        while (!transpilerThread.isRunning()) {

            Thread.yield();

        }

        /* */

        getLog().info("Starting jetty thread  ... ");

        jettyThread = new JettyThread(this);

        jettyThread.start();

        while (!jettyThread.isRunning()) {

            Thread.yield();

        }

        /* */

        WatchService jsweetWatcher = createJSweetWatcher(project);

        WatchService jettyWatcher = createJettyWatcher(project);

        /* */

        for (; ; ) {

            /* */

            int jettyWatchReturn = jettyWatch(jettyWatcher);

            if (jettyWatchReturn == -1) {

                try {

                    jettyWatcher.close();

                    jettyWatcher = createJettyWatcher(project);

                } catch (IOException ioException) {

                    getLog().info(ioException);

                }

            }

            /* */

            Thread.yield();

            /* */

            int jsweetWatchReturn = jsweetWatch(jsweetWatcher);

            if (jsweetWatchReturn == -1) {

                try {

                    jsweetWatcher.close();

                    jsweetWatcher = createJSweetWatcher(project);

                } catch (IOException ioException) {

                    getLog().info(ioException);

                }

            }

            /* */

            Thread.yield();

            /* */

        }


    }

    private WatchService createJSweetWatcher(MavenProject project) {

        WatchService watchService = null;

        try {

            @SuppressWarnings("unchecked")
            List<String> sourcePaths = project.getCompileSourceRoots();

            getLog().info("Updating jsweet source paths");

            SourceScanner sourceScanner = new SourceScanner(
                    this,
                    sourcePaths,
                    includes,
                    excludes,
                    sharedIncludes

            );

            watchService = FileSystems.getDefault().newWatchService();

            WatcherUtils.registerPaths(this, watchService, sourceScanner.scan());

            /* */

        } catch (IOException ioException) {

            getLog().info(ioException);

        }

        return watchService;

    }

    private WatchService createJettyWatcher(MavenProject project) {

        WatchService watchService = null;

        try {

            @SuppressWarnings("unchecked")
            List<String> sourcePaths = project.getCompileSourceRoots();

            getLog().info("Updating server source paths");

            SourceScanner sourceScanner = new SourceScanner(
                    this,
                    sourcePaths,
                    excludes,
                    includes,
                    sharedIncludes

            );

            watchService = FileSystems.getDefault().newWatchService();

            WatcherUtils.registerPaths(this, watchService, sourceScanner.scan());

            /* */

        } catch (IOException ioException) {

            getLog().info(ioException);

        }

        return watchService;

    }



    /* */

    private int jsweetWatch(WatchService watchService) {

        WatchKey key;

        key = watchService.poll();

        if (key == null) return 0;

        for (WatchEvent<?> event : key.pollEvents()) {

            WatchEvent.Kind<?> kind = event.kind();

            if (kind == OVERFLOW) {

                continue;

            }

            @SuppressWarnings("unchecked")
            WatchEvent<Path> ev = (WatchEvent<Path>) event;

            Path filename = ev.context();

            /* */

            if (kind == ENTRY_MODIFY) {

                if (isJavaFile(filename.toString())) {

                    getLog().info("Jsweet file change detected * " + filename);

                    transpilerThread.tick();

                }

            }

            /* */

            if (kind == ENTRY_CREATE) {

                if (isJavaFile(filename.toString())) {

                    getLog().info("Jsweet file change detected * " + filename);

                    transpilerThread.tick();

                } else {

                    getLog().info("Jsweet new directory added");

                    return -1;

                }

            }

            /* */

            if (kind == ENTRY_DELETE) {

                getLog().info("Jsweet file change detected * " + filename);

                transpilerThread.tick();

            }

        }

        key.reset();

        return 0;

    }

    private int jettyWatch(WatchService jsweetWatcher) {

        WatchKey jettyKey;

        jettyKey = jsweetWatcher.poll();

        if (jettyKey == null) return 0;

        for (WatchEvent<?> event : jettyKey.pollEvents()) {

            WatchEvent.Kind<?> kind = event.kind();

            if (kind == OVERFLOW) {

                continue;

            }

            @SuppressWarnings("unchecked")
            WatchEvent<Path> ev = (WatchEvent<Path>) event;

            Path filename = ev.context();

            /* */

            if (kind == ENTRY_MODIFY) {

                if (isJavaFile(filename.toString())) {

                    getLog().info("Jetty file change detected * " + filename);

                    jettyThread.tick();

                }

            }

            /* */

            if (kind == ENTRY_CREATE) {

                if (isJavaFile(filename.toString())) {

                    getLog().info("Jetty file change detected ! " + filename);

                    jettyThread.tick();

                } else {

                    getLog().info("Jetty new directory added !");

                    return -1;

                }

            }

            /* */

            if (kind == ENTRY_DELETE) {

                getLog().info("Jetty file change detected ! " + filename);

                jettyThread.tick();

            }

        }

        jettyKey.reset();

        return 0;
    }

    /* */

    private boolean isJavaFile(String fileName) {

        return fileName.endsWith(".java");
    }

}

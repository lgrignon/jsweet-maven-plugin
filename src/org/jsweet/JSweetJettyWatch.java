package org.jsweet;

import com.sun.nio.file.SensitivityWatchEventModifier;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static java.nio.file.StandardWatchEventKinds.*;

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
 *
 *  @author EPOTH -/- ponthiaux.e@sfeir.com -/- ponthiaux.eric@gmail.com
 *
 */

@Mojo(name = "jetty-watch", defaultPhase = LifecyclePhase.TEST,requiresDependencyResolution = ResolutionScope.COMPILE)
public class JSweetJettyWatch extends AbstractJSweetMojo {

    @Parameter(defaultValue = "HIGH", required = false, readonly = true)
    public String watcherSensitivity;

    private TranspilatorThread transpilatorThread;

    private JettyThread jettyThread;

    private SensitivityWatchEventModifier sensitivity = SensitivityWatchEventModifier.HIGH;

    public void execute() throws MojoFailureException, MojoExecutionException {

        System.setProperty("org.eclipse.jetty.io.LEVEL","ALL");

        MavenProject project = getMavenProject();

        System.setErr(new PrintStream(new JSweetLogStream(getLog())));

        setOutDir(project.getBasedir() + "/src/main/webapp/" + getRelativeOutDir());

        getLog().info("Starting transpiler thread  ... ");

        getLog().info("Transpilator output directory " + getOutDir());

        transpilatorThread = new TranspilatorThread(this);

        transpilatorThread.setTranspiler(createJSweetTranspiler(project));

        transpilatorThread.start();

        while (!transpilatorThread.isRunning()) {

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

        ArrayList<Path> jsweetPaths = new ArrayList<>();

        WatchService jsweetWatcher = createJSweetWatcher(jsweetPaths, project);

        WatchService jettyWatcher = createJettyWatcher(jsweetPaths, project);

        /* */

        for (; ; ) {

            /* */

            int jettyWatchReturn = jettyWatch(jettyWatcher);

            if (jettyWatchReturn == -1) {

                try {

                    jettyWatcher.close();

                    jettyWatcher = createJettyWatcher(jsweetPaths, project);

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

                    jsweetPaths.clear();

                    jsweetWatcher = createJSweetWatcher(jsweetPaths, project);

                } catch (IOException ioException) {

                    getLog().info(ioException);

                }

            }

            /* */

            Thread.yield();

            /* */

        }


    }

    private WatchService createJSweetWatcher(ArrayList<Path> jsweetWatchedPaths, MavenProject project) {

        WatchService watchService = null;

        try {

            @SuppressWarnings("unchecked")
            List<String> sourcePaths = project.getCompileSourceRoots();

            getLog().info("Updating jsweet watcher source paths");

            int i = 0, j = 0, k = 0, l = 0;

            for (i = 0, j = sourcePaths.size(); i < j; i++) {

                String sourcePath = sourcePaths.get(i);

                getLog().info("     - Analysing " + sourcePath);

                DirectoryScanner dirScanner = new DirectoryScanner();

                dirScanner.setBasedir(new File(sourcePath));

                dirScanner.setIncludes(includes);

                dirScanner.setExcludes(excludes);

                dirScanner.scan();

                /*  */

                String[] includedDirectories = dirScanner.getIncludedDirectories();

                /*  */

                if (includedDirectories.length == 0) {

                    getLog().info("     - No source includes found , using [" + sourcePath + "]");

                    includedDirectories = new String[]{sourcePath};

                } else {

                    getLog().info("     - " + includedDirectories.length + " directory found .");

                    for (k = 0, l = includedDirectories.length; k < l; k++) {

                        includedDirectories[k] = dirScanner.getBasedir().getPath() + System.getProperty("file.separator") + includedDirectories[k];

                    }

                }

                /*  */

                for (k = 0, l = includedDirectories.length; k < l; k++) {

                    Path path = Paths.get(includedDirectories[k]);

                    jsweetWatchedPaths.add(path);

                }

                /*  */

            }

            /* */

            watchService = FileSystems.getDefault().newWatchService();

            /* */

            for (i = 0, j = jsweetWatchedPaths.size(); i < j; i++) {

                Path includedDirectory = jsweetWatchedPaths.get(i);

                try {

                    getLog().info("     - Registering [" + includedDirectory.toString() + "]");

                    includedDirectory.register(

                            watchService,

                            new WatchEvent.Kind[]{ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE, OVERFLOW},

                            sensitivity

                    );

                } catch (IOException ioException) {

                    getLog().info("    * Cannot register [" + includedDirectory.toString() + "]", ioException);

                }

            }

        } catch (IOException ioException) {

            getLog().info(ioException);

        }

        return watchService;

    }

    private WatchService createJettyWatcher(ArrayList<Path> jsweetWatcherPath, MavenProject project) {

        WatchService watchService = null;

        try {

            @SuppressWarnings("unchecked")
            List<String> sourcePaths = project.getCompileSourceRoots();

            getLog().info("Updating jetty watcher source paths");

            List<Path> jettyWatchedPaths = new ArrayList<>();

            int i = 0, j = 0, k = 0, l = 0, m = 0, n = 0;

            for (String sourcePath : sourcePaths) {

                getLog().info("     - Analysing " + sourcePath);

                Path path = Paths.get(sourcePath);

                FileVisitor fileVisitor = new FileVisitor();

                Files.walkFileTree(path, fileVisitor);

                jettyWatchedPaths.addAll(fileVisitor.getDirectories());

                /* Excludes don't seem to work well (i'm certainly missing something ... ) */
                /* So i use the first path list generated for jsweet to filter the jetty one */

                for (k = 0, j = jsweetWatcherPath.size(); k < j; k++) {

                    Path jsweetPath = jsweetWatcherPath.get(k);

                    Iterator<Path> jettyPathIterator = jettyWatchedPaths.iterator();

                    while (jettyPathIterator.hasNext()) {

                        Path jettyPath = jettyPathIterator.next();

                        if (jettyPath.toString().trim().equals(jsweetPath.toString().trim())) {

                            jettyPathIterator.remove(); // remove duplicated path

                        }

                    }

                }

                // if there no path stop here !

            }

            /* */

            watchService = FileSystems.getDefault().newWatchService();

            /* */

            for (i = 0, j = jettyWatchedPaths.size(); i < j; i++) {

                Path includedDirectory = jettyWatchedPaths.get(i);

                try {

                    getLog().info("     - Registering [" + includedDirectory.toString() + "]");

                    includedDirectory.register(

                            watchService,

                            new WatchEvent.Kind[]{ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE, OVERFLOW},

                            sensitivity

                    );

                } catch (IOException ioException) {

                    getLog().info("    * Cannot register [" + includedDirectory.toString() + "]", ioException);

                }

            }

        } catch (IOException ioException) {

            getLog().info(ioException);

        }

        return watchService;

    }

    /* */

    private int jsweetWatch(WatchService jsweetWatcher) {

        WatchKey jsweetKey;

        jsweetKey = jsweetWatcher.poll();

        if (jsweetKey == null) return 0;

        for (WatchEvent<?> event : jsweetKey.pollEvents()) {

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

                    transpilatorThread.tick();

                }

            }

            /* */

            if (kind == ENTRY_CREATE) {

                if (isJavaFile(filename.toString())) {

                    getLog().info("Jsweet file change detected ! " + filename);

                    transpilatorThread.tick();

                } else {

                    getLog().info("Jsweet new directory added");

                    return -1;

                }

            }

            /* */

            if (kind == ENTRY_DELETE) {

                getLog().info("Jsweet file change detected ! " + filename);

                transpilatorThread.tick();

            }

        }

        jsweetKey.reset();

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

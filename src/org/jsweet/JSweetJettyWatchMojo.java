package org.jsweet;

import com.sun.nio.file.SensitivityWatchEventModifier;
import org.apache.commons.lang3.ArrayUtils;
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
 * @author EPOTH -/- ponthiaux.e@sfeir.com -/- ponthiaux.eric@gmail.com
 */

@Mojo(name = "jetty-watch", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.COMPILE)
public class JSweetJettyWatchMojo extends AbstractJSweetMojo {

    @Parameter(defaultValue = "HIGH", required = false, readonly = true)
    public String watcherSensitivity;

    private TranspilerThread transpilerThread;

    private JettyThread jettyThread;

    private SensitivityWatchEventModifier sensitivity = SensitivityWatchEventModifier.HIGH;

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

        ArrayList<Path> paths = new ArrayList<>();

        try {

            @SuppressWarnings("unchecked")
            List<String> sourcePaths = project.getCompileSourceRoots();

            getLog().info("Updating jsweet source paths");

            int k = 0, l = 0;

            for (String sourcePath : sourcePaths) {

                getLog().info("     - Analysing " + sourcePath);

                DirectoryScanner dirScanner = new DirectoryScanner();

                dirScanner.setBasedir(new File(sourcePath));

                dirScanner.setIncludes(ArrayUtils.addAll(includes, sharedIncludes));

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

                    paths.add(path);

                }

                /*  */

            }

            /* */

            watchService = FileSystems.getDefault().newWatchService();

            /* */

            for (Path includedPath : paths) {

                try {

                    getLog().info("     - Registering [" + includedPath.toString() + "]");

                    includedPath.register(

                            watchService,

                            new WatchEvent.Kind[]{ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE, OVERFLOW},

                            sensitivity

                    );

                } catch (IOException ioException) {

                    getLog().info("    * Cannot register [" + includedPath.toString() + "]", ioException);

                }

            }

        } catch (IOException ioException) {

            getLog().info(ioException);

        }

        return watchService;

    }

    private WatchService createJettyWatcher(MavenProject project) {

        WatchService watchService = null;

        ArrayList<Path> jettyWatchedPath = new ArrayList<>();

        try {

            @SuppressWarnings("unchecked")
            List<String> sourcePaths = project.getCompileSourceRoots();

            getLog().info("Updating server source paths");

            int k = 0, l = 0;

            for (String sourcePath : sourcePaths) {

                getLog().info("     - Analysing " + sourcePath);

                DirectoryScanner dirScanner = new DirectoryScanner();

                dirScanner.setBasedir(new File(sourcePath));

                dirScanner.setIncludes(ArrayUtils.addAll(excludes, sharedIncludes));

                dirScanner.setExcludes(includes);

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

                    jettyWatchedPath.add(path);

                }

                /*  */

            }

            /* */

            watchService = FileSystems.getDefault().newWatchService();

            /* */

            for (Path path : jettyWatchedPath) {

                try {

                    getLog().info("     - Registering [" + path.toString() + "]");

                    path.register(

                            watchService,

                            new WatchEvent.Kind[]{ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE, OVERFLOW},

                            sensitivity

                    );

                } catch (IOException ioException) {

                    getLog().info("    * Cannot register [" + path.toString() + "]", ioException);

                }

            }

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

                    getLog().info("Jsweet file change detected ! " + filename);

                    transpilerThread.tick();

                } else {

                    getLog().info("Jsweet new directory added");

                    return -1;

                }

            }

            /* */

            if (kind == ENTRY_DELETE) {

                getLog().info("Jsweet file change detected ! " + filename);

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

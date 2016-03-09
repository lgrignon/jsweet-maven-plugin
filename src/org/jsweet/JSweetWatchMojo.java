package org.jsweet;

import com.sun.nio.file.SensitivityWatchEventModifier;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * @author EPOTH - ponthiaux.e@sfeir.com -/- ponthiaux.eric@gmail.com
 * @author Louis Grignon On the fly transpilation through maven .
 *         <p>
 *         Very early version
 */
@Mojo(name = "watch", defaultPhase = LifecyclePhase.TEST)
public class JSweetWatchMojo extends AbstractJSweetMojo {

    @Parameter(defaultValue = "HIGH", required = false, readonly = true)
    public String watcherSensitivity;

    private TranspilatorThread transpilatorThread;


    private SensitivityWatchEventModifier sensitivity = SensitivityWatchEventModifier.HIGH;

    public void execute() throws MojoFailureException, MojoExecutionException {

        setOutDir(getOutDir()+"/" + getRelativeOutDir());

        MavenProject project = getMavenProject();

        getLog().info("- Starting transpiler thread  ... ");

        transpilatorThread = new TranspilatorThread(this);

        transpilatorThread.setTranspiler(createJSweetTranspiler(project));

        transpilatorThread.start();

        while (!transpilatorThread.isRunning()) {

            Thread.yield();

        }

        start(project);

    }

    private void start(MavenProject project) {

        for (; ; ) {

            try {

                @SuppressWarnings("unchecked")
                List<String> sourcePaths = project.getCompileSourceRoots();

                getLog().info("Updating watcher source paths");

                List<Path> watchedPaths = new ArrayList<>();

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

                        watchedPaths.add(path);

                    }

                    /*  */

                }

                /* */

                WatchService watchService = FileSystems.getDefault().newWatchService();

                /* */

                for (i = 0, j = watchedPaths.size(); i < j; i++) {

                    Path includedDirectory = watchedPaths.get(i);

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

                /* */

                watch(watchService);

                /* */

                try {

                    getLog().info("- Closing watcher");

                    watchService.close(); // always close the watcher

                } catch (IOException ioException) {

                    getLog().info(ioException);

                }

            } catch (IOException ioException) {

                getLog().info(ioException);

            }

        }

    }

    /* */

    private void watch(WatchService watchService) {

        getLog().info("Now waiting for file change");

        for (; ; ) {

            WatchKey key;

            try {

                key = watchService.take();

            } catch (InterruptedException i) {

                return;

            }

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

                        getLog().info("File change detected * " + filename);

                        transpilatorThread.tick();

                    }

                }

                /* */

                if (kind == ENTRY_CREATE) {

                    if (isJavaFile(filename.toString())) {

                        getLog().info("File change detected ! " + filename);

                        transpilatorThread.tick();

                    } else {

                        getLog().info("New directory added");

                        return;

                    }

                }

                /* */

                if (kind == ENTRY_DELETE) {

                    getLog().info("File change detected ! " + filename);

                    transpilatorThread.tick();

                }

            }

            boolean valid = key.reset();

            if (!valid) {

                break;

            }

        }

    }

    /* */

    private boolean isJavaFile(String fileName) {

        return fileName.endsWith(".java");
    }


}

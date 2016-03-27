package org.jsweet;

import com.sun.nio.file.SensitivityWatchEventModifier;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.*;
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

    private TranspilerThread transpilerThread;

    private SensitivityWatchEventModifier sensitivity = SensitivityWatchEventModifier.HIGH;

    public void execute() throws MojoFailureException, MojoExecutionException {

        setOutDir(getOutDir() + "/" + getRelativeOutDir());

        MavenProject project = getMavenProject();

        getLog().info("- Starting transpiler thread  ... ");

        transpilerThread = new TranspilerThread(this, createJSweetTranspiler(project));

        transpilerThread.start();

        while (!transpilerThread.isRunning()) {

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

                SourceScanner sourceScanner = new SourceScanner(
                        this,
                        sourcePaths,
                        includes,
                        excludes,
                        sharedIncludes
                );

                WatchService watchService = FileSystems.getDefault().newWatchService();

                WatcherUtils.registerPaths(this, watchService, sourceScanner.scan());

                /* */

                watch(watchService);

                /* */

                try {

                    watchService.close();

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

                        transpilerThread.tick();

                    }

                }

                /* */

                if (kind == ENTRY_CREATE) {

                    if (isJavaFile(filename.toString())) {

                        getLog().info("File change detected * " + filename);

                        transpilerThread.tick();

                    } else {

                        getLog().info("New directory added");

                        return;

                    }

                }

                /* */

                if (kind == ENTRY_DELETE) {

                    getLog().info("File change detected ! " + filename);

                    transpilerThread.tick();

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

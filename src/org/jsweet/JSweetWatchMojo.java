package org.jsweet;

import com.sun.nio.file.SensitivityWatchEventModifier;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

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

    public void execute() throws MojoFailureException, MojoExecutionException {

        MavenProject project = getMavenProject();

        getLog().info("- Starting transpilator process  ... ");

        transpilatorThread = new TranspilatorThread(this, project);

        transpilatorThread.setTranspiler(createJSweetTranspiler(project));

        transpilatorThread.start();

        initialize(project);

    }

    private void initialize(MavenProject project) {

		/* */

        @SuppressWarnings("unchecked")
        List<String> sourcePaths = project.getCompileSourceRoots();

        try {

            for (; ; ) {

				/* */

                WatchService watchService = FileSystems.getDefault().newWatchService();

                List<Path> watchedPaths = new ArrayList<>();

				/* */

                getLog().info("+ Registering source path");

                for (String sourceDirectory : sourcePaths) {
                    Path path = Paths.get(sourceDirectory);
                    watchedPaths.add(path);
                    walkDirectoryTree(path, watchedPaths, watchService);
                }

                getLog().info("- Registering source path , DONE .");

				/* */

                getLog().info("");

				/* */

                getLog().info("- Listening for file change ... ");

				/* */

                try {
                    watch(watchService);
                } catch (Exception exception) {
                    watchService.close();
                }

				/* */

                Thread.yield();

				/* */

            }

			/* */

        } catch (IOException ioException) {

            getLog().error(ioException);

        }
    }

	/* */

    private void walkDirectoryTree(
            Path startPath,
            List<Path> watchedPaths,
            WatchService watchService
    )
            throws IOException {

        RegisteringFileTreeScanner scanner = new RegisteringFileTreeScanner(watchedPaths, watchService, this);

        scanner.setSensitivity(SensitivityWatchEventModifier.HIGH);

        Files.walkFileTree(startPath, scanner);

    }

	/* */

    private void watch(WatchService watchService) throws Exception {

        for (; ; ) {

            WatchKey key;

            try {

                key = watchService.take();

            } catch (InterruptedException x) {

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
                if (kind == ENTRY_MODIFY || kind == ENTRY_CREATE || kind == ENTRY_DELETE) {

                    getLog().info("* File change detected * " + filename);

                    transpilatorThread.tick();

                }

            }

            boolean valid = key.reset();
            if (!valid) {
                break;
            }
        }

		/* */

        try {
            watchService.close();
        } catch (IOException ioException) {
            getLog().error(ioException);
        }

		/* */
    }

}

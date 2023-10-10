package org.jsweet;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jsweet.transpiler.JSweetTranspiler;

import com.sun.nio.file.SensitivityWatchEventModifier;

/**
 *
 * @author EPOTH - ponthiaux.e@sfeir.com -/- ponthiaux.eric@gmail.com
 * @author Louis Grignon On the fly transpilation through maven .
 *
 *         Very early version
 *
 */
@Mojo(name = "watch", defaultPhase = LifecyclePhase.TEST)
public class JSweetWatchMojo extends AbstractJSweetMojo {

	@Parameter(defaultValue = "HIGH", required = false, readonly = true)
	public String watcherSensitivity;

	private TranspilatorThread transpilatorThread;

	private static final SensitivityWatchEventModifier SENSITIVITY_WATCH_EVENT_MODIFIER = SensitivityWatchEventModifier.HIGH;

	private static ReentrantLock __Lock = new ReentrantLock();

	private static LinkedList<String> __RandomKeysTrigger = new LinkedList<>();

	private JSweetTranspiler transpiler;

	public void execute() throws MojoFailureException, MojoExecutionException {
		super.execute();
		
		MavenProject project = getMavenProject();

		transpiler = createJSweetTranspiler(project);

		getLog().info("- Starting transpilator process  ... ");

		transpilatorThread = new TranspilatorThread(this, project);
		transpilatorThread.start();

		initialize(project);
	}

	private void initialize(MavenProject project) {
		List<String> sourcePaths = project.getCompileSourceRoots();

		try {

			for (;;) {

				WatchService watchService = FileSystems.getDefault().newWatchService();

				List<Path> watchedPaths = new ArrayList<>();

				getLog().info("+ Registering source path");

				for (String sourceDirectory : sourcePaths) {
					Path path = Paths.get(sourceDirectory);
					watchedPaths.add(path);
					walkDirectoryTree(path, watchedPaths, watchService);
				}

				getLog().info("- Registering source path , DONE .");

				getLog().info("");

				getLog().info("- Listening for file change ... ");

				try {
					watch(watchService);
				} catch (Exception exception) {
					watchService.close();
				}

				Thread.yield();
			}

		} catch (IOException ioException) {

			getLog().error(ioException);

		}
	}

	/* */

	private void walkDirectoryTree(Path startPath, List<Path> watchedPaths, WatchService watchService)
			throws IOException {

		RegisteringFileTreeScanner scanner = new RegisteringFileTreeScanner(watchedPaths, watchService, this);

		Files.walkFileTree(startPath, scanner);

	}

	/* */

	private void watch(WatchService watchService) throws Exception {

		for (;;) {

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

					getLog().info("* File change detected *" + filename);

					__Lock.lock();

					__RandomKeysTrigger.add(generateRandomEntry()); // generate
																	// a random
																	// entry

					__Lock.unlock();

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

	private String generateRandomEntry() {

		return String.valueOf((int) (10000 * Math.random()));
	}

	public static class RegisteringFileTreeScanner extends SimpleFileVisitor<Path> {

		private List<Path> directories;
		private WatchService watchService;
		private AbstractMojo mojo;

		public RegisteringFileTreeScanner(List<Path> directories, WatchService watchService, AbstractMojo mojo) {

			this.directories = directories;
			this.watchService = watchService;
			this.mojo = mojo;

		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {

			// --> DO NOTHING

			return CONTINUE;
		}

		@Override
		public FileVisitResult postVisitDirectory(Path directory, IOException exc) {

			directories.add(directory);

			try {

				directory.register(

				this.watchService,

				new WatchEvent.Kind[] { ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE, OVERFLOW },

				SENSITIVITY_WATCH_EVENT_MODIFIER

				);

				this.mojo.getLog().info("  - Added [" + directory.toString() + "]");

			} catch (IOException ioException) {

				this.mojo.getLog().error("  * Cannot register [" + directory.toString() + "]");

			}

			return CONTINUE;
		}

		@Override
		public FileVisitResult visitFileFailed(Path file, IOException exc) {

			// --> DO NOTHING

			return CONTINUE;
		}
	}

	private class TranspilatorThread extends Thread {

		private MavenProject project;
		private AbstractMojo mojo;

		public TranspilatorThread(AbstractMojo mojo, MavenProject project) {
			setPriority(Thread.MAX_PRIORITY);
			this.project = project;
			this.mojo = mojo;
		}

		public void run() {

			this.mojo.getLog().info("- Transpilator process started ...");

			StringBuilder stringBuilder = new StringBuilder();

			stringBuilder.append("- JSweet transpiler version ");
			stringBuilder.append(JSweetConfig.getVersionNumber());
			stringBuilder.append(" (build date: ");
			stringBuilder.append(JSweetConfig.getBuildDate()).append(")");

			getLog().info(stringBuilder.toString());

			for (;;) {

				if (__Lock.tryLock()) {

					if (__RandomKeysTrigger.size() != 0) {
						__RandomKeysTrigger.removeLast();
						try {
							transpile(project, transpiler);
						} catch (Exception exception) {
							getLog().info(exception.getMessage());
						}
					}
					__Lock.unlock();
				}
				Thread.yield();
			}
		}
	}
}

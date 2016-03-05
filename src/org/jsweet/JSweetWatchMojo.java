package org.jsweet;

import com.sun.nio.file.SensitivityWatchEventModifier;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.*;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.jsweet.transpiler.*;
import org.jsweet.transpiler.util.ConsoleTranspilationHandler;
import org.jsweet.transpiler.util.ErrorCountTranspilationHandler;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.file.StandardWatchEventKinds.*;

import static java.nio.file.FileVisitResult.*;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 *
 * @author EPOTH - ponthiaux.e@sfeir.com -/- ponthiaux.eric@gmail.com
 *
 *  On the fly transpilation ...
 *
 */

@Mojo(name = "watch", defaultPhase = LifecyclePhase.TEST)
public class JSweetWatchMojo extends AbstractMojo {

    @Parameter(alias = "target", defaultValue = "ES3", required = true, readonly = true)
    public EcmaScriptComplianceLevel targetVersion;

    @Parameter(defaultValue = "none", required = false, readonly = true)
    public ModuleKind module;

    @Parameter(readonly = true)
    public String outDir;

    @Parameter(readonly = true)
    public String tsOut;

    @Parameter(required = false, readonly = true)
    public boolean bundle;

    @Parameter(defaultValue = "false", required = false, readonly = true)
    public boolean declaration;

    @Parameter(readonly = true)
    public String dtsOut;

    @Parameter(defaultValue = "false", required = false, readonly = true)
    public boolean sourceMap;

    @Parameter(defaultValue = "false", required = false, readonly = true)
    public boolean verbose;

    @Parameter(required = false, readonly = true)
    public String bundlesDirectory;

    @Parameter(defaultValue = "HIGH", required = false, readonly = true)
    public String watcherSensitivity;

    @Parameter(required = false, readonly = true)
    protected File candiesJsOut;

    @Parameter
    public String[] includes;

    @Parameter
    public String[] excludes;

    @Parameter(defaultValue = "UTF-8", required = false)
    public String encoding;

    @Parameter(defaultValue = "false", required = false)
    public boolean noRootDirectories;

    @Parameter(defaultValue = "false", required = false)
    public boolean enableAssertions;

    @Parameter(defaultValue = "${java.home}")
    protected File jdkHome;

    @Parameter(defaultValue = "${localRepository}", required = true, readonly = true)
    protected ArtifactRepository localRepository;

    @Parameter(defaultValue = "${project.remoteArtifactRepositories}", required = true, readonly = true)
    private List<ArtifactRepository> remoteRepositories;

    @Component
    private ArtifactFactory artifactFactory;

    @Component
    private ArtifactResolver resolver;

    @Component
    private ArtifactMetadataSource metadataSource;

    private JSweetTranspiler transpiler;

    private List<File> candiesJarDependenciesFiles;

    private TranspilatorThread T;

    private static SensitivityWatchEventModifier SENSITIVITY_WATCH_EVENT_MODIFIER = SensitivityWatchEventModifier.HIGH;

    /* */

    private static ReentrantLock __Lock = new ReentrantLock();

    /* */

    private static LinkedList<String> __RandomKeysTrigger = new LinkedList<>();

    /* */

    public void execute() throws MojoFailureException, MojoExecutionException {

        Map<?, ?> ctx = getPluginContext();

        MavenProject project = (MavenProject) ctx.get("project");

        /* */

        transpiler = createJSweetTranspiler(project);

        /* */

        getLog().info("- Starting transpilator process  ... ");

        T = new TranspilatorThread(this, project);

        T.start();

        /* */

        initialize(project);

        /* */

    }

    private void initialize(MavenProject project) {

        /* */

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

                getLog().info("- Registering source path , DONE ." );

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

                Thread.currentThread().yield();

                /* */

            }

            /* */

        } catch (IOException ioException) {

            getLog().error(ioException);

        }
    }

    /* */

    private void walkDirectoryTree(Path startPath, List<Path> watchedPaths, WatchService watchService) throws IOException {

        RegisteringFileTreeScanner scanner = new RegisteringFileTreeScanner(watchedPaths, watchService, this);

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

                WatchEvent<Path> ev = (WatchEvent<Path>) event;

                Path filename = ev.context();

                if (kind == ENTRY_MODIFY || kind == ENTRY_CREATE || kind == ENTRY_DELETE) {

                    getLog().info("* File change detected * " + filename);

                    __Lock.lock();

                    __RandomKeysTrigger.add(generateRandomEntry()); // generate a random entry

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

            //--> DO NOTHING

            return CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path directory, IOException exc) {

            directories.add(directory);

            try {

                directory.register(

                        this.watchService,

                        new WatchEvent.Kind[]{ ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE, OVERFLOW } ,

                        SENSITIVITY_WATCH_EVENT_MODIFIER

                );

                this.mojo.getLog().info("  - Added [" + directory.toString() + "]");

            } catch ( IOException ioException ) {

                this.mojo.getLog().error("  * Cannot register [" + directory.toString() + "]");

            }

            return CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {

            //--> DO NOTHING

            return CONTINUE;
        }
    }

    public void transpile(MavenProject project) throws MojoFailureException, MojoExecutionException {

        try {

            ErrorCountTranspilationHandler transpilationHandler = new ErrorCountTranspilationHandler(new ConsoleTranspilationHandler());

            try {

                SourceFile[] sources = collectSourceFiles(project);

                transpiler.transpile(transpilationHandler, sources);

            } catch (NoClassDefFoundError error) {

                transpilationHandler.report(JSweetProblem.JAVA_COMPILER_NOT_FOUND, null, JSweetProblem.JAVA_COMPILER_NOT_FOUND.getMessage());

            }

            int errorCount = transpilationHandler.getErrorCount();

            if (errorCount > 0) {

                throw new MojoFailureException("transpilation failed with " + errorCount + " error(s) and "
                        + transpilationHandler.getWarningCount() + " warning(s)");

            } else {

                if (transpilationHandler.getWarningCount() > 0) {

                    getLog().info("transpilation completed with " + transpilationHandler.getWarningCount() + " warning(s)");

                } else {

                    getLog().info("transpilation successfully completed with no errors and no warnings");

                }

            }

        } catch (Exception e) {

            getLog().error("transpilation failed", e);

            throw new MojoExecutionException("transpilation failed", e);

        }

    }

    private SourceFile[] collectSourceFiles(MavenProject project) {

        @SuppressWarnings("unchecked")
        List<String> sourcePaths = project.getCompileSourceRoots();

        getLog().info("source includes: " + ArrayUtils.toString(includes));
        getLog().info("source excludes: " + ArrayUtils.toString(excludes));

        getLog().info("sources paths: " + sourcePaths);

        List<SourceFile> sources = new LinkedList<>();

        for (String sourcePath : sourcePaths) {

            DirectoryScanner dirScanner = new DirectoryScanner();

            dirScanner.setBasedir(new File(sourcePath));

            dirScanner.setIncludes(includes);

            dirScanner.setExcludes(excludes);

            dirScanner.scan();

            for (String includedPath : dirScanner.getIncludedFiles()) {

                if (includedPath.endsWith(".java")) {

                    sources.add(new SourceFile(new File(sourcePath, includedPath)));

                }

            }

        }

        getLog().info("sourceFiles=" + sources);

        return sources.toArray(new SourceFile[0]);

    }

    private JSweetTranspiler createJSweetTranspiler(MavenProject project) throws MojoExecutionException {

        try {

            List<File> dependenciesFiles = getCandiesJars(project);

            String classPath = dependenciesFiles.stream()
                    .map(f -> f.getAbsolutePath())
                    .collect(joining(System.getProperty("path.separator")));

            String tsOutputDirPath = ".ts";

            if (isNotBlank(this.tsOut)) {

                tsOutputDirPath = new File(this.tsOut).getCanonicalPath();

            }

            File jsOutDir = null;

            String jsOutputDirPath = "js";

            if (isNotBlank(this.outDir)) {

                jsOutputDirPath = new File(this.outDir).getCanonicalPath();

            }

            jsOutDir = new File(jsOutputDirPath);

            File declarationOutDir = null;

            if (isNotBlank(this.dtsOut)) {

                declarationOutDir = new File(this.dtsOut).getCanonicalFile();

            }

            if (bundlesDirectory != null) {

                getLog().info("bundlesDirectory: " + bundlesDirectory);

            }

            JSweetConfig.initClassPath(jdkHome.getAbsolutePath());

            LogManager.getLogger("org.jsweet").setLevel(Level.ALL);

            transpiler = new JSweetTranspiler(new File(tsOutputDirPath), jsOutDir, candiesJsOut, classPath);

            transpiler.setTscWatchMode(false);
            transpiler.setEcmaTargetVersion(targetVersion);
            transpiler.setModuleKind(module);
            transpiler.setBundle(bundle);
            transpiler.setBundlesDirectory(StringUtils.isBlank(bundlesDirectory) ? null : new File(bundlesDirectory));
            transpiler.setPreserveSourceLineNumbers(sourceMap);
            transpiler.setEncoding(encoding);
            transpiler.setNoRootDirectories(noRootDirectories);
            transpiler.setIgnoreAssertions(!enableAssertions);
            transpiler.setGenerateDeclarations(declaration);

            transpiler.setDeclarationsOutputDir(declarationOutDir);

            return transpiler;

        } catch (Exception e) {

            getLog().error("failed to create transpiler", e);

            throw new MojoExecutionException("failed to create transpiler", e);

        }

    }

    private List<File> getCandiesJars(MavenProject project)
            throws ArtifactResolutionException, ArtifactNotFoundException {

        @SuppressWarnings("unchecked")
        List<Dependency> dependencies = project.getDependencies();

        getLog().info("dependencies=" + dependencies);

        List<Artifact> directDependencies = new LinkedList<>();

        for (Dependency dependency : dependencies) {

            Artifact mavenArtifact = artifactFactory.createArtifact(dependency.getGroupId(), dependency.getArtifactId(),
                    dependency.getVersion(), Artifact.SCOPE_COMPILE, "jar");

            getLog().info("add direct candy dependency: " + dependency + "=" + mavenArtifact);

            directDependencies.add(mavenArtifact);

        }

        ArtifactResolutionResult dependenciesResolutionResult = resolver.resolveTransitively( //
                new HashSet<>(directDependencies),
                project.getArtifact(),
                remoteRepositories,
                localRepository,
                metadataSource);

        @SuppressWarnings("unchecked")
        Set<ResolutionNode> allDependenciesArtifacts = dependenciesResolutionResult.getArtifactResolutionNodes();

        getLog().info("all candies artifacts: " + allDependenciesArtifacts);

        List<File> dependenciesFiles = new LinkedList<>();

        for (ResolutionNode depResult : allDependenciesArtifacts) {

            dependenciesFiles.add(depResult.getArtifact().getFile());

        }

        getLog().info("candies jars: " + dependenciesFiles);

        return dependenciesFiles;
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

            for (; ; ) {

                if (__Lock.tryLock()) {

                    if (__RandomKeysTrigger.size() != 0) {

                        __RandomKeysTrigger.removeLast();

                        try {

                            transpile(project);

                        } catch (Exception exception) {

                            getLog().info(exception.getMessage());

                        }

                    }

                    __Lock.unlock();

                }

                yield();

            }

        }

    }


}

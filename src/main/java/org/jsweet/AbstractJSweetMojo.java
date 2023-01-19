package org.jsweet;

import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.ResolutionNode;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.util.DirectoryScanner;
import org.jsweet.transpiler.EcmaScriptComplianceLevel;
import org.jsweet.transpiler.JSweetFactory;
import org.jsweet.transpiler.JSweetProblem;
import org.jsweet.transpiler.JSweetTranspiler;
import org.jsweet.transpiler.ModuleKind;
import org.jsweet.transpiler.ModuleResolution;
import org.jsweet.transpiler.SourceFile;
import org.jsweet.transpiler.SourcePosition;
import org.jsweet.transpiler.util.ConsoleTranspilationHandler;
import org.jsweet.transpiler.util.ErrorCountTranspilationHandler;
import org.jsweet.transpiler.util.ProcessUtil;

import com.sun.source.util.JavacTask;

public abstract class AbstractJSweetMojo extends AbstractMojo {

    @Parameter(alias = "target", required = false)
    protected EcmaScriptComplianceLevel targetVersion;

    @Parameter(required = false)
    protected ModuleKind module;

    @Parameter(required = false)
    protected String outDir;

    @Parameter(required = false)
    protected String tsOut;

    @Parameter(required = false)
    protected Boolean tsserver;

    @Parameter(required = false)
    protected Boolean bundle;

    @Parameter(required = false)
    protected Boolean declaration;

    @Parameter(required = false)
    protected Boolean tsOnly;

    @Parameter(required = false)
    protected String dtsOut;

    @Parameter(required = false)
    protected Boolean sourceMap;

    @Parameter(required = false)
    protected String sourceRoot;

    /**
     * If present, overrides maven's project.compileSourceRoots
     */
    @Parameter(required = false)
    private List<String> compileSourceRootsOverride;

    @Parameter(required = false)
    protected Boolean verbose;

    @Parameter(required = false)
    protected Boolean veryVerbose;

    @Parameter(required = false)
    protected Boolean ignoreDefinitions;

    @Parameter(required = false)
    protected File candiesJsOut;

    @Parameter(required = false)
    protected String[] allowedDependencyScopes;

    @Parameter
    protected String[] includes;

    @Parameter
    protected String[] excludes;

    @Parameter(required = false)
    protected String encoding;

    @Parameter(required = false)
    protected Boolean noRootDirectories;

    @Parameter(required = false)
    protected Boolean enableAssertions;

    @Parameter(required = false)
    protected Boolean disableSinglePrecisionFloats;

    @Parameter(defaultValue = "${java.home}")
    protected File jdkHome;

    @Parameter(required = false)
    protected String extraSystemPath;

    @Parameter(required = false)
    protected ModuleResolution moduleResolution;

    @Parameter(defaultValue = "${localRepository}", required = true)
    protected ArtifactRepository localRepository;

    @Parameter(defaultValue = "${project.remoteArtifactRepositories}", required = true)
    protected List<ArtifactRepository> remoteRepositories;

    @Parameter(required = false)
    protected String factoryClassName;

    @Parameter(required = false)
    protected List<JSweetProblem> ignoredProblems;

    @Parameter(required = false)
    protected String javaCompilerExtraOptions;

    @Parameter(required = false)
    protected Boolean ignoreTypeScriptErrors;

    @Parameter(required = false)
    protected File header;

    @Parameter(required = false)
    protected File workingDir;

    @Parameter(required = false)
    protected Boolean usingJavaRuntime;

    @Component
    protected ArtifactFactory artifactFactory;

    @Component
    protected ArtifactResolver resolver;

    @Component
    protected ArtifactMetadataSource metadataSource;

    @Component
    private PluginDescriptor descriptor;

    @Component
    private RuntimeInformation runtime;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        logInfo("maven version: " + runtime.getMavenVersion());
    }

    private void logInfo(String content) {
        if (verbose != null && verbose || veryVerbose != null && veryVerbose) {
            getLog().info(content);
        }
    }

    protected SourceFile[] collectSourceFiles(MavenProject project) {

        logInfo("source includes: " + ArrayUtils.toString(includes));
        logInfo("source excludes: " + ArrayUtils.toString(excludes));

        List<String> sourcePaths = getCompileSourceRoots(project);
        logInfo("sources paths: " + sourcePaths);

        List<SourceFile> sources = new LinkedList<>();
        for (String sourcePath : sourcePaths) {
            scanForJavaFiles(sources, new File(sourcePath));
        }

        List<Resource> resources = project.getResources();
        logInfo("sources paths from resources: " + sourcePaths);

        for (Resource resource : resources) {
            String directory = resource.getDirectory();
            scanForJavaFiles(sources, new File(directory));
        }

        logInfo("sourceFiles=" + sources);

        return sources.toArray(new SourceFile[0]);
    }

    private void scanForJavaFiles(List<SourceFile> sources, File sourceDirectory) {
        if (!sourceDirectory.exists()) {
            getLog().debug(sourceDirectory.getAbsolutePath() + " is declared but doesn't exist");
            return;
        }

        DirectoryScanner dirScanner = new DirectoryScanner();
        dirScanner.setBasedir(sourceDirectory);
        dirScanner.setIncludes(includes);
        dirScanner.setExcludes(excludes);
        dirScanner.scan();

        for (String includedPath : dirScanner.getIncludedFiles()) {
            if (includedPath.endsWith(".java")) {
                sources.add(new SourceFile(new File(sourceDirectory, includedPath)));
            }
        }
    }

    protected JSweetTranspiler createJSweetTranspiler(MavenProject project) throws MojoExecutionException {

        try {

            List<File> dependenciesFiles = getCandiesJars();

            String classPath = dependenciesFiles.stream() //
                    .map(f -> f.getAbsolutePath()) //
                    .collect(joining(System.getProperty("path.separator")));

            logInfo("classpath from maven: " + classPath);

            File tsOutputDir = getTsOutDir();

            File jsOutDir = getJsOutDir();

            File declarationOutDir = getDeclarationsOutDir();

            boolean isTsserverEnabled = true;
            if (this.tsserver != null) {
                isTsserverEnabled = this.tsserver;
            }

            logInfo("jsOut: " + jsOutDir);
            logInfo("bundle: " + bundle);
            logInfo("tsOut: " + tsOutputDir);
            logInfo("tsOnly: " + tsOnly);
            logInfo("tsserver: " + isTsserverEnabled);
            logInfo("declarations: " + declaration);
            logInfo("ignoreDefinitions: " + ignoreDefinitions);
            logInfo("declarationOutDir: " + declarationOutDir);
            logInfo("candiesJsOutDir: " + candiesJsOut);
            logInfo("ecmaTargetVersion: " + targetVersion);
            logInfo("moduleKind: " + module);
            logInfo("sourceMap: " + sourceMap);
            logInfo("sourceRoot: " + sourceRoot);
            logInfo("compileSourceRootsOverride" + compileSourceRootsOverride);
            logInfo("verbose: " + verbose);
            logInfo("veryVerbose: " + veryVerbose);
            logInfo("jdkHome: " + jdkHome);
            logInfo("factoryClassName: " + factoryClassName);
            logInfo("ignoredProblems: " + ignoredProblems);
            logInfo("javaCompilerExtraOptions: " + javaCompilerExtraOptions);

            logInfo("extraSystemPath: " + extraSystemPath);
            if (isNotBlank(extraSystemPath)) {
                ProcessUtil.addExtraPath(extraSystemPath);
            }

            LogManager.getLogger("org.jsweet").setLevel(Level.WARN);

            if (verbose != null && verbose) {
                LogManager.getLogger("org.jsweet").setLevel(Level.DEBUG);
            }
            if (veryVerbose != null && veryVerbose) {
                LogManager.getLogger("org.jsweet").setLevel(Level.ALL);
            }

            JSweetFactory factory = createJSweetFactory(project, dependenciesFiles, classPath);

            if (workingDir != null && !workingDir.isAbsolute()) {
                workingDir = new File(getBaseDirectory(), workingDir.getPath());
            }

            JSweetTranspiler transpiler = new JSweetTranspiler(getBaseDirectory(), null, factory, workingDir,
                    tsOutputDir, jsOutDir, candiesJsOut, classPath);
            transpiler.setTscWatchMode(false);
            if (targetVersion != null) {
                transpiler.setEcmaTargetVersion(targetVersion);
            }
            if (module != null) {
                transpiler.setModuleKind(module);
            }
            if (bundle != null) {
                transpiler.setBundle(bundle);
            }

            transpiler.setUseTsserver(isTsserverEnabled);

            if (sourceMap != null) {
                transpiler.setGenerateSourceMaps(sourceMap);
            }
            File sourceRoot = getSourceRoot();
            if (sourceRoot != null) {
                transpiler.setSourceRoot(sourceRoot);
            }
            if (encoding != null) {
                transpiler.setEncoding(encoding);
            }
            if (noRootDirectories != null) {
                transpiler.setNoRootDirectories(noRootDirectories);
            }
            if (enableAssertions != null) {
                transpiler.setIgnoreAssertions(!enableAssertions);
            }
            if (declaration != null) {
                transpiler.setGenerateDeclarations(declaration);
            }
            if (declarationOutDir != null) {
                transpiler.setDeclarationsOutputDir(declarationOutDir);
            }
            if (ignoreDefinitions != null) {
                transpiler.setGenerateDefinitions(!ignoreDefinitions);
            }
            if (tsOnly != null) {
                transpiler.setGenerateJsFiles(!tsOnly);
            }
            if (ignoreTypeScriptErrors != null) {
                transpiler.setIgnoreTypeScriptErrors(ignoreTypeScriptErrors);
            }
            if (header != null) {
                transpiler.setHeaderFile(header);
            }
            if (disableSinglePrecisionFloats != null) {
                transpiler.setDisableSinglePrecisionFloats(disableSinglePrecisionFloats);
            }
            if (moduleResolution != null) {
                transpiler.setModuleResolution(moduleResolution);
            }
            if (tsOutputDir != null) {
                transpiler.setTsOutputDir(tsOutputDir);
            }
            if (jsOutDir != null) {
                transpiler.setJsOutputDir(jsOutDir);
            }
            if (usingJavaRuntime != null) {
                transpiler.setUsingJavaRuntime(usingJavaRuntime);
            }
            if (javaCompilerExtraOptions != null) {
                transpiler.setJavaCompilerExtraOptions(javaCompilerExtraOptions.split(","));
            }

            return transpiler;

        } catch (Exception e) {
            getLog().error("failed to create transpiler", e);
            throw new MojoExecutionException("failed to create transpiler", e);
        }
    }

    private JSweetFactory createJSweetFactory(MavenProject project, List<File> dependenciesFiles, String classPath)
            throws DependencyResolutionRequiredException, MalformedURLException, IOException, MojoExecutionException {
        JSweetFactory factory = null;

        if (factoryClassName != null) {
            ClassRealm realm = descriptor.getClassRealm();

            List<String> classpathElements = project.getRuntimeClasspathElements();
            classpathElements.addAll(project.getCompileClasspathElements());
            for (String element : classpathElements) {
                File elementFile = new File(element);
                realm.addURL(elementFile.toURI().toURL());
            }
            for (File dependencyFile : dependenciesFiles) {
                realm.addURL(dependencyFile.toURI().toURL());
            }

            try {
                Class<?> c = realm.loadClass(factoryClassName);
                factory = (JSweetFactory) c.newInstance();

            } catch (Exception e) {
                logInfo("factory not found using ClassRealm.loadClass");
                ClassLoader classLoader = null;
                try {
                    classLoader = new URLClassLoader(realm.getURLs(), Thread.currentThread().getContextClassLoader());
                    factory = (JSweetFactory) classLoader.loadClass(factoryClassName).newInstance();
                } catch (Exception e2) {
                    logInfo("factory not found using Thread.currentThread().getContextClassLoader().loadClass");
                    try {
                        // try forName just in case
                        factory = (JSweetFactory) Class.forName(factoryClassName).newInstance();
                    } catch (Exception e3) {
                        logInfo("factory not found using Class.forName");

                        String relativePath = factoryClassName.replace(".", File.separator) + ".java";

                        List<String> sourcePaths = getCompileSourceRoots(project);
                        for (String sourcePath : sourcePaths) {
                            File factorySourceFile = new File(sourcePath, relativePath);
                            try {
                                factory = compileJSweetFactory(factorySourceFile, classLoader, classPath);
                            } catch (Exception compileFactoryException) {
                                getLog().error("cannot compile factory class from source file: " + factorySourceFile,
                                        compileFactoryException);
                            }
                            if (factory != null) {
                                break;
                            }
                        }

                        if (factory == null) {
                            throw new MojoExecutionException("cannot find or instantiate factory class: "
                                    + factoryClassName
                                    + " (make sure the class is in the plugin's classpath and that it defines an empty public constructor)",
                                    e3);
                        }
                    }
                }
            }

            logInfo("JSweet factory class " + factoryClassName + ": LOADED");
        }

        if (factory == null) {
            factory = new JSweetFactory();
        }
        return factory;
    }

    private JSweetFactory compileJSweetFactory(File factorySourceFile, ClassLoader classLoader, String classPath)
            throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {

        logInfo("factory source path: " + factorySourceFile.getCanonicalPath() + " - exists="
                + factorySourceFile.exists());
        JSweetFactory factory = null;
        if (factorySourceFile.exists()) {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.getDefault(),
                    Charset.forName("UTF-8"));

            List<File> javaFiles = new ArrayList<>();
            javaFiles.add(factorySourceFile);

            File factoryModuleSourceFile = new File(factorySourceFile.getParentFile(), "module-info.java");
            if (factoryModuleSourceFile.exists()) {
                javaFiles.add(factoryModuleSourceFile);
            }

            logInfo("factory files: " + javaFiles);
            Iterable<? extends JavaFileObject> javaFileObjectsIterable = fileManager
                    .getJavaFileObjectsFromFiles(javaFiles);
            List<JavaFileObject> javaFileObjects = new ArrayList<>();
            javaFileObjectsIterable.forEach(javaFileObjects::add);

            List<String> options = new ArrayList<>();
            options.add("-Xlint:path");
            options.add("-cp");
            options.add(classPath);
            options.add("--module-path");
            options.add(classPath);
            options.add("-encoding");
            options.add("UTF-8");

            String compiledClassDirectoryPath = getBaseDirectory().getCanonicalPath() + "/target/classes";
            options.add("-d");
            options.add(compiledClassDirectoryPath);

            logInfo("creating JavaCompiler task with options: " + options);
            JavacTask task = (JavacTask) compiler.getTask(null, fileManager, null, options, null, javaFileObjects);
            if (!task.call()) {
                throw new RuntimeException(factorySourceFile + " compilation ended with errors");
            }

            logInfo("factory COMPILATION SUCCEEDED! (to " + compiledClassDirectoryPath + ")");
            URL compiledClassDirectoryURL = new File(compiledClassDirectoryPath).toURI().toURL();
            classLoader = new URLClassLoader(new URL[] { compiledClassDirectoryURL }, classLoader);
            logInfo("loading class " + factoryClassName + " looking in " + compiledClassDirectoryURL);

            factory = (JSweetFactory) classLoader.loadClass(factoryClassName).newInstance();
        }

        return factory;
    }

    protected File getDeclarationsOutDir() throws IOException {
        File declarationOutDir = null;
        if (isNotBlank(this.dtsOut)) {
            File dtsOutFile = new File(this.dtsOut);
            if (!dtsOutFile.isAbsolute()) {
                dtsOutFile = new File(getBaseDirectory(), this.dtsOut);
            }
            return dtsOutFile.getCanonicalFile();
        }
        return declarationOutDir;
    }

    protected File getSourceRoot() throws IOException {
        File sourceRoot = null;
        if (isNotBlank(this.sourceRoot)) {
            File sourceRootFile = new File(this.sourceRoot);
            if (!sourceRootFile.isAbsolute()) {
                sourceRootFile = new File(getBaseDirectory(), this.sourceRoot);
            }
            return sourceRootFile.getCanonicalFile();
        }
        return sourceRoot;
    }

    protected File getJsOutDir() throws IOException {
        if (isNotBlank(this.outDir)) {
            File jsOutFile = new File(this.outDir);
            if (!jsOutFile.isAbsolute()) {
                jsOutFile = new File(getBaseDirectory(), this.outDir);
            }
            return jsOutFile.getCanonicalFile();
        } else {
            return null;
        }
    }

    protected File getBaseDirectory() throws IOException {
        return getMavenProject().getBasedir().getAbsoluteFile();
    }

    protected File getTsOutDir() throws IOException {
        if (isNotBlank(this.tsOut)) {
            File tsOutFile = new File(this.tsOut);
            if (!tsOutFile.isAbsolute()) {
                tsOutFile = new File(getBaseDirectory(), this.tsOut);
            }
            return tsOutFile.getCanonicalFile();

        } else {
            return null;
        }
    }

    protected List<File> getCandiesJars() throws ArtifactResolutionException, ArtifactNotFoundException {

        MavenProject project = getMavenProject();

        List<Dependency> dependencies = project.getDependencies();
        logInfo("dependencies=" + dependencies);

        Set<String> allowedScopes = new HashSet<>();
        if (allowedDependencyScopes != null) {
            allowedScopes.addAll(Set.of(allowedDependencyScopes));
        } else {
            allowedScopes.add(Artifact.SCOPE_COMPILE);
        }

        // add artifacts of declared dependencies
        List<Artifact> directDependencies = new LinkedList<>();
        for (Dependency dependency : dependencies) {
            if (!dependency.getType().equals("jar")) {
                getLog().warn("dependency of type other than jar excluded from candies detection: " + dependency);
                continue;
            }
            if (!allowedScopes.contains(dependency.getScope())) {
                getLog().warn("dependency with scope '" + dependency.getScope() + "' excluded from candies detection: " + dependency);
                continue;
            }
            Artifact mavenArtifact = artifactFactory.createArtifact(dependency.getGroupId(), dependency.getArtifactId(),
                    dependency.getVersion(), Artifact.SCOPE_COMPILE, "jar");

            logInfo("candies detection: add project dependency " + dependency + " => " + mavenArtifact);

            directDependencies.add(mavenArtifact);
        }

        // lookup for transitive dependencies
        ArtifactResolutionResult dependenciesResolutionResult = resolver.resolveTransitively( //
                new HashSet<>(directDependencies), //
                project.getArtifact(), //
                remoteRepositories, //
                localRepository, //
                metadataSource);

        Set<ResolutionNode> allDependenciesArtifacts = dependenciesResolutionResult.getArtifactResolutionNodes();
        logInfo("all candies artifacts: " + allDependenciesArtifacts);

        // add dependencies files
        List<File> dependenciesFiles = new LinkedList<>();
        for (ResolutionNode depResult : allDependenciesArtifacts) {
            dependenciesFiles.add(depResult.getArtifact().getFile());
        }

        logInfo("candies jars: " + dependenciesFiles);

        return dependenciesFiles;
    }

    protected MavenProject getMavenProject() {
        Map<?, ?> ctx = getPluginContext();
        MavenProject project = (MavenProject) ctx.get("project");
        return project;
    }

    protected List<String> getCompileSourceRoots(MavenProject project) {
        if (compileSourceRootsOverride == null || compileSourceRootsOverride.isEmpty()) {
            return project.getCompileSourceRoots();
        }
        compileSourceRootsOverride = compileSourceRootsOverride.stream().filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
        if (compileSourceRootsOverride.isEmpty()) {
            getLog().warn("compileSourceRootsOverride has blank compileSourceRoot " + "element/s. Using defaults: "
                    + project.getCompileSourceRoots());
            return project.getCompileSourceRoots();
        }
        logInfo("Overriding compileSourceRoots with: " + compileSourceRootsOverride);
        return compileSourceRootsOverride;
    }

    private class JSweetMavenPluginTranspilationHandler extends ErrorCountTranspilationHandler {

        class Error {
            final JSweetProblem problem;
            final SourcePosition sourcePosition;
            final String message;

            public Error(JSweetProblem problem, SourcePosition sourcePosition, String message) {
                this.problem = problem;
                this.sourcePosition = sourcePosition;
                this.message = message;
            }

            @Override
            public String toString() {
                String userFriendlyValue = "";
                if (sourcePosition != null) {
                    userFriendlyValue += sourcePosition.toString();
                }
                userFriendlyValue += message;
                return userFriendlyValue;
            }
        }

        private final List<Error> errors = new ArrayList<>();

        public List<Error> getErrors() {
            return Collections.unmodifiableList(errors);
        }

        public JSweetMavenPluginTranspilationHandler() {
            super(new ConsoleTranspilationHandler());
        }

        @Override
        public void report(JSweetProblem problem, SourcePosition sourcePosition, String message) {
            if (ignoredProblems != null && ignoredProblems.contains(problem)) {
                return;
            }
            super.report(problem, sourcePosition, message);

            errors.add(new Error(problem, sourcePosition, message));
        }
    }

    protected void transpile(MavenProject project, JSweetTranspiler transpiler) throws MojoExecutionException {
        try {
            JSweetMavenPluginTranspilationHandler transpilationHandler = new JSweetMavenPluginTranspilationHandler();
            try {

                SourceFile[] sources = collectSourceFiles(project);

                transpiler.transpile(transpilationHandler, sources);

            } catch (NoClassDefFoundError error) {
                error.printStackTrace();
                transpilationHandler.report(JSweetProblem.JAVA_COMPILER_NOT_FOUND, null,
                        JSweetProblem.JAVA_COMPILER_NOT_FOUND.getMessage());
            }

            int errorCount = transpilationHandler.getErrorCount();

            if (errorCount > 0) {

                StringBuilder errorsSummaryBuilder = new StringBuilder(
                        "\n\n=========================================\nTRANSPILATION ERRORS SUMMARY:\n");
                for (JSweetMavenPluginTranspilationHandler.Error error : transpilationHandler.getErrors()) {
                    errorsSummaryBuilder.append("* " + error + "\n");
                }
                errorsSummaryBuilder.append("\n\n=========================================");

                throw new MojoFailureException("transpilation failed with " + errorCount + " error(s) and "
                        + transpilationHandler.getWarningCount() + " warning(s)" + errorsSummaryBuilder);
            } else {

                if (transpilationHandler.getWarningCount() > 0) {
                    getLog().info(
                            "transpilation completed with " + transpilationHandler.getWarningCount() + " warning(s)");
                } else {
                    getLog().info("transpilation successfully completed with no errors and no warnings");
                }

            }

        } catch (Exception e) {
            getLog().error("transpilation failed", e);
            throw new MojoExecutionException("transpilation failed", e);
        }
    }
}

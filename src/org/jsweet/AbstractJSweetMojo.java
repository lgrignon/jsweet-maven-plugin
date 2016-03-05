package org.jsweet;

import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.ResolutionNode;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.jsweet.transpiler.EcmaScriptComplianceLevel;
import org.jsweet.transpiler.JSweetProblem;
import org.jsweet.transpiler.JSweetTranspiler;
import org.jsweet.transpiler.ModuleKind;
import org.jsweet.transpiler.SourceFile;
import org.jsweet.transpiler.util.ConsoleTranspilationHandler;
import org.jsweet.transpiler.util.ErrorCountTranspilationHandler;

import static org.jsweet.Util.getTranspilerWorkingDirectory;

public abstract class AbstractJSweetMojo extends AbstractMojo {

	@Parameter(alias = "target", defaultValue = "ES3", required = true, readonly = true)
	protected EcmaScriptComplianceLevel targetVersion;

	@Parameter(defaultValue = "none", required = false, readonly = true)
	protected ModuleKind module;

	@Parameter(readonly = true)
	protected String outDir;

	@Parameter(readonly = true)
	protected String tsOut;

	@Parameter(required = false, readonly = true)
	protected boolean bundle;

	@Parameter(defaultValue = "false", required = false, readonly = true)
	protected boolean declaration;

	@Parameter(readonly = true)
	protected String dtsOut;

	@Parameter(defaultValue = "false", required = false, readonly = true)
	protected boolean sourceMap;

	@Parameter(defaultValue = "false", required = false, readonly = true)
	protected boolean verbose;

	@Parameter(required = false, readonly = true)
	protected String bundlesDirectory;

	@Parameter(required = false, readonly = true)
	protected File candiesJsOut;

	@Parameter
	protected String[] includes;

	@Parameter
	protected String[] excludes;

	@Parameter(defaultValue = "UTF-8", required = false)
	protected String encoding;

	@Parameter(defaultValue = "false", required = false)
	protected boolean noRootDirectories;

	@Parameter(defaultValue = "false", required = false)
	protected boolean enableAssertions;

	@Parameter(defaultValue = "${java.home}")
	protected File jdkHome;

	@Parameter(defaultValue = "${localRepository}", required = true, readonly = true)
	protected ArtifactRepository localRepository;

	@Parameter(defaultValue = "${project.remoteArtifactRepositories}", required = true, readonly = true)
	protected List<ArtifactRepository> remoteRepositories;

	@Component
	protected ArtifactFactory artifactFactory;

	@Component
	protected ArtifactResolver resolver;

	@Component
	protected ArtifactMetadataSource metadataSource;

	private void logInfo(String content) {
		if (verbose) {
			getLog().info(content);
		}
	}

	protected SourceFile[] collectSourceFiles(MavenProject project) {

		@SuppressWarnings("unchecked")
		List<String> sourcePaths = project.getCompileSourceRoots();

		logInfo("source includes: " + ArrayUtils.toString(includes));
		logInfo("source excludes: " + ArrayUtils.toString(excludes));

		logInfo("sources paths: " + sourcePaths);

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

		logInfo("sourceFiles=" + sources);

		return sources.toArray(new SourceFile[0]);
	}

	protected JSweetTranspiler createJSweetTranspiler(MavenProject project) throws MojoExecutionException {

		try {

			List<File> dependenciesFiles = getCandiesJars(project);

			String classPath = dependenciesFiles.stream() //
					.map(f -> f.getAbsolutePath()) //
					.collect(joining(System.getProperty("path.separator")));

			logInfo("classpath from maven: " + classPath);

			File tsOutputDir = getTsOutDir();

			File jsOutDir = getJsOutDir();

			File declarationOutDir = getDeclarationsOutDir();

			logInfo("jsOut: " + jsOutDir);
			logInfo("bundle: " + bundle);
			if (bundlesDirectory != null) {
				logInfo("bundlesDirectory: " + bundlesDirectory);
			}
			logInfo("tsOut: " + tsOutputDir);
			logInfo("declarations: " + declaration);
			logInfo("declarationOutDir: " + declarationOutDir);
			logInfo("candiesJsOutDir: " + candiesJsOut);
			logInfo("ecmaTargetVersion: " + targetVersion);
			logInfo("moduleKind: " + module);
			logInfo("sourceMap: " + sourceMap);
			logInfo("verbose: " + verbose);
			logInfo("jdkHome: " + jdkHome);

			JSweetConfig.initClassPath(jdkHome.getAbsolutePath());

			if (verbose) {
				LogManager.getLogger("org.jsweet").setLevel(Level.ALL);
			}

			JSweetTranspiler transpiler = new JSweetTranspiler(getTranspilerWorkingDirectory(project), tsOutputDir,
					jsOutDir, candiesJsOut, classPath);
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

	protected File getDeclarationsOutDir() throws IOException {
		File declarationOutDir = null;
		if (isNotBlank(this.dtsOut)) {
			declarationOutDir = new File(this.dtsOut).getCanonicalFile();
		}
		return declarationOutDir;
	}

	protected File getJsOutDir() throws IOException {
		File jsOutDir = null;
		String jsOutputDirPath = "js";
		if (isNotBlank(this.outDir)) {
			jsOutputDirPath = new File(this.outDir).getCanonicalPath();
		}
		jsOutDir = new File(jsOutputDirPath);
		return jsOutDir;
	}

	protected File getTsOutDir() throws IOException {
		String tsOutputDirPath = ".ts";
		if (isNotBlank(this.tsOut)) {
			tsOutputDirPath = new File(this.tsOut).getCanonicalPath();
		}
		File tsOutputDir = new File(tsOutputDirPath);
		return tsOutputDir;
	}

	protected List<File> getCandiesJars(MavenProject project)
			throws ArtifactResolutionException, ArtifactNotFoundException {

		@SuppressWarnings("unchecked")
		List<Dependency> dependencies = project.getDependencies();
		logInfo("dependencies=" + dependencies);

		// add artifacts of declared dependencies
		List<Artifact> directDependencies = new LinkedList<>();
		for (Dependency dependency : dependencies) {
			Artifact mavenArtifact = artifactFactory.createArtifact(dependency.getGroupId(), dependency.getArtifactId(),
					dependency.getVersion(), Artifact.SCOPE_COMPILE, "jar");

			logInfo("add direct candy dependency: " + dependency + "=" + mavenArtifact);

			directDependencies.add(mavenArtifact);
		}

		// lookup for transitive dependencies
		ArtifactResolutionResult dependenciesResolutionResult = resolver.resolveTransitively( //
				new HashSet<>(directDependencies), //
				project.getArtifact(), //
				remoteRepositories, //
				localRepository, //
				metadataSource);

		@SuppressWarnings("unchecked")
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

	protected void transpile(MavenProject project, JSweetTranspiler transpiler) throws MojoExecutionException {
		try {
			ErrorCountTranspilationHandler transpilationHandler = new ErrorCountTranspilationHandler(
					new ConsoleTranspilationHandler());
			try {
	
	
				SourceFile[] sources = collectSourceFiles(project);
	
				transpiler.transpile(transpilationHandler, sources);
	
			} catch (NoClassDefFoundError error) {
				transpilationHandler.report(JSweetProblem.JAVA_COMPILER_NOT_FOUND, null,
						JSweetProblem.JAVA_COMPILER_NOT_FOUND.getMessage());
			}
	
			int errorCount = transpilationHandler.getErrorCount();
	
			if (errorCount > 0) {
				throw new MojoFailureException("transpilation failed with " + errorCount + " error(s) and "
						+ transpilationHandler.getWarningCount() + " warning(s)");
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
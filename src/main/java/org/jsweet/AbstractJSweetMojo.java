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
import org.jsweet.transpiler.JSweetFactory;
import org.jsweet.transpiler.JSweetProblem;
import org.jsweet.transpiler.JSweetTranspiler;
import org.jsweet.transpiler.ModuleKind;
import org.jsweet.transpiler.SourceFile;
import org.jsweet.transpiler.util.ConsoleTranspilationHandler;
import org.jsweet.transpiler.util.ErrorCountTranspilationHandler;

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

	@Parameter(required = false)
	protected Boolean verbose;

	@Parameter(required = false)
	protected Boolean ignoreDefinitions;

	@Parameter(required = false)
	protected File candiesJsOut;

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

	@Parameter(defaultValue = "${localRepository}", required = true)
	protected ArtifactRepository localRepository;

	@Parameter(defaultValue = "${project.remoteArtifactRepositories}", required = true)
	protected List<ArtifactRepository> remoteRepositories;

	@Parameter(required = false)
	protected String factoryClassName;

	@Parameter(required = false)
	protected Boolean ignoreTypeScriptErrors;

	@Parameter(required = false)
	protected File header;

	@Parameter(required = false)
	protected File workingDir;

	@Component
	protected ArtifactFactory artifactFactory;

	@Component
	protected ArtifactResolver resolver;

	@Component
	protected ArtifactMetadataSource metadataSource;

	private void logInfo(String content) {
		if (verbose != null && verbose) {
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
			logInfo("tsOut: " + tsOutputDir);
			logInfo("tsOnly: " + tsOnly);
			logInfo("declarations: " + declaration);
			logInfo("ignoreDefinitions: " + ignoreDefinitions);
			logInfo("declarationOutDir: " + declarationOutDir);
			logInfo("candiesJsOutDir: " + candiesJsOut);
			logInfo("ecmaTargetVersion: " + targetVersion);
			logInfo("moduleKind: " + module);
			logInfo("sourceMap: " + sourceMap);
			logInfo("sourceRoot: " + sourceRoot);
			logInfo("verbose: " + verbose);
			logInfo("jdkHome: " + jdkHome);
			logInfo("factoryClassName: " + factoryClassName);

			JSweetConfig.initClassPath(jdkHome.getAbsolutePath());

			if (verbose != null && verbose) {
				LogManager.getLogger("org.jsweet").setLevel(Level.ALL);
			}

			JSweetFactory factory = null;

			if (factoryClassName != null) {
				try {
					factory = (JSweetFactory) Thread.currentThread().getContextClassLoader().loadClass(factoryClassName)
							.newInstance();
				} catch (Exception e) {
					try {
						// try forName just in case
						factory = (JSweetFactory) Class.forName(factoryClassName).newInstance();
					} catch (Exception e2) {
						throw new MojoExecutionException(
								"cannot find or instantiate factory class: " + factoryClassName
										+ " (make sure the class is in the plugin's classpath and that it defines an empty public constructor)",
								e2);
					}
				}
			}

			if (factory == null) {
				factory = new JSweetFactory();
			}

			JSweetTranspiler transpiler = new JSweetTranspiler(factory, workingDir, tsOutputDir, jsOutDir, candiesJsOut,
					classPath);
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
			if (sourceMap != null) {
				transpiler.setPreserveSourceLineNumbers(sourceMap);
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
			if (tsOutputDir != null) {
				transpiler.setTsOutputDir(tsOutputDir);
			}
			if (jsOutDir != null) {
				transpiler.setJsOutputDir(jsOutDir);
			}

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

	protected File getSourceRoot() throws IOException {
		File sourceRoot = null;
		if (isNotBlank(this.sourceRoot)) {
			sourceRoot = new File(this.sourceRoot);
		}
		return sourceRoot;
	}

	protected File getJsOutDir() throws IOException {
		if (isNotBlank(this.outDir)) {
			String jsOutputDirPath = new File(this.outDir).getCanonicalPath();
			return new File(jsOutputDirPath);
		} else {
			return null;
		}
	}

	protected File getTsOutDir() throws IOException {
		if (isNotBlank(this.tsOut)) {
			String tsOutputDirPath = new File(this.tsOut).getCanonicalPath();
			return new File(tsOutputDirPath);

		} else {
			return null;
		}
	}

	protected List<File> getCandiesJars(MavenProject project)
			throws ArtifactResolutionException, ArtifactNotFoundException {

		@SuppressWarnings("unchecked")
		List<Dependency> dependencies = project.getDependencies();
		logInfo("dependencies=" + dependencies);

		// add artifacts of declared dependencies
		List<Artifact> directDependencies = new LinkedList<>();
		for (Dependency dependency : dependencies) {
			if (!dependency.getType().equals("jar")) {
				getLog().warn("dependency type not-jar excluded from candies detection: " + dependency);
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
				error.printStackTrace();
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
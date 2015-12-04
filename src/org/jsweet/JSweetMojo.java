/* 
 * Copyright (C) 2015 Louis Grignon <louis.grignon@gmail.com>
 * 
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
package org.jsweet;

import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.jsweet.Util.getTranspilerWorkingDirectory;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.jsweet.transpiler.EcmaScriptComplianceLevel;
import org.jsweet.transpiler.JSweetProblem;
import org.jsweet.transpiler.JSweetTranspiler;
import org.jsweet.transpiler.ModuleKind;
import org.jsweet.transpiler.SourceFile;
import org.jsweet.transpiler.TranspilationHandler;
import org.jsweet.transpiler.TranspilationHandler.SourcePosition;

/**
 * JSweet transpiler as a maven plugin
 * 
 * @author Louis Grignon
 */
@Mojo(name = "jsweet", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class JSweetMojo extends AbstractMojo {

	@Parameter(alias = "target", defaultValue = "ES3", required = true, readonly = true)
	public EcmaScriptComplianceLevel ecmaTargetVersion;

	@Parameter(defaultValue = "none", required = false, readonly = true)
	public ModuleKind module;

	@Parameter(readonly = true)
	public String outDir;

	@Parameter(readonly = true)
	public String tsOut;

	@Parameter(required = false, readonly = true)
	public boolean bundle;

	@Parameter(required = false, readonly = true)
	public boolean debug;
	
	@Parameter(required = false, readonly = true)
	public String bundlesDirectory;

	@Parameter
	public String[] includes;

	@Parameter
	public String[] excludes;

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

	private List<Alert> alerts;

	public void execute() throws MojoFailureException, MojoExecutionException {
		alerts = new LinkedList<>();

		getLog().info("executing jsweet transpilation");
		Map<?, ?> ctx = getPluginContext();

		MavenProject project = (MavenProject) ctx.get("project");
		// PluginDescriptor pluginDescriptor = (PluginDescriptor)
		// ctx.get("pluginDescriptor");

		createJSweetTranspiler(project);
		SourceFile[] sources = collectSourceFiles(project);

		try {
			transpiler.transpile(new JSweetTranspilationHandler(), sources);
		} catch (Exception e) {
			getLog().error("transpilation failed", e);
			throw new MojoExecutionException("transpilation failed", e);
		}

		if (alerts.size() > 0) {
			throw new MojoFailureException("problems encountered: " + alerts);
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
		// TODO : what is dynamic compile source? => NoSuchMethod if uncommented
		// sourcePaths.addAll(project.getDynamicCompileSourceRoots());

		getLog().info("sourceFiles=" + sources);

		return sources.toArray(new SourceFile[0]);
	}

	private JSweetTranspiler createJSweetTranspiler(MavenProject project) throws MojoExecutionException {
		try {

			List<File> dependenciesFiles = getCandiesJars(project);
			String classPath = dependenciesFiles.stream() //
					.map(f -> f.getAbsolutePath()) //
					.collect(joining(System.getProperty("path.separator")));

			File workingDir = getTranspilerWorkingDirectory(project);
			String tsOutputDirPath = workingDir.getCanonicalPath() + File.separator + "ts";
			if (isNotBlank(this.tsOut)) {
				tsOutputDirPath = new File(this.tsOut).getCanonicalPath();
			}

			File jsOutDir = null;
			String jsOutputDirPath = workingDir.getCanonicalPath() + File.separator + "js";
			if (isNotBlank(this.outDir)) {
				jsOutputDirPath = new File(this.outDir).getCanonicalPath();
			}
			jsOutDir = new File(jsOutputDirPath);

			getLog().info("> jsOut=" + jsOutDir);
			getLog().info("> bundle=" + bundle);
			if (bundlesDirectory != null) {
				getLog().info("> bundlesDirectory=" + bundlesDirectory);
			}
			getLog().info("> tsOut=" + tsOutputDirPath);
			getLog().info("> ecmaTargetVersion=" + ecmaTargetVersion);
			getLog().info("> moduleKind=" + module);
			getLog().info("> debug=" + true);

			transpiler = new JSweetTranspiler(workingDir, new File(tsOutputDirPath), jsOutDir, classPath);
			transpiler.setPreserveSourceLineNumbers(true);
			transpiler.setTscWatchMode(false);
			transpiler.setEcmaTargetVersion(ecmaTargetVersion);
			transpiler.setModuleKind(module);
			transpiler.setBundle(bundle);
			transpiler.setBundlesDirectory(StringUtils.isBlank(bundlesDirectory) ? null : new File(bundlesDirectory));
			transpiler.setPreserveSourceLineNumbers(debug);
			
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

		// add artifacts of declared dependencies
		List<Artifact> directDependencies = new LinkedList<>();
		for (Dependency dependency : dependencies) {

			if (dependency.getGroupId().startsWith(JSweetConfig.MAVEN_CANDIES_GROUP)) {

				Artifact mavenArtifact = artifactFactory.createArtifact(dependency.getGroupId(),
						dependency.getArtifactId(), dependency.getVersion(), Artifact.SCOPE_COMPILE, "jar");

				getLog().info("add direct candy dependency: " + dependency + "=" + mavenArtifact);

				directDependencies.add(mavenArtifact);
			}
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
		getLog().info("all candies artifacts: " + allDependenciesArtifacts);

		// add dependencies files
		List<File> dependenciesFiles = new LinkedList<>();
		for (ResolutionNode depResult : allDependenciesArtifacts) {
			dependenciesFiles.add(depResult.getArtifact().getFile());
		}

		getLog().info("candies jars: " + dependenciesFiles);

		return dependenciesFiles;
	}

	private class Alert {
		JSweetProblem problem;
		SourcePosition sourcePosition;
		String message;

		private Alert(JSweetProblem problem, SourcePosition sourcePosition, String message) {
			this.problem = problem;
			this.sourcePosition = sourcePosition;
			this.message = message;
		}

		@Override
		public String toString() {
			return problem + ": " + message + " in " + sourcePosition;
		}
	}

	private class JSweetTranspilationHandler implements TranspilationHandler {

		@Override
		public void report(JSweetProblem problem, SourcePosition sourcePosition, String message) {
			alerts.add(new Alert(problem, sourcePosition, message));
		}
		
		@Override
		public void reportSilentError() {
		}

		@Override
		public void onCompleted(JSweetTranspiler transpiler, boolean fullPass, SourceFile[] files) {

		}
	}
}

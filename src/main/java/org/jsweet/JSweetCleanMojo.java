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

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

/**
 * Maven mojo to clean JSweet working directory
 * 
 * @author Louis Grignon
 */
@Mojo(name = "clean", defaultPhase = LifecyclePhase.CLEAN)
public class JSweetCleanMojo extends AbstractJSweetMojo {

	public void execute() throws MojoFailureException, MojoExecutionException {
		super.execute();
		
		getLog().info("cleaning jsweet working directory");
		try {
			MavenProject project = getMavenProject();

			File tsOutDir = getTsOutDir();
			FileUtils.deleteQuietly(tsOutDir);

			File jsOutDir = getJsOutDir();
			FileUtils.deleteQuietly(jsOutDir);

			File declarationsOutDir = getDeclarationsOutDir();
			if (declarationsOutDir != null) {
				FileUtils.deleteQuietly(declarationsOutDir);
			}

			if (candiesJsOut != null) {
				FileUtils.deleteQuietly(candiesJsOut);
			}

			FileUtils.deleteQuietly(workingDir == null ? Util.getTranspilerWorkingDirectory(project) : workingDir);
		} catch (Exception e) {
			getLog().error("transpilation failed", e);
			throw new MojoExecutionException("transpilation failed", e);
		}
	}
}

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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.jsweet.transpiler.JSweetTranspiler;

/**
 * JSweet transpiler as a maven plugin
 *
 * @author Louis Grignon
 */
@Mojo(name = "jsweet", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class JSweetMojo extends AbstractJSweetMojo {

	public void execute() throws MojoFailureException, MojoExecutionException {
		super.execute();
		
		getLog().info("JSweet transpiler version " + JSweetConfig.getVersionNumber() + " (build date: "
				+ JSweetConfig.getBuildDate() + ")");

		MavenProject project = getMavenProject();

		JSweetTranspiler transpiler = createJSweetTranspiler(project);

		transpile(project, transpiler);
	}
}

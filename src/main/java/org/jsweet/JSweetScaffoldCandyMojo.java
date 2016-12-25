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

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jsweet.input.typescriptdef.TypescriptDef2Java;

/**
 * Maven mojo to generate candy sources
 * 
 * @author Louis Grignon
 */
@Mojo(name = "candy-generate-sources")
public class JSweetScaffoldCandyMojo extends AbstractMojo {

	@Parameter(required = true, property = "candy.scaffold.outDir")
	protected File scaffoldOutDir;

	@Parameter(required = true, property = "candy.scaffold.candyName")
	protected String scaffoldCandyName;

	@Parameter(defaultValue = "1", property = "candy.scaffold.candyVersion")
	protected String scaffoldCandyVersion;

	@Parameter(required = true, property = "candy.scaffold.tsFiles")
	protected String[] scaffoldTsFiles;

	@Parameter(property = "candy.scaffold.tsDependencyFiles")
	protected String[] scaffoldTsDependencies;

	public void execute() throws MojoFailureException, MojoExecutionException {
		try {
			getLog().info("scaffolding candy: \n" //
					+ "* candyName: " + scaffoldCandyName + "\n" //
					+ "* candyVersion: " + scaffoldCandyVersion + "\n" //
					+ "* tsFiles: " + Arrays.toString(scaffoldTsFiles) + "\n" //
					+ "* tsDependencies: " + Arrays.toString(scaffoldTsDependencies) + "\n" //
					+ " to out: " + scaffoldOutDir);

			List<File> tsFiles = asList(scaffoldTsFiles).stream().map(File::new).collect(toList());
			List<File> tsDependencies = asList(scaffoldTsDependencies).stream().map(File::new).collect(toList());

			TypescriptDef2Java.translate( //
					tsFiles, //
					tsDependencies, //
					scaffoldOutDir, //
					null, //
					false);
			
			getLog().info("**************************************************************");
			getLog().info("candy " + scaffoldCandyName + " successfully generated to " + scaffoldOutDir);
			getLog().info("**************************************************************");
		} catch (Throwable e) {
			getLog().error("generation failed", e);
			throw new MojoExecutionException("generation failed", e);
		}
	}
}

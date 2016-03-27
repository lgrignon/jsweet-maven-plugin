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

import org.apache.maven.project.MavenProject;
import org.jsweet.transpiler.JSweetTranspiler;

import java.io.File;

/**
 * General utilities without a long life expectation
 * @author Louis Grignon
 *
 */
public class Util {
	public static File getTranspilerWorkingDirectory(MavenProject project) {
		return new File(JSweetTranspiler.TMP_WORKING_DIR_NAME);
	}
}

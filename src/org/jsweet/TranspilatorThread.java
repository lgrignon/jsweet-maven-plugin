package org.jsweet;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.jsweet.transpiler.JSweetTranspiler;

/*
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

/**
 * @author EPOTH -/- ponthiaux.e@sfeir.com -/- ponthiaux.eric@gmail.com
 */

public class TranspilatorThread extends TickThread {

    private JSweetTranspiler transpiler;

    public TranspilatorThread(AbstractJSweetMojo mojo) {

        super(mojo);

        setPriority(Thread.MAX_PRIORITY);

    }

    public void setTranspiler(JSweetTranspiler transpiler) {

        this.transpiler = transpiler;

    }

    public void onRun() {

        getLog().info("Transpiler thread started ...");

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("JSweet transpiler version ");
        stringBuilder.append(JSweetConfig.getVersionNumber());
        stringBuilder.append(" (build date: ");
        stringBuilder.append(JSweetConfig.getBuildDate()).append(")");

        getLog().info(stringBuilder.toString());

    }

    public void execute() {

        try {

            getMojo().transpile(this.getMojo().getMavenProject(), transpiler);

        } catch (MojoExecutionException mojoExecutionException) {

            getLog().error("execute", mojoExecutionException);

        }

    }

}

package org.jsweet;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.jsweet.transpiler.JSweetTranspiler;

/**
 * @author EPOTH -/- ponthiaux.e@sfeir.com -/- ponthiaux.eric@gmail.com
 */

public class TranspilatorThread extends TickThread {

    private MavenProject project;
    private JSweetTranspiler transpiler;

    public TranspilatorThread(AbstractJSweetMojo mojo, MavenProject project) {

        super(mojo);

        setPriority(Thread.MAX_PRIORITY);

        this.project = project;

    }

    public void setTranspiler(JSweetTranspiler transpiler) {

        this.transpiler = transpiler;

    }

    public void onRun() {

        getLog().info("- Transpiler thread started ...");

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("- JSweet transpiler version ");
        stringBuilder.append(JSweetConfig.getVersionNumber());
        stringBuilder.append(" (build date: ");
        stringBuilder.append(JSweetConfig.getBuildDate()).append(")");

        getLog().info(stringBuilder.toString());

    }

    public void execute() {

        try {

            getMojo().transpile(project, transpiler);

        } catch (MojoExecutionException mojoExecutionException) {

            getLog().error("execute", mojoExecutionException);

        }

    }

}

package org.jsweet;

import org.apache.maven.project.MavenProject;

/**
 * @author EPOTH -/- ponthiaux.e@sfeir.com -/- ponthiaux.eric@gmail.com
 */
public class JettyThread extends TickThread {

    private MavenProject project;

    public JettyThread(AbstractJSweetMojo mojo, MavenProject project) {

        super(mojo);

        this.project = project;

    }

    @Override
    public void onRun() {

        getLog().info("- Jetty thread started ... ");

    }

    @Override
    public void execute() {

    }

}

package org.jsweet;

import org.apache.maven.project.MavenProject;
import org.eclipse.jetty.server.Server;

/**
 * @author EPOTH -/- ponthiaux.e@sfeir.com -/- ponthiaux.eric@gmail.com
 */

public class JettyThread extends TickThread {

    private MavenProject project;

    private Server server;

    public JettyThread(AbstractJSweetMojo mojo, MavenProject project) {

        super(mojo);

        this.project = project;

    }

    @Override
    public void onRun() {

        getLog().info("Jetty thread started ... ");

        server = new Server(8080);

        try {

            server.start();

        } catch (Exception exception) {

            getLog().info(exception);

        }

    }

    @Override
    public void execute() {

        try {

            server.stop();

            getLog().info("Waiting for jetty to stop");

            while (server.isRunning()) {

                Thread.yield();

            }

            getLog().info("Jetty stopped");

        } catch (Exception exception) {

            getLog().info(exception);

        }

        /* */

        try {

            getLog().info("Jetty restart");

            server.start();

        } catch (Exception exception) {

            getLog().info(exception);

        }

    }

}

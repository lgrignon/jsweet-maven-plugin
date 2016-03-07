package org.jsweet;

import org.apache.maven.project.MavenProject;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

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

        WebAppContext webapp = new WebAppContext();

        webapp.setContextPath("/");

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(project.getBuild().getDirectory());

        stringBuilder.append("/");

        stringBuilder.append(project.getBuild().getFinalName());

        getLog().info("server resource base " + stringBuilder.toString());

        webapp.setResourceBase(stringBuilder.toString());

        server.setHandler(webapp);

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

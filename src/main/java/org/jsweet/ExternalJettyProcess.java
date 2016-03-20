package org.jsweet;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.eclipse.jetty.webapp.WebAppContext;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;

/**
 * Created by broken on 19/03/2016.
 */

public class ExternalJettyProcess {

    public static void main(String args[]) {

        System.err.println("--->" + System.getenv("RESOURCE_BASE"));

        Server server;

        server = new Server(8080);

        WebAppContext webAppContext = new WebAppContext();

        webAppContext.setContextPath("/");

        WebAppClassLoader webAppClassLoader = null;

        try {

            webAppClassLoader = new WebAppClassLoader(webAppContext);

        } catch (IOException ioException) {

            System.err.println(ioException);

            return;

        }

        System.err.println("Server resource base [" + System.getenv("RESOURCE_BASE") + "]");

        webAppContext.setResourceBase(System.getenv("RESOURCE_BASE"));


        try {

            System.err.println("WebApp classes directory [" + System.getenv("SERVER_CLASSES") + "]");

            Resource classesResource = Resource.newResource(System.getenv("SERVER_CLASSES"));

            webAppClassLoader.addClassPath(classesResource);

        } catch (MalformedURLException malformedURLException) {

            System.err.println(malformedURLException);

            return;

        } catch (IOException ioException) {

            System.err.println(ioException);

            return;

        }

        String dependencies[] = args[1].split(";");

        for (String dependency : dependencies) {

            try {

                System.err.println("Add to webapp classpath [" + dependency + "]");

                Resource lib = Resource.newResource(dependency);

                webAppClassLoader.addClassPath(lib);

            } catch (MalformedURLException malFormedURLException) {

                System.out.println(malFormedURLException);

            } catch (IOException ioException) {

                System.out.println(ioException);

                return;

            }

        }

        webAppContext.setClassLoader(webAppClassLoader);


        System.out.println("Source maps resource base [" + System.getenv("ADDITIONAL_RESOURCE_BASE") + "]");

        WebAppContext javaSourcesContext = new WebAppContext();

        javaSourcesContext.setContextPath("/java");

        javaSourcesContext.setResourceBase(System.getenv("ADDITIONAL_RESOURCE_BASE"));


        ArrayList<Handler> handlers = new ArrayList<>();


        handlers.add(webAppContext);

        handlers.add(javaSourcesContext);


        ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();

        contextHandlerCollection.setHandlers(handlers.toArray(new Handler[handlers.size()]));

        server.setHandler(contextHandlerCollection);

        try {

            server.start();

            server.join();

        } catch (Exception exception) {

            System.out.println(exception);

            return;

        }

    }

}

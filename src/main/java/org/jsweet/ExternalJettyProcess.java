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

        String baseDir = args[0];

        String buildDirectory = args[1];

        String finalName = args[2];

        String serverclassPath = args[3];

        Server server;

        System.out.println("Jetty thread started ... ");

        server = new Server(8080);

        /* Mount the application */


        WebAppContext webAppContext = new WebAppContext();

        webAppContext.setContextPath("/");

        WebAppClassLoader webAppClassLoader = null;

        try {

            webAppClassLoader = new WebAppClassLoader(webAppContext);

        } catch (IOException ioException) {

            System.out.println(ioException);

            return;

        }

        /* */

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(baseDir);

        stringBuilder.append("/");

        stringBuilder.append("src/main/webapp");

        /* */

        System.out.println("Server resource base [" + stringBuilder.toString() + "]");

        /* */

        webAppContext.setResourceBase(stringBuilder.toString());

        /* */

        stringBuilder.delete(0, stringBuilder.length());

        stringBuilder.append(buildDirectory);

        stringBuilder.append("/");

        stringBuilder.append(finalName);

        stringBuilder.append("/WEB-INF/classes");

        /* */

        try {

            System.out.println("WebApp classes directory [" + stringBuilder.toString() + "]");

            Resource classesResource = Resource.newResource(stringBuilder.toString());

            webAppClassLoader.addClassPath(classesResource);

        } catch (MalformedURLException malformedURLException) {

            System.out.println(malformedURLException);

            return;

        } catch (IOException ioException) {

            System.out.println(ioException);

            return;

        }

        /* */

        String dependencies[] = serverclassPath.split(";");

        /* */

        for (String dependency : dependencies) {

            try {

                System.out.println("Add to webapp classpath [" + dependency + "]");

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



        /* to resolve source maps */


        stringBuilder.delete(0, stringBuilder.length());

        stringBuilder.append(baseDir);

        stringBuilder.append("/");

        stringBuilder.append("src/main/java");

        System.out.println("Source maps resource base [" + stringBuilder.toString() + "]");

        WebAppContext javaSourcesContext = new WebAppContext();

        javaSourcesContext.setContextPath("/java");

        javaSourcesContext.setResourceBase(stringBuilder.toString());



        /* Mount all the context */


        ArrayList<Handler> handlers = new ArrayList<>();


        handlers.add(webAppContext);

        handlers.add(javaSourcesContext);


        ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();

        contextHandlerCollection.setHandlers(handlers.toArray(new Handler[handlers.size()]));

        server.setHandler(contextHandlerCollection);



        /* start the server */


        try {

            server.start();

            while (!server.isRunning()) {
                Thread.yield();
            }

            System.out.println("Jetty has started");

        } catch (Exception exception) {

            System.out.println(exception);

            return;

        }


    }

}

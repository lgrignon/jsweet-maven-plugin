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


/*

   Copyright 2016 Eric Ponthiaux -/- ponthiaux.eric@gmail.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

*/


/* @author EPOTH -/- ponthiaux.e@sfeir.com -/- ponthiaux.eric@gmail.com */

public class ExternalJettyProcess {

    private static final int SERVER_PORT = 8080;

    public static void main( String args[] ) {

        System.setProperty("org.eclipse.jetty.io.LEVEL", "DEBUG");

        Server server = new Server(SERVER_PORT);

        WebAppContext webAppContext = new WebAppContext();

        webAppContext.setContextPath("/");

        WebAppClassLoader webAppClassLoader = null;

        try {

            webAppClassLoader = new WebAppClassLoader(webAppContext);

        } catch (IOException ioException) {

            System.out.println(ioException);

            return;

        }

        System.out.println("- Jetty webapp resource base [" + System.getenv("RESOURCE_BASE") + "]");

        webAppContext.setResourceBase(System.getenv("RESOURCE_BASE"));

        try {

            System.out.println("- Jetty webapp classes directory [" + System.getenv("SERVER_CLASSES") + "]");

            Resource classesResource = Resource.newResource(System.getenv("SERVER_CLASSES"));

            webAppClassLoader.addClassPath(classesResource);

        } catch (MalformedURLException malformedURLException) {

            System.err.println(malformedURLException);

            return;

        } catch (IOException ioException) {

            System.err.println(ioException);

            return;

        }

        String dependencies[] = System.getenv("WEBAPP_DEPENDENCIES").split(";");

        for (String dependency : dependencies) {

            try {

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

        System.out.println("- Jetty source maps resource base [" + System.getenv("ADDITIONAL_RESOURCE_BASE") + "]");

        WebAppContext javaSourcesContext = new WebAppContext();

        javaSourcesContext.setContextPath("/java");

        javaSourcesContext.setResourceBase(System.getenv("ADDITIONAL_RESOURCE_BASE"));

        /* */

        ArrayList<Handler> handlers = new ArrayList<>();

        /* */

        handlers.add(webAppContext);

        handlers.add(javaSourcesContext);

        /* */

        ContextHandlerCollection contextHandlerCollection = new ContextHandlerCollection();

        contextHandlerCollection.setHandlers(handlers.toArray(new Handler[handlers.size()]));

        server.setHandler(contextHandlerCollection);

        try {

            server.start();

            System.out.println("- Jetty is listening on port " + SERVER_PORT);

            server.join();

        } catch (Exception exception) {

            System.out.println(exception);

            return;

        }

    }

}

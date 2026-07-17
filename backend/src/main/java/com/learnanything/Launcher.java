package com.learnanything;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;

public class Launcher {
    public static void main(String[] args) throws Exception {
        // ponytail: read port from ENV or use default 8080 (the docker exposed port)
        int port = 8080;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.trim().isEmpty()) {
            port = Integer.parseInt(portEnv);
        }
        
        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        
        // Add API servlets
        context.addServlet(new ServletHolder(new GeneratePathServlet()), "/api/generate-path");
        context.addServlet(new ServletHolder(new GetLessonServlet()), "/api/get-lesson");
        
        // ponytail: locate static resources at 'frontend' (Docker) or '../frontend' (Local Dev)
        String resourceBase = "frontend";
        if (!new java.io.File(resourceBase).exists()) {
            resourceBase = "../frontend";
        }
        // Resolve to absolute path so Jetty DefaultServlet can locate files reliably
        resourceBase = new java.io.File(resourceBase).getCanonicalPath();

        ServletHolder staticServlet = new ServletHolder("default", org.eclipse.jetty.ee10.servlet.DefaultServlet.class);
        staticServlet.setInitParameter("resourceBase", resourceBase);
        staticServlet.setInitParameter("dirAllowed", "false");
        staticServlet.setInitParameter("welcomeServlets", "false");
        context.addServlet(staticServlet, "/");
        context.setWelcomeFiles(new String[]{"index.html"});
        
        server.setHandler(context);
        
        System.out.println("Starting embedded Jetty server on port " + port + " with resource base: " + resourceBase);
        server.start();
        server.join();
    }
}

package org.caseyandgary;

import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.access.jetty.RequestLogImpl;

/*
 * The main class of the application.
 *
 */
public class RestServer {

    private static final Logger logger = LoggerFactory.getLogger(RestServer.class);

    private static Server launchServer(int serverPortNumber,String packageName,
        RequestLogHandler requestLogHandler){
        HandlerCollection handlerCollection = new HandlerCollection();
        //START standard boiler plate code to register the Jersey Servlet with the Jetty server
        ResourceConfig config = new ResourceConfig();
        config.packages(packageName);
        ServletHolder servlet = new ServletHolder(new ServletContainer(config));
        Server jettyServer = new Server(serverPortNumber);
        jettyServer.setStopAtShutdown(true);
        ServletContextHandler context = new ServletContextHandler(jettyServer, "/*");
        context.addServlet(servlet, "/*");
        Handler[] handlerArr = null;
        if(requestLogHandler!=null){
            handlerArr = new Handler[]{context,requestLogHandler};
        }else{
            handlerArr = new Handler[]{context};
        }
        handlerCollection.setHandlers(handlerArr);
        jettyServer.setHandler(handlerCollection);
        //END standard boiler plate code to register the Jersey Servlet with the Jetty server
        return jettyServer;
    }

    public static void main(String[] args) throws Exception {
        Server adminServer = null,apiServer = null;
        try {

            CommandLineParser parser = new PosixParser();
            Options options = new Options();
            options.addOption("config",true,"Configuration file.");
            CommandLine cmd = parser.parse(options,args);

            String xmlConfigFile = cmd.getOptionValue("config");

            Configuration.setXmlPath(xmlConfigFile);
            Configuration config =  Configuration.getConfiguration(); 

            String accessLogPath = config.getAccessLogPath();
            RequestLogImpl requestLogImpl = new RequestLogImpl();
            requestLogImpl.setFileName(accessLogPath);
            RequestLogHandler requestLogHandler = new RequestLogHandler();
            requestLogHandler.setRequestLog(requestLogImpl);

            int servicePort = config.getPort(Configuration.PORT_TYPE.SERVICE);
            apiServer  = RestServer.launchServer(servicePort,"org.caseyandgary",
            requestLogHandler);
            logger.info("Started HTTP Web service on port {}",servicePort);
            //start the servers
            apiServer.start();
            apiServer.join();
            logger.trace("API server joined.");

        }catch(Exception ex){
            logger.error("Error in launching Jetty server:",ex);
        } finally {
            if(apiServer!=null) apiServer.destroy();

        }

    }
}

package org.caseyandgary;


import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.File;
import java.util.Set;
import java.util.TreeSet;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.caseyandgary.Configuration.ShowInfo;


import jersey.repackaged.com.google.common.collect.Lists;


@Path("mplayer")
public class MplayerWS {
private static final Logger logger = LoggerFactory.getLogger(MplayerWS.class);

    //private static Process mplayerProcess = null;
    private JMPlayer jMPlayer = null;

    private enum Action{
        PLAY, LIST
    }


    class CallbackParams{
        Map<String,String> params;
        JsonObjectBuilder response;

        CallbackParams(Map<String,String> params,JsonObjectBuilder response){
            this.params = params;
            this.response = response;
        }
        
    }
    
    public MplayerWS() {
        logger.trace("In constructor");
        jMPlayer = JMPlayer.getInstance();
        jMPlayer.setMPlayerPath("/usr/local/bin/mplayer");
    }

    @GET @Path("/stop")
    @Produces(MediaType.APPLICATION_JSON)
    public Response play() {
        Map<String,String> params = null;
        Function<CallbackParams,Exception> playFile = 
        new Function<CallbackParams,Exception>(){
            @Override
            public Exception apply(CallbackParams cp){
                try{
                    logger.trace("Closing existing process");
                    jMPlayer.close();
                    cp.response.add("message","OK");
                    return null;
                }catch(Exception ex){
                    return ex;
                }
            }
        };
        return executeHelper(playFile,params);
    }

    @GET @Path("/seek")
    @Produces(MediaType.APPLICATION_JSON)
    public Response seek(@QueryParam("seconds") String secondsParam) {
        Map<String,String> params = new HashMap<String,String>();
        final String seekKey = "seconds";
        params.put(seekKey,secondsParam);
        Function<CallbackParams,Exception> seekFile = 
        new Function<CallbackParams,Exception>(){
            @Override
            public Exception apply(CallbackParams cp){
                final int seconds = Integer.parseInt(cp.params.get(seekKey));
                try{
                    logger.trace("Seeking {} seconds",seconds);
                    jMPlayer.seek(seconds);
                    cp.response.add("message","OK");
                    return null;
                }catch(Exception ex){
                    return ex;
                }
            }
        };
        return executeHelper(seekFile,params);
    }

    @GET @Path("/play")
    @Produces(MediaType.APPLICATION_JSON)
    public Response play(@QueryParam("file") String fileParam) {
        Map<String,String> params = new HashMap<String,String>();
        final String fileKey = "file";
        params.put(fileKey,fileParam);
        Function<CallbackParams,Exception> playFile = 
        new Function<CallbackParams,Exception>(){
            @Override
            public Exception apply(CallbackParams cp){
                final String fileName = cp.params.get(fileKey);
                logger.trace("Playing path {}",fileName);
                try{
                    //mplayerProcess = Runtime.getRuntime().exec("mplayer -slave -quiet -idle "+fileName);
                    jMPlayer.open(new File(fileName));
                    jMPlayer.setFullScreen();
                    jMPlayer.setVolume(100);
                    cp.response.add("message","OK");
                    return null;
                }catch(Exception ex){
                    return ex;
                }
            }
        };
        return executeHelper(playFile,params);
    }

    @GET @Path("/show_info")
    @Produces(MediaType.APPLICATION_JSON)
    public Response showInfo(@QueryParam("show_name") String showNameParam) {
        Map<String,String> params = new HashMap<String,String>();
        final String showNameKey = "show_name";
        params.put(showNameKey,showNameParam);
        Function<CallbackParams,Exception> getShowName = 
        new Function<CallbackParams,Exception>(){
            @Override
            public Exception apply(CallbackParams cp){
                try{
                    String showName = cp.params.get(showNameKey);
                    if(showName!=null) showName = showName.toLowerCase();
                    logger.trace("Getting info for show name {}",showName);
                    ShowInfo showInfo = Configuration.getConfiguration().
                    getShowInfo(showName);
                    JsonArrayBuilder jsonList = Json.createArrayBuilder();
                    if(showInfo!=null){
                        File dir = new File(showInfo.path);
                        FileFilter fileFilter = new WildcardFileFilter(showInfo.filePattern);
                        logger.trace("Looking at base path {}.",dir.toString());
                        File[] files = dir.listFiles(fileFilter);
                        if(files!=null && files.length>0){
                            Set<String> sortedFileNames = new TreeSet<String>();
                            for(File file:files){
                                sortedFileNames.add(file.toString());
                                logger.trace("Added file {}",file.toString());
                            }
                            for(String fileName:sortedFileNames){
                                jsonList.add(fileName);
                            }
                        }else{
                            logger.trace("No files found.");
                        }
                    }
                    cp.response.add("files",jsonList);
                    cp.response.add("message","OK");
                    return null;
                }catch(Exception ex){
                    return ex;
                }
            }
        };
        return executeHelper(getShowName,params);
    }

    @GET @Path("/list")
    @Produces(MediaType.APPLICATION_JSON)
    public Response list() {
        Map<String,String> params = null;
        Function<CallbackParams,Exception> listShows = 
        new Function<CallbackParams,Exception>(){
            @Override
            public Exception apply(CallbackParams cp){
                try{
                    Collection<String> showNames = 
                    Configuration.getConfiguration().getShows();
                    JsonArrayBuilder jsonList = Json.createArrayBuilder();
                    if(showNames!=null && showNames.size()>0){
                        for(String showName:showNames){
                            jsonList.add(showName);
                            logger.trace("Added showname {}",showName);
                        }
                    }else{
                        logger.trace("No shows found.");
                    }
                    cp.response.add("shows",jsonList);
                    cp.response.add("message","OK");
                    return null;
                }catch(Exception ex){
                    return ex;
                }
            }
        };
        return executeHelper(listShows,params);
    }

    private void handleException(JsonObjectBuilder metadata,Exception ex){
        StringWriter writer = new StringWriter();
        ex.printStackTrace(new PrintWriter(writer));
        writer.flush();
        metadata.add("status",500);
        metadata.add("message",writer.toString());
    }
    
    private Response executeHelper(
    Function<CallbackParams,Exception> callBack, Map<String,String> params){
        StringWriter sw = new StringWriter();
        Map<String, Boolean> conf = new HashMap<String, Boolean>();
        conf.put(JsonGenerator.PRETTY_PRINTING, true);
        JsonWriterFactory jwf = Json.createWriterFactory(conf);
        JsonObjectBuilder json = Json.createObjectBuilder();
        JsonObjectBuilder metadata = Json.createObjectBuilder();
        JsonObjectBuilder response = Json.createObjectBuilder();
        long start = System.currentTimeMillis();
        Exception ex = callBack.apply(new CallbackParams(params,response));
        if(ex!=null){
            handleException(metadata,ex);
        }else{
            metadata.add("status", 200);
            metadata.add("message", "Success");
        }
        metadata.add("response_time", System.currentTimeMillis() - start);
        json.add("metadata", metadata);
        json.add("response", response);
        try (JsonWriter jw = jwf.createWriter(sw)) {
           jw.writeObject(json.build());
        }
        return Response.ok(sw.toString()).build();
    }

}

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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
//import org.caseyandgary.Configuration.MovieInfo;


import jersey.repackaged.com.google.common.collect.Lists;


@Path("mplayer")
public class MplayerWS {

    private static final Logger logger = LoggerFactory.getLogger(MplayerWS.class);
    private final String MEDIA_TYPE_CHANNELS = "channels";
    private final String MEDIA_TYPE_MOVIES = "movies";
    private final String DVB_PLAY_PREFIX = "dvb://1@";
    private final String DVB_RECORD_PREFIX = "dvb://2@";

    //private static Process mplayerProcess = null;
    private JMPlayer jMPlayer = null;
    private JMPlayer jMRecorder = null;

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
        jMRecorder = JMPlayer.getInstance();
        jMRecorder.setMPlayerPath("/usr/local/bin/mplayer");

    }

    @GET @Path("/stop")
    @Produces(MediaType.APPLICATION_JSON)
    public Response stop() {
        Map<String,String> params = null;
        Function<CallbackParams,Exception> stopFunc = 
        new Function<CallbackParams,Exception>(){
            @Override
            public Exception apply(CallbackParams cp){
                try{
                    logger.trace("Closing existing process");
                    jMPlayer.close(JMPlayer.MPLAYER_TYPE.PLAY);
                    cp.response.add("message","OK");
                    return null;
                }catch(Exception ex){
                    return ex;
                }
            }
        };
        return executeHelper(stopFunc,params);
    }

    @GET @Path("/reload")
    @Produces(MediaType.APPLICATION_JSON)
    public Response reload() {
        Map<String,String> params = null;
        Function<CallbackParams,Exception> reloadFunc = 
        new Function<CallbackParams,Exception>(){
            @Override
            public Exception apply(CallbackParams cp){
                try{
                    logger.trace("Reloading movie list");
                    Configuration.reload();
                    cp.response.add("message","Reloaded movie list.");
                    return null;
                }catch(Exception ex){
                    return ex;
                }
            }
        };
        return executeHelper(reloadFunc,params);
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
                    jMPlayer.seek(JMPlayer.MPLAYER_TYPE.PLAY,seconds);
                    cp.response.add("message","OK");
                    return null;
                }catch(Exception ex){
                    return ex;
                }
            }
        };
        return executeHelper(seekFile,params);
    }

    @GET @Path("/volume")
    @Produces(MediaType.APPLICATION_JSON)
    public Response volume(@QueryParam("volume") String volumeParam) {
        Map<String,String> params = new HashMap<String,String>();
        final String volumeKey = "volume";
        params.put(volumeKey,volumeParam);
        Function<CallbackParams,Exception> volumeFile = 
        new Function<CallbackParams,Exception>(){
            @Override
            public Exception apply(CallbackParams cp){
                final int volume = Integer.parseInt(cp.params.get(volumeKey));
                try{
                    logger.trace("Setting volume to {}",volume);
                    jMPlayer.setVolume(JMPlayer.MPLAYER_TYPE.PLAY,volume);
                    cp.response.add("message","OK");
                    return null;
                }catch(Exception ex){
                    return ex;
                }
            }
        };
        return executeHelper(volumeFile,params);
    }

    @GET @Path("/play")
    @Produces(MediaType.APPLICATION_JSON)
    public Response play(
    @QueryParam("file") String fileParam, 
    @QueryParam("type") String typeParam) {
        Map<String,String> params = new HashMap<String,String>();
        final String typeKey = "type";
        final String fileKey = "file";
        params.put(typeKey,typeParam);
        params.put(fileKey,fileParam);
        Function<CallbackParams,Exception> playFile = 
        new Function<CallbackParams,Exception>(){
            @Override
            public Exception apply(CallbackParams cp){
                final String typeName = cp.params.get(typeKey);
                final String mediaName = cp.params.get(fileKey);
                logger.trace("Playing path {} of type {}",mediaName,typeName);
                try{
                    //mplayerProcess = Runtime.getRuntime().
                    //exec("mplayer -slave -quiet -idle "+fileName);
                    String pathName = null;
                    if(typeName.equals(MEDIA_TYPE_CHANNELS)){
                        String dvbPrefix = DVB_PLAY_PREFIX;
                        String channelName = 
                        Configuration.getConfiguration().
                        getChannelName(mediaName);
                        pathName = dvbPrefix + channelName;
                    }else{
                        pathName = Configuration.getConfiguration().
                        getMoviePath(mediaName);
                    }
                    logger.trace("As a precaution, closing existing process");
                    jMPlayer.close(JMPlayer.MPLAYER_TYPE.PLAY);
                    jMPlayer.open(JMPlayer.MPLAYER_TYPE.PLAY,pathName);
                    //jMPlayer.setFullScreen(JMPlayer.MPLAYER_TYPE.PLAY);
                    cp.response.add("message","OK");
                    logger.trace("JMPlayer was opened.");
                    return null;
                }catch(Exception ex){
                    return ex;
                }
            }
        };
        return executeHelper(playFile,params);
    }

    @GET @Path("/record")
    @Produces(MediaType.APPLICATION_JSON)
    public Response record(
    @QueryParam("channel") String channelParam, 
    @QueryParam("duration_minutes") String durationMinutesParam){ 
        Map<String,String> params = new HashMap<String,String>();
        final String channelKey = "channel";
        final String durationMinutesKey = "duration_minutes";
        params.put(channelKey,channelParam);
        params.put(durationMinutesKey,durationMinutesParam);
        Function<CallbackParams,Exception> recordFile = 
        new Function<CallbackParams,Exception>(){
            @Override
            public Exception apply(CallbackParams cp){
                final String channel = cp.params.get(channelKey);
                final String durationMinutes = 
                cp.params.get(durationMinutesKey);

                try{
                    DateTimeFormatter formatter = 
                    DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
                    String timeStr = LocalDateTime.now().
                    format(formatter);
      		    String fileName = "recording_"+timeStr+".m2t";
    
                    logger.trace("Recording channel {} for {} minutes. "+
                    "File name is {}",channel,durationMinutes,fileName);
                    int durationSeconds = 60 * 
                    Integer.parseInt(durationMinutes);

                    String dvbPrefix = DVB_RECORD_PREFIX;
                    String channelName = 
                    Configuration.getConfiguration().
                    getChannelName(channel);
                    String inputPath = dvbPrefix + channelName;
                    boolean returnCode = 
                    jMRecorder.record(JMPlayer.MPLAYER_TYPE.RECORD,inputPath,
                    fileName,durationSeconds);
                    cp.response.add("recording",Boolean.toString(returnCode));
                    cp.response.add("filename",fileName);
                    logger.trace("JMRecorder was started.");
                    return null;
                }catch(Exception ex){
                    return ex;
                }
            }
        };
        return executeHelper(recordFile,params);
    }

//    @GET @Path("/movie_info")
//    @Produces(MediaType.APPLICATION_JSON)
//    public Response movieInfo(@QueryParam("movie_name") String movieNameParam) {
//        Map<String,String> params = new HashMap<String,String>();
//        final String movieNameKey = "movie_name";
//        params.put(movieNameKey,movieNameParam);
//        Function<CallbackParams,Exception> getMovieName = 
//        new Function<CallbackParams,Exception>(){
//            @Override
//            public Exception apply(CallbackParams cp){
//                try{
//                    String movieName = cp.params.get(movieNameKey);
//                    if(movieName!=null) movieName = movieName.toLowerCase();
//                    logger.trace("Getting info for movie name {}",movieName);
//                    MovieInfo movieInfo = Configuration.getConfiguration().
//                    getMovieInfo(movieName);
//                    JsonArrayBuilder jsonList = Json.createArrayBuilder();
//                    if(movieInfo!=null){
//                        File dir = new File(movieInfo.path);
//                        FileFilter fileFilter = new WildcardFileFilter(movieInfo.filePattern);
//                        logger.trace("Looking at base path {}.",dir.toString());
//                        File[] files = dir.listFiles(fileFilter);
//                        if(files!=null && files.length>0){
//                            Set<String> sortedFileNames = new TreeSet<String>();
//                            for(File file:files){
//                                sortedFileNames.add(file.toString());
//                                logger.trace("Added file {}",file.toString());
//                            }
//                            for(String fileName:sortedFileNames){
//                                jsonList.add(fileName);
//                            }
//                        }else{
//                            logger.trace("No files found.");
//                        }
//                    }
//                    cp.response.add("files",jsonList);
//                    cp.response.add("message","OK");
//                    return null;
//                }catch(Exception ex){
//                    return ex;
//                }
//            }
//        };
//        return executeHelper(getMovieName,params);
//    }

    @GET @Path("/list")
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(@QueryParam("type") String typeParam) {
        Map<String,String> params = new HashMap<String,String>();
        final String typeKey = "type";
        params.put(typeKey,typeParam);

        Function<CallbackParams,Exception> listMedia = 
        new Function<CallbackParams,Exception>(){
            @Override
            public Exception apply(CallbackParams cp){
                try{
                    final String typeName = cp.params.get(typeKey);
                    logger.trace("Selected media type {}",typeName);
                    if(typeName.equalsIgnoreCase(MEDIA_TYPE_MOVIES)){
                        Collection<String> movieNames = 
                        Configuration.getConfiguration().getMovies();
                        JsonArrayBuilder jsonList = Json.createArrayBuilder();
                        if(movieNames!=null && movieNames.size()>0){
                            for(String movieName:movieNames){
                                jsonList.add(movieName);
                                logger.trace("Added moviename {}",movieName);
                            }
                        }else{
                            logger.trace("No movies found.");
                        }
                        cp.response.add("movies",jsonList);
                    }else if(typeName.equalsIgnoreCase(MEDIA_TYPE_CHANNELS)){
                        Collection<String> channelNames = 
                        Configuration.getConfiguration().getChannels();
                        JsonArrayBuilder jsonList = Json.createArrayBuilder();
                        if(channelNames!=null && channelNames.size()>0){
                            for(String channelName:channelNames){
                                jsonList.add(channelName);
                                logger.trace("Added channelname {}",channelName);
                            }
                        }else{
                            logger.trace("No channels found.");
                        }
                        cp.response.add("channels",jsonList);
                    }
                    cp.response.add("message","OK");
                    return null;
                }catch(Exception ex){
                    return ex;
                }
            }
        };
        return executeHelper(listMedia,params);
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

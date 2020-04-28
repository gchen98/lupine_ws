package org.caseyandgary;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;


public class Configuration{

    private static final Logger logger = LoggerFactory.getLogger(Configuration.class);
    private static Configuration instance = null;
    private static Map<String,String> movieMap = null;
    private static Map<String,String> channelMap = null;
    private XMLConfiguration config = null;
    private static String xmlPath = null;
    private LevenshteinDistance levDistance = new LevenshteinDistance();

    public static enum PORT_TYPE{
        ADMIN,SERVICE
    }

    public static enum VIDEO_TYPE{
        TV,MOVIE
    }

    public static void setXmlPath(String xmlPath1){
        xmlPath = xmlPath1;
    }

    public static Configuration getConfiguration(){
        if(instance == null){
            try{
                Configuration.reload();
            }catch(ConfigurationException e){
                logger.error("An error occured loading configuration: ",e);
            }
        }
        return instance;
    }

    private Configuration(){
    }

    public static void reload()
    throws ConfigurationException{
        instance = new Configuration();
        Configurations configs = new Configurations();
        if(xmlPath==null) 
        throw new ConfigurationException(
        "XML path must be set before calling getConfiguration");
        instance.config = configs.xml(xmlPath);
        instance.movieMap = new HashMap<String,String>();
        List<HierarchicalConfiguration<ImmutableNode>> movies = 
	instance.config.configurationsAt("movies.movie");
        for(HierarchicalConfiguration<ImmutableNode> sub:movies){
            movieMap.put(sub.getString("name"),sub.getString("path"));
        }
        channelMap = new HashMap<String,String>();
        List<HierarchicalConfiguration<ImmutableNode>> channels = 
	instance.config.configurationsAt("tv_channels.channel");
        for(HierarchicalConfiguration<ImmutableNode> sub:channels){
            channelMap.put(sub.getString("number"),sub.getString("name"));
        }
    }

    public String getDvbPrefix(){
        String dvbPrefix = config.getString("dvb_prefix");
        logger.trace("Using DVB prefix of {}",dvbPrefix);
        return dvbPrefix;
    }

    public String getMplayerOptions(){
        String mplayerOptions = config.getString("mplayer_options");
        logger.trace("Mplayer options are {}",mplayerOptions);
        return mplayerOptions;
    }

    public String getAccessLogPath(){
        String logPath = config.getString("access_logs");
        logger.trace("Using access logs path of {}",logPath);
        return logPath;

    }

    public int getPort(PORT_TYPE portType){
        HierarchicalConfiguration<ImmutableNode> portConf = config.configurationAt("ports");
        int port = 0;
        switch(portType) {
            case SERVICE:
                port = portConf.getInt("service");
                break;
            case ADMIN:
                port = portConf.getInt("admin");
                break;
        }
        return port;
    }


    public Collection<String> getMovies(){
        return movieMap.keySet();
    }

    public Collection<String> getChannels(){
        return channelMap.keySet();
    }

    public String getMoviePath(String name){
        return getFuzzyMatch(movieMap,name);
    }

    public String getChannelName(String number){
        return getFuzzyMatch(channelMap,number);
    }

    private String getFuzzyMatch(Map<String,String> map, String fuzzyKey){
        logger.trace("Received fuzzy key {}",fuzzyKey);
        String bestKey = null;
        int lowestDistance = Integer.MAX_VALUE;
        for(String key:map.keySet()){
            String lower1 = key.toLowerCase();
            String lower2 = fuzzyKey.toLowerCase();
            Integer currDistance = 
            levDistance.apply(lower1,lower2);
            logger.trace("Comparing {} to {} and score is {}",
            lower1,lower2,currDistance);
            if(currDistance<lowestDistance){
                bestKey = key;
                lowestDistance = currDistance.intValue();
            }
        }
        logger.trace("Best fuzzy match is {}",bestKey);
        return map.get(bestKey);
    }

}

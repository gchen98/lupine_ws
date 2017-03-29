package org.caseyandgary;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;


public class Configuration{

    private static final Logger logger = LoggerFactory.getLogger(Configuration.class);
    private static Configuration instance = null;
    private static Map<String, ShowInfo> showMap = null;
    private XMLConfiguration config = null;
    private static String xmlPath = null;

    public static enum PORT_TYPE{
        ADMIN,SERVICE
    }

    public static enum VIDEO_TYPE{
        TV,MOVIE
    }

    class ShowInfo{
        String path;
        String filePattern;
        ShowInfo(String path, String filePattern){
          this.path = path;
          this.filePattern = filePattern;
        }
    }

    public static void setXmlPath(String xmlPath1){
        xmlPath = xmlPath1;
    }


    public static Configuration getConfiguration()
    throws ConfigurationException{
        if(instance == null){
            if(xmlPath!=null) {
                instance = new Configuration();
            }else{
                throw new ConfigurationException("XML path must be set before calling getConfiguration");
            }
        }
        return instance;
    }

    private Configuration()
    throws ConfigurationException{
        Configurations configs = new Configurations();
        showMap = new HashMap<String,ShowInfo>();
        config = configs.xml(xmlPath);
        List<HierarchicalConfiguration<ImmutableNode>> shows = config.configurationsAt("show_paths.show");
        for(HierarchicalConfiguration<ImmutableNode> sub:shows){
            showMap.put(sub.getString("name"),
            new ShowInfo(sub.getString("path"),sub.getString("filepattern")));
        }

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


    public Collection<String> getShows(){
        return showMap.keySet();
    }

    public ShowInfo getShowInfo(String name){
        return showMap.get(name);
    }

}

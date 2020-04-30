package org.caseyandgary;

import java.io.*;
//import java.util.logging.Logger;
//import java.util.logging.Level;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A player which is actually an interface to the famous MPlayer.
 * @author Adrian BER
 * @author Gary Chen
 */
public class JMPlayer {

    public static enum MPLAYER_TYPE{
        PLAY,RECORD
    }

    private static JMPlayer instance = null;
    private static final Logger logger = LoggerFactory.getLogger(JMPlayer.class);
    private static class Mplayer {

        /** The process corresponding to MPlayer. */
        private Process mplayerProcess;
        /** The standard input for MPlayer where you can send commands. */
        private PrintStream mplayerIn;
        /** A combined reader for the the standard output and error of MPlayer. Used to read MPlayer responses. */
        private BufferedReader mplayerOutErr;
        /** A flag to notify when to write to the process's pipe */
        private volatile boolean writeToPipe;

        private Mplayer(){
            writeToPipe = false;
        }

    }

    /** A thread that reads from an input stream and outputs to another line by line. */
    private static class LineRedirecter extends Thread {
        /** The input stream to read from. */
        private InputStream in;
        /** The output stream to write to. */
        private OutputStream out;
        /** The prefix used to prefix the lines when outputting to the logger. */
        private String prefix;
        /** Reference to the mplayer process */
        private Mplayer mplayer;

        /**
         * @param in the input stream to read from.
         * @param out the output stream to write to.
         * @param prefix the prefix used to prefix the lines when outputting to the logger.
         * @param mplayer reference to the mplayer process
         */
        LineRedirecter(InputStream in, OutputStream out, String prefix, 
        Mplayer mplayer) {
            this.in = in;
            this.out = out;
            this.prefix = prefix;
            this.mplayer = mplayer;
        }

        @Override
        public void finalize() {
            logger.trace("Called finalize on thread!");
        }

        @Override
        public void run()
        {
            try {
                // creates the decorating reader and writer
                BufferedReader reader = new BufferedReader(
                new InputStreamReader(in));
                PrintStream printStream = new PrintStream(out);
                String line;
                // read line by line
                while ( (line = reader.readLine()) != null) {
                    if(mplayer.writeToPipe){
                        printStream.println(line);
                    }
                }
                logger.trace((prefix != null ? prefix : " finish loop ")  );
            } catch (Exception exc) {
                logger.warn("An error has occured while grabbing lines", exc);
            }
        }

    }

    /**
    * a thread that will kill a mplayer process
    */

    private static class MplayerHalter
    implements Runnable{

        // reference to the enclosing class
        JMPlayer jmplayer;
        // reference to the specific mplayer instance
        Mplayer mplayer;
        // duration in seconds
        int durationSeconds;

        private MplayerHalter(JMPlayer jmplayer,Mplayer mplayer,
        int durationSeconds){
            this.jmplayer = jmplayer;
            this.mplayer = mplayer;
            this.durationSeconds = durationSeconds;
        }

        @Override
        public void run(){
            try{
                logger.trace("Recording for {} seconds.", 
                durationSeconds);
                long pid = mplayer.mplayerProcess.pid();
                logger.trace("PID is {}",pid);

                Thread.sleep(durationSeconds*1000);
                try{
                    Runtime.getRuntime().exec("kill -SIGINT "+pid);
                    mplayer.mplayerProcess.waitFor();
                }catch(IOException e){
                    logger.warn("Could not kill process, kill manually.");
                    mplayer.mplayerProcess.waitFor(1,
                    TimeUnit.SECONDS);
                }
                logger.trace("Recording done!");
                mplayer.mplayerProcess = null;
            }catch(InterruptedException e){
                logger.warn("Interrupted Exception: ",e);
            }
        }
    }

    /** The path to the MPlayer executable. */
    private String mplayerPath = "mplayer";
    /** Options passed to MPlayer. */
    private String mplayerOptions = "-slave -idle";

    private Mplayer playMplayer = null;
    private Mplayer recordMplayer= null;

    private Mplayer getMplayer(MPLAYER_TYPE mplayerType){
        Mplayer mplayer =  null;
        switch(mplayerType){
            case PLAY:
                mplayer = playMplayer;
                break;
            case RECORD:
                mplayer = recordMplayer;
                break;
                
        }
        return mplayer;
    }


    public static JMPlayer getInstance(){
        if(instance==null){
            instance = new JMPlayer();
        }
        return instance;
    }

    private JMPlayer() {
        try{
            mplayerOptions = Configuration.getConfiguration().
            getMplayerOptions();
            playMplayer = new Mplayer();
            recordMplayer = new Mplayer();
        }catch(Exception e){
            logger.warn( "Error in getting XML configuiation", e);
        }
        logger.info("Mplayer options are: "+mplayerOptions);
    }

    /** @return the path to the MPlayer executable. */
    public String getMPlayerPath() {
        return mplayerPath;
    }

    /** Sets the path to the MPlayer executable.
     * @param mplayerPath the new MPlayer path; this will be actually efective
     * after {@link #close() closing} the currently running player.
     */
    public void setMPlayerPath(String mplayerPath) {
        this.mplayerPath = mplayerPath;
    }

    public void open(MPLAYER_TYPE mplayerType,String rawPath) 
    throws IOException {
        Mplayer mplayer = getMplayer(mplayerType);
        //String path = file.getAbsolutePath().replace('\\', '/');
        String path = rawPath.replace('\\', '/');
	
        if (mplayer.mplayerProcess == null) {
            // start MPlayer as an external process
            String command =  mplayerPath  + " "+ mplayerOptions + " " 
            + path + "";

            logger.info("Starting MPlayer process: " + command);

            //Process process = Runtime.getRuntime().exec(command);
            mplayer.mplayerProcess = Runtime.getRuntime().exec(command);

            // create the piped streams where to redirect the standard output 
            // and error of MPlayer
            // specify a bigger pipesize
            PipedInputStream  readFrom = new PipedInputStream(1024*1024);
            PipedOutputStream writeTo = new PipedOutputStream(readFrom);
            mplayer.mplayerOutErr = new BufferedReader(
            new InputStreamReader(readFrom));

            // create the threads to redirect the standard output and 
            // error of MPlayer
            new LineRedirecter(mplayer.mplayerProcess.getInputStream(), 
            writeTo, "MPlayer STDOUT: ",mplayer).start();
            new LineRedirecter(mplayer.mplayerProcess.getErrorStream(), 
            writeTo, "MPlayer STDERR: ",mplayer).start();

            // the standard input of MPlayer
            mplayer.mplayerIn = new PrintStream(
            mplayer.mplayerProcess.getOutputStream());
        } else {
            logger.trace("Using existing mplayer process");
            execute(mplayer,"loadfile " + path + " 0");
        }
        // wait to start playing
        waitForAnswer(mplayer,"Starting playback...");
        logger.info("Started playing file " + path);
    }

    public boolean record(MPLAYER_TYPE mplayerType,String inputPath, 
    String outputPath,int durationSeconds) 
    throws IOException {
        Mplayer mplayer = getMplayer(mplayerType);
        outputPath = outputPath.replace('\\', '/');
        String recordingDir = Configuration.getConfiguration().
        getRecordingDir();
        String dumpFile = recordingDir + outputPath;
	
        if (mplayer.mplayerProcess == null) {
            // start MPlayer as an external process
            String command =  mplayerPath  + " -slave -idle -dumpstream " +
            inputPath + " -dumpfile " + dumpFile;
            //String command =  mplayerPath  + " -slave -idle " +
            //inputPath ;
            logger.info("Starting MPlayer process: " + command);
            mplayer.mplayerProcess = Runtime.getRuntime().exec(command);

            // create the piped streams where to redirect the standard output 
            // and error of MPlayer
            // specify a bigger pipesize
            PipedInputStream  readFrom = new PipedInputStream(1024*1024);
            PipedOutputStream writeTo = new PipedOutputStream(readFrom);
            mplayer.mplayerOutErr = new BufferedReader(
            new InputStreamReader(readFrom));

            // create the threads to redirect the standard output and 
            // error of MPlayer
            new LineRedirecter(mplayer.mplayerProcess.getInputStream(),
            writeTo, "MPlayer STDOUT: ",mplayer).start();
            new LineRedirecter(mplayer.mplayerProcess.getErrorStream(),
            writeTo, "MPlayer STDERR: ",mplayer).start();

            // the standard input of MPlayer
            mplayer.mplayerIn = new PrintStream(
            mplayer.mplayerProcess.getOutputStream());
            // wait to start playing
            //waitForAnswer(mplayer,"Starting playback...");
            waitForAnswer(mplayer,"dump:");
            logger.info("Started recording file " + outputPath);
            new Thread(new MplayerHalter(this,mplayer,durationSeconds)).start();
            return true;
        } else {
            logger.trace("Not going to use existing mplayer process "+
            "until it is null");
            return false;
        }
    }

    public void close(MPLAYER_TYPE mplayerType) {
        Mplayer mplayer = getMplayer(mplayerType);
        if (mplayer.mplayerProcess != null) {
            execute(mplayer,"quit");
            try {
                mplayer.mplayerProcess.waitFor();
            }
            catch (InterruptedException e) {}
            mplayer.mplayerProcess = null;
        }
    }

    public File getPlayingFile(MPLAYER_TYPE mplayerType) {
        Mplayer mplayer = getMplayer(mplayerType);
        String path = getProperty(mplayer,"path");
        return path == null ? null : new File(path);
    }

    public void togglePlay(MPLAYER_TYPE mplayerType) {
        Mplayer mplayer = getMplayer(mplayerType);
        execute(mplayer,"pause");
    }

    public void setFullScreen(MPLAYER_TYPE mplayerType) {
        Mplayer mplayer = getMplayer(mplayerType);
        execute(mplayer,"vo_fullscreen 1");
    }

    public void seek(MPLAYER_TYPE mplayerType,long seconds){
        // second argument to seek is seek type:
        //    Seek to some place in the movie.
        //        0 is a relative seek of +/- <value> seconds (default).
        //        1 is a seek to <value> % in the movie.
        //        2 is a seek to an absolute position of <value> seconds.
        Mplayer mplayer = getMplayer(mplayerType);
        execute(mplayer,"seek "+seconds+" 0");
    }

    public boolean isPlaying(MPLAYER_TYPE mplayerType) {
        Mplayer mplayer = getMplayer(mplayerType);
        return mplayer.mplayerProcess != null;
    }

    public long getTimePosition(MPLAYER_TYPE mplayerType) {
        Mplayer mplayer = getMplayer(mplayerType);
        return getPropertyAsLong(mplayer,"time_pos");
    }

    public void setTimePosition(MPLAYER_TYPE mplayerType,long seconds) {
        Mplayer mplayer = getMplayer(mplayerType);
        setProperty(mplayer,"time_pos", seconds);
    }


    public long getTotalTime(MPLAYER_TYPE mplayerType) {
        Mplayer mplayer = getMplayer(mplayerType);
        return getPropertyAsLong(mplayer,"length");
    }

    public float getVolume(MPLAYER_TYPE mplayerType) {
        Mplayer mplayer = getMplayer(mplayerType);
        return getPropertyAsFloat(mplayer,"volume");
    }

    public void setVolume(MPLAYER_TYPE mplayerType, float volume) {
        Mplayer mplayer = getMplayer(mplayerType);
        setProperty(mplayer,"volume", volume);
    }

    protected String getProperty(Mplayer mplayer, String name) {
        if (name == null || mplayer.mplayerProcess == null) {
            return null;
        }
        String s = "ANS_" + name + "=";
        String x = execute(mplayer,"get_property " + name, s);
        if (x == null)
            return null;
        if (!x.startsWith(s))
            return null;
        return x.substring(s.length());
    }

    protected long getPropertyAsLong(Mplayer mplayer,String name) {
        try {
            return Long.parseLong(getProperty(mplayer,name));
        }
        catch (NumberFormatException exc) {}
        catch (NullPointerException exc) {}
        return 0;
    }

    protected float getPropertyAsFloat(Mplayer mplayer,String name) {
        try {
            return Float.parseFloat(getProperty(mplayer,name));
        }
        catch (NumberFormatException exc) {}
        catch (NullPointerException exc) {}
        return 0f;
    }

    protected void setProperty(Mplayer mplayer,String name, String value) {
        execute(mplayer,"set_property " + name + " " + value);
    }

    protected void setProperty(Mplayer mplayer,String name, long value) {
        execute(mplayer,"set_property " + name + " " + value);
    }

    protected void setProperty(Mplayer mplayer,String name, float value) {
        execute(mplayer,"set_property " + name + " " + value);
    }

    /** Sends a command to MPlayer..
     * @param command the command to be sent
     */
    private void execute(Mplayer mplayer,String command) {
        execute(mplayer,command, null);
    }

    /** Sends a command to MPlayer and waits for an answer.
     * @param command the command to be sent
     * @param expected the string with which has to start the line; if null don't wait for an answer
     * @return the MPlayer answer
     */
    private String execute(Mplayer mplayer, String command, String expected) {
        if (mplayer.mplayerProcess != null) {
            logger.info("Send to MPlayer the command \"" + 
            command + "\" and expecting " + (expected != null ? "\"" 
            + expected + "\"" : "no answer"));
            mplayer.mplayerIn.print(command);
            mplayer.mplayerIn.print("\n");
            mplayer.mplayerIn.flush();
            logger.info("Command sent");
            if (expected != null) {
                String response = waitForAnswer(mplayer,expected);
                logger.info("MPlayer command response: " + response);
                return response;
            }
        }
        return null;
    }

    /** Read from the MPlayer standard output and error a line that starts with the given parameter and return it.
     * @param expected the expected starting string for the line
     * @return the entire line from the standard output or error of MPlayer
     */
    private String waitForAnswer(Mplayer mplayer,String expected) {
        // todo add the possibility to specify more options to be specified
        // todo use regexp matching instead of the beginning of a string
        String line = null;
        if (expected != null) {
            try {
                mplayer.writeToPipe = true;
                while ((line = mplayer.mplayerOutErr.readLine()) != null) {
                    logger.info("Reading line: " + line);
                    if (line.startsWith(expected)) {
                        return line;
                    }
                }
                mplayer.writeToPipe = false;
            }
            catch (IOException e) {
            }
        }
        return line;
    }

    @Override
    public void finalize(){
       logger.trace("Called finalize on JMPlayer!");
    }


    public static void main(String[] args) throws IOException {

        String xmlConfigFile = "conf/config.xml";
        logger.info("Using XML config at {}",xmlConfigFile);

        Configuration.setXmlPath(xmlConfigFile);
        JMPlayer jmPlayer = JMPlayer.getInstance();
        //jmPlayer.setMPlayerPath("/usr/local/bin/mplayer");
        // open a video file
        //jmPlayer.open(MPLAYER_TYPE.PLAY,"dvb://1@KABC");
        //Runtime.getRuntime().exec("/usr/local/bin/mplayer /home/garyc/Videos/movies/test.mp4");
        jmPlayer.open(MPLAYER_TYPE.PLAY,"/home/garyc/Videos/movies/test.mp4");
        // skip 2 minutes
        //jmPlayer.setTimePosition(MPLAYER_TYPE.PLAY,120);
        // skip 2 minutes
        jmPlayer.setFullScreen(MPLAYER_TYPE.PLAY);
        // set volume to 90%
        //jmPlayer.setVolume(MPLAYER_TYPE.PLAY,90);        
        try{
            //new Thread(new MplayerHalter(jmPlayer)).start();
        }catch(Exception e){
            System.err.println("Exception is "+e.getMessage());
        }
        System.err.println("Quitting");
        //jmPlayer.close(MPLAYER_TYPE.PLAY);
    }
}

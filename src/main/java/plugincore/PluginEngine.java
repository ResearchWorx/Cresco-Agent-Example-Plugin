package plugincore;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.apache.commons.configuration.SubnodeConfiguration;

import channels.RPCCall;
import shared.Clogger;
import shared.MsgEvent;
import shared.MsgEventType;
import shared.PluginImplementation;

public class PluginEngine {

    private static CommandExec commandExec;
    private static WatchDog wd;

    static Clogger clog;
    static ConcurrentLinkedQueue<MsgEvent> logOutQueue;
    static PluginConfig config;
    static String pluginName;
    static String pluginVersion;

    public static String plugin;
    public static String agent;
    public static String region;
    public static boolean RESTfulActive;
    public static RPCCall rpcc;
    public static Map<String, MsgEvent> rpcMap;
    //public static ConcurrentLinkedQueue<MsgEvent> msgOutQueue;
    public static ConcurrentLinkedQueue<MsgEvent> msgInQueue;

    public PluginEngine() {
        pluginName = "cresco-agent-example-plugin";
        clog = new Clogger(new ConcurrentLinkedQueue<MsgEvent>(), "init", "init", pluginName, Clogger.Level.Info);
    }

    public static void shutdown() {
        clog.info("Plugin Shutdown : Agent=" + agent + "pluginname=" + plugin);
        RESTfulActive = false;
        wd.timer.cancel(); //prevent rediscovery
        try {
            MsgEvent me = new MsgEvent(MsgEventType.CONFIG, region, null, null, "disabled");
            me.setParam("src_region", region);
            me.setParam("src_agent", agent);
            me.setParam("src_plugin", plugin);
            me.setParam("dst_region", region);

            //msgOutQueue.offer(me);
            msgInQueue.offer(me);
            //PluginEngine.rpcc.call(me);
            clog.debug("Sent disable message");
        } catch (Exception ex) {
            String msg2 = "Plugin Shutdown Failed: Agent=" + agent + "pluginname=" + plugin;
            clog.error(msg2);
        }
    }

    public static String getName() {
        return pluginName;
    }

    public static String getVersion() {
        String version;
        try {
            String jarFile = PluginImplementation.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            File file = new File(jarFile.substring(5, (jarFile.length() - 2)));
            FileInputStream fis = new FileInputStream(file);
            @SuppressWarnings("resource")
            JarInputStream jarStream = new JarInputStream(fis);
            Manifest mf = jarStream.getManifest();

            Attributes mainAttribs = mf.getMainAttributes();
            version = mainAttribs.getValue("Implementation-Version");
        } catch (Exception ex) {
            String msg = "Unable to determine Plugin Version " + ex.toString();
            clog.error(msg);
            version = "Unable to determine Version";
        }

        return pluginName + "." + version;

    }

    //steps to init the plugin
    public boolean initialize(ConcurrentLinkedQueue<MsgEvent> outQueue, ConcurrentLinkedQueue<MsgEvent> inQueue, SubnodeConfiguration configObj, String newRegion, String newAgent, String newPlugin) {
        //create logger
        clog = new Clogger(inQueue, newRegion, newAgent, newPlugin, Clogger.Level.Info);

        clog.trace("Call to initialize");
        clog.trace("Building rpcMap");
        rpcMap = new ConcurrentHashMap<>();
        clog.trace("Building rpcc");
        rpcc = new RPCCall();

        clog.trace("Building commandExec");
        commandExec = new CommandExec();


        //clog.trace("Building msgOutQueue");
        //ConcurrentLinkedQueue<MsgEvent> msgOutQueue = outQueue;
        clog.trace("Setting msgInQueue");
        msgInQueue = inQueue; //messages to agent should go here

        clog.trace("Setting Region");
        region = newRegion;
        clog.trace("Setting Agent");
        agent = newAgent;
        clog.trace("Setting Plugin");
        plugin = newPlugin;

        try {
            clog.trace("Building logOutQueue");
            logOutQueue = new ConcurrentLinkedQueue<>(); //create our own queue

            clog.trace("Checking msgInQueue");
            if (msgInQueue == null) {
                System.out.println("MsgInQueue==null");
                return false;
            }

            clog.trace("Building new PluginConfig");
            config = new PluginConfig(configObj);

            clog.info("Starting Example Plugin");
            RESTfulActive = true;
            try {

                /*
                        Put your execution code here
                 */

                System.out.println("\n\n\n\t" + config.getPath("message") + "\n\n");

                System.out.println("\t\t[packet_trace_args]" + config.getPath("packet_trace_args"));
                System.out.println("\t\t[location]" + config.getPath("location"));
                System.out.println("\t\t[device]" + config.getPath("device"));
                System.out.println("\t\t[amqp_server]" + config.getPath("amqp_server"));
                System.out.println("\t\t[amqp_login]" + config.getPath("amqp_login"));
                System.out.println("\t\t[amqp_password]" + config.getPath("amqp_password"));

                System.out.println("\n\n");

                clog.info("Completed Plugin Routine");


            } catch (Exception ex) {
                clog.error("Failed to load Plugin Execution Segment {}", ex.toString());
                return false;
            }

            clog.trace("Starting WatchDog");
            wd = new WatchDog();
            clog.trace("Successfully started plugin");
            return true;
        } catch (Exception ex) {
            String msg = "ERROR IN PLUGIN: : Region=" + region + " Agent=" + agent + " plugin=" + plugin + " " + ex.getMessage();
            ex.printStackTrace();
            clog.error("initialize {}", msg);
            //clog.error(msg);
            return false;
        }
    }

    public static void msgIn(MsgEvent me) {
        final MsgEvent ce = me;
        try {
            Thread thread = new Thread() {
                public void run() {
                    try {
                        MsgEvent re = commandExec.cmdExec(ce);
                        if (re != null) {
                            re.setReturn(); //reverse to-from for return
                            msgInQueue.offer(re); //send message back to queue
                        }

                    } catch (Exception ex) {
                        clog.error("Controller : PluginEngine : msgIn Thread: {}", ex.toString());
                    }
                }
            };
            thread.start();
        } catch (Exception ex) {
            clog.error("Controller : PluginEngine : msgIn Thread: {}", ex.toString());
        }
    }
}

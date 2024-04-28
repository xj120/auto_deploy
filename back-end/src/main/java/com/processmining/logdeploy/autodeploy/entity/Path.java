package com.processmining.logdeploy.autodeploy.entity;

import java.util.UUID;

public class Path {

    public static final String TOOL = "tool";

    public static String LOCAL_TOOL(String username) {
        String uuid = UUID.randomUUID().toString();
        return TOOL + "/" + username + "/" + uuid;
    }

    public static String DEFAULT_AGENT() {
        return TOOL + "/default/log_agent-0.0.3-SNAPSHOT.jar";
    }

    public static String DEFAULT_SERVER () {
        return TOOL + "/default/log_server-0.0.4-SNAPSHOT.jar";
    }

    public static String REMOTE_TOOL(String username) {
        return "/home/" + username + "/autodeploy/" + TOOL;
    }



    public static final String APPLICATION = "application";

    public static String LOCAL_APPLICATION(String username) {
        String uuid = UUID.randomUUID().toString();
        return APPLICATION + "/" + username + "/" + uuid;
    }

    public static String REMOTE_APPLICATION(String username) {
        return "/home/" + username + "/autodeploy/" + APPLICATION;
    }



    public static final String CONFIG = "config";

    public static String LOCAL_CONFIG(String username, String collectName) {
        return CONFIG + "/" + username + "/" + collectName;
    }

    public static String REMOTE_CONFIG(String username, String collectName) {
        return "/home/" + username + "/autodeploy/" + CONFIG + "/" + collectName;
    }

    public static String REMOTE_AGENT_CONFIG_XML(String username, String collectName) {
        return REMOTE_CONFIG(username, collectName) + "/agent-config.xml";
    }

    public static String REMOTE_CONFIG_XML(String username, String collectName) {
        return REMOTE_CONFIG(username, collectName) + "/config.xml";
    }



    public static final String LOG = "log";



    public static final String START_SERVER = "../shell/start_server.sh";
    public static final String STOP_SERVER = "../shell/stop_server.sh";

    public static String REMOTE_SHELL(String username) {
        return "/home/" + username + "/autodeploy/shell";
    }



    public static final String DISTRIBUTED = "distributed";

    public static String LOCAL_DISTRIBUTED_APPLICATION(String username, String applicationName) {
        return DISTRIBUTED + "/" + APPLICATION + "/" + username + "/" + applicationName;
    }

    public static String REMOTE_DISTRIBUTED_APPLICATION(String username) {
        return "/home/" + username + "/autodeploy/" + DISTRIBUTED + "/" + APPLICATION;
    }

    public static String LOCAL_DISTRIBUTED_APPLICATION_CONFIG(String username, String applicationName) {
        return LOCAL_DISTRIBUTED_APPLICATION(username, applicationName) + "/config";
    }

    public static String REMOTE_DISTRIBUTED_APPLICATION_CONFIG(String username, String applicationName, String configPath) {
        if (!configPath.startsWith("/"))
            configPath = "/" + configPath;
        return REMOTE_DISTRIBUTED_APPLICATION(username) + "/" + applicationName + configPath;
    }



    public static final String SCRIPT = "script";

    public static String LOCAL_SCRIPT(String username, String projectName, String applicationName) {
        if (applicationName.contains("."))
            applicationName = applicationName.substring(0, applicationName.indexOf('.'));
        return SCRIPT + "/" + username + "/" + projectName + "/" + applicationName;
    }

    public static String REMOTE_SCRIPT(String username, String applicationName) {
        if (applicationName.contains("."))
            applicationName = applicationName.substring(0, applicationName.indexOf('.'));
        return "/home/" + username + "/autodeploy/" + SCRIPT + "/" + applicationName;
    }

}

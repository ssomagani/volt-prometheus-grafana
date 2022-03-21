/*
 * This file is part of VoltDB.
 * Copyright (C) 2019-2021 VoltDB Inc.
 */

package org.voltdb.prometheus;

import java.io.File;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.voltdb.prometheus.VoltDBPrometheusMetricEngine.SupportedStatsSelectors;
import org.voltdb.utils.MiscUtils;

public class VoltDBPrometheusAgent {

    private static final int DEFAULT_VOLTDB_PORT = 21211;
    private static final int DEFAULT_WEBSERVER_PORT = 1234;

    public static void main(String[] args) {

        // Initialize parameter defaults
        String serverList = "localhost";
        int port = DEFAULT_VOLTDB_PORT;
        String user = ""; // for connection to VoltDB
        String password = ""; // likewise
        String credsFile = null; // alternative for user+password
        boolean delta = false;
        boolean sslEnabled = false;
        String sslConfigFile = null;
        int webserverPort = DEFAULT_WEBSERVER_PORT;
        String statsList = null;
        String skipStatsList = null;

        // Parse out parameters
        for (String arg : args) {
            if (arg.startsWith("--servers")) {
                serverList = extractArgString(arg);
            } else if (arg.startsWith("--port")) {
                port = extractArgInteger(arg, 1, 65535);
            } else if (arg.startsWith("--webserverport")) {
                webserverPort = extractArgInteger(arg, 1, 65535);
            } else if (arg.startsWith("--user")) {
                user = extractArgString(arg);
            } else if (arg.startsWith("--password")) {
                password = extractArgString(arg);
            } else if (arg.startsWith("--credentials")) {
                credsFile = extractArgString(arg);
            } else if (arg.startsWith("--skipstats")) {
                skipStatsList = extractArgString(arg);
            } else if (arg.startsWith("--stats")) {
                statsList = extractArgString(arg);
            } else if (arg.startsWith("--delta")) {
                delta = extractArgBoolean(arg);
            } else if (arg.startsWith("--ssl")) {
                sslConfigFile = extractOptionalArgString(arg);
                sslEnabled = true;
            } else {
                System.err.println("Error: invalid parameter " + arg);
                System.exit(1);
            }
        }

        if (statsList != null && skipStatsList != null) {
            System.err.println("Error: can't set both --stats and --skipstats.");
            System.exit(1);
        }

        Set<SupportedStatsSelectors> statsSet = parseStatsSelectors(statsList, skipStatsList);
        if (statsSet == null || statsSet.isEmpty()) {
            System.err.println("Error: no statistics to poll.");
            System.exit(1);
        }

        if (credsFile != null) {
            if (!user.isEmpty() || !password.isEmpty()) {
                System.err.println("Error: can't specify --credentials with --user or --password.");
                System.exit(1);
            }
            try {
                Properties props = MiscUtils.readPropertiesFromCredentials(credsFile);
                user = props.getProperty("username");
                password = props.getProperty("password");
            }
            catch (Exception ex) {
                System.err.println("Error: cannot read credentials file " + credsFile);
                System.exit(1);
            }
            if (user == null || user.isEmpty()) {
                System.err.println("Error: 'username' not found in credentials file " + credsFile);
                System.exit(1);
            }
            if (password == null) {
                password = "";
            }
        }

        if (user.isEmpty() && password.isEmpty()) {
            String envUser = System.getenv("VOLTDB_USERNAME");
            String envPwd = System.getenv("VOLTDB_PASSWORD");
            if (envUser != null && !envUser.isEmpty()) {
                user = envUser;
                password = envPwd != null ? envPwd : "";
                System.out.println("Using VoltDB credentials from environment variables");
            }
        }

        if (sslConfigFile != null) {
            File sslTemp = new File(sslConfigFile);
            if (!(sslTemp.isFile() && sslTemp.canRead())) {
                System.err.println("Error: cannot read SSL configuration file " + sslConfigFile);
                System.exit(1);
            }
        }

        System.out.printf("Serving %s%s metrics%nFrom VoltDB at %s port %d %s%n",
                          statsSet, (delta ? " delta" : ""),
                          serverList, port, sslEnabled ? "(SSL enabled)" : "");

        System.out.printf("Listening for connections on port %d%n%n", webserverPort);
        Server server = new Server(webserverPort);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);

        context.addServlet(new ServletHolder(new TopLevelServlet(serverList, port)), "/");
        context.addServlet(new ServletHolder(new PrometheusServlet(serverList, port, user, password, statsSet, delta, sslEnabled, sslConfigFile)), "/metrics");

        // Start the webserver.
        try {
            server.start();
            server.join();
        } catch (Exception ex) {
            System.err.println("Error: UNEXPECTED EXCEPTION");
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private static String extractOptionalArgString(String arg) {
        if (arg.indexOf('=') < 0) {
            return null;
        } else {
            return extractArgString(arg);
        }
    }

    private static String extractArgString(String arg) {
        String[] splitStrings = arg.split("=", 2);
        if (splitStrings[0].isEmpty()) {
            System.err.println("Error: invalid parameter value " + arg);
            System.exit(1);
        }
        if (splitStrings.length < 2 || splitStrings[1].isEmpty()) {
            System.err.println("Error: " + splitStrings[0] + " must have a value");
            System.exit(1);
        }
        return splitStrings[1];
    }

    private static int extractArgInteger(String arg, int min, int max) {
        String val = extractArgString(arg);
        int n = -1;
        try {
            n = Integer.parseInt(val);
            if (n < min || n > max) {
                System.err.println("Error: out-of-range integer in " + arg);
                System.exit(1);
            }
        } catch (NumberFormatException ex) {
            System.err.println("Error: invalid integer in " + arg);
            System.exit(1);
        }
        return n;
    }

    private static boolean extractArgBoolean(String arg) {
        String val = extractArgString(arg);
        boolean b = false;
        if (val.equalsIgnoreCase("true")) {
            b = true;
        }
        else if (!val.equalsIgnoreCase("false")) {
            System.err.println("Error: value is not 'true' or 'false' in " + arg);
            System.exit(1);
        }
        return b;
    }

    private static Set<SupportedStatsSelectors> parseStatsSelectors(String allowedStatsList, String skipStatsList) {
        Set<SupportedStatsSelectors> allstats = EnumSet.allOf(SupportedStatsSelectors.class);
        if (allowedStatsList != null) {
            return validateStats(allowedStatsList, allstats);
        } else if (skipStatsList != null) {
            allstats.removeAll(validateStats(skipStatsList, allstats));
            return allstats; // which is no longer all stats
        }
        return allstats;
    }

    private static Set<SupportedStatsSelectors> validateStats(String statsList, Set<SupportedStatsSelectors> allstats) {
        Set<SupportedStatsSelectors> sanitized = EnumSet.noneOf(SupportedStatsSelectors.class);
        boolean badName = false;
        for (String s : statsList.split(",")) {
            String name = s.trim();
            try {
                sanitized.add(SupportedStatsSelectors.valueOf(name));
            } catch (IllegalArgumentException e) {
                System.err.println("Error: unsupported statistics " + name);
                badName = true;
            }
        }
        if (badName) {
            System.err.println("Supported statistics are: " + allstats);
            System.exit(1);
        }
        return sanitized;
    }
}

/*
 * Copyright (C) 2020-2021 VoltDB Inc.
 * This file is part of VoltDB.
 */

package org.voltdb.prometheus;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.voltcore.logging.VoltLogger;
import org.voltdb.client.Client;
import org.voltdb.client.ClientConfig;
import org.voltdb.client.ClientFactory;

import io.prometheus.client.Gauge;

public class VoltDBPrometheusMetricEngine {

    private static final VoltLogger logger = new VoltLogger("CONSOLE");

    private static class ClientException extends Exception {
        public ClientException(String msg) {
            super(msg);
        }
    }

    /**
     * All possible supported statistics categories. The names here
     * are the same as used in the StatsSelector enum, since it
     * would be confusing to the user to have them be different.
     */
    public static enum SupportedStatsSelectors {
        COMMANDLOG,
        CPU,
        EXPORT,
        GC,
        IDLETIME,
        INDEX,
        INITIATOR, // may cause significant overhead
        IOSTATS,
        LATENCY,
        LIVECLIENTS,
        MEMORY,
        PROCEDURE,
        QUEUE,
        QUEUEPRIORITY,
        TABLE,
        ;
    }

    /**
     * Necessary fixed data for registering and gathering statistics.
     * This could be made part of the enum itself, but it seems preferable
     * to structure it as a separate map.
     */
    private static final EnumMap<SupportedStatsSelectors, StatsData> statsData =
        new EnumMap<>(SupportedStatsSelectors.class);

    private static class StatsData {
        Class<? extends AbstractStatsProcedureCallback> cbClass;
        // we may some day need other data here
    }

    private static void addStats(SupportedStatsSelectors selector,
                                 Class<? extends AbstractStatsProcedureCallback> cbClass) {
        StatsData data = new StatsData();
        data.cbClass = cbClass;
        statsData.put(selector, data);
    }

    static {
        addStats(SupportedStatsSelectors.COMMANDLOG,    CommandLogStatsCallback.class);
        addStats(SupportedStatsSelectors.CPU,           CPUStatsCallback.class);
        addStats(SupportedStatsSelectors.EXPORT,        ExportStatsCallback.class);
        addStats(SupportedStatsSelectors.GC,            GCStatsCallback.class);
        addStats(SupportedStatsSelectors.IDLETIME,      IdletimeStatsCallback.class);
        addStats(SupportedStatsSelectors.INDEX,         IndexStatsCallback.class);
        addStats(SupportedStatsSelectors.INITIATOR,     InitiatorStatsCallback.class);
        addStats(SupportedStatsSelectors.IOSTATS,       IOStatsCallback.class);
        addStats(SupportedStatsSelectors.LATENCY,       LatencyStatsCallback.class);
        addStats(SupportedStatsSelectors.LIVECLIENTS,   LiveClientsStatsCallback.class);
        addStats(SupportedStatsSelectors.MEMORY,        MemoryStatsCallback.class);
        addStats(SupportedStatsSelectors.PROCEDURE,     ProcedureStatsCallback.class);
        addStats(SupportedStatsSelectors.QUEUE,         QueueStatsCallback.class);
        addStats(SupportedStatsSelectors.QUEUEPRIORITY, QueuePriorityStatsCallback.class);
        addStats(SupportedStatsSelectors.TABLE,         TableStatsCallback.class);
    }

    /**
     * We only use Gauges to store metrics. Counters might seem plausible, but there's
     * no 'set' method, so there is no way to transfer the current value from VoltDB
     * statistics. Messing with arithmetic will work some of the time, but not if
     * the stats have been reset: Counters cannot be decreased.
     *
     * The map is keyed on the full metric name as known to Prometheus.
     */
    private final Map<String,Gauge> m_gaugeMap = new HashMap<>();

    /**
     * Values needed for VoltDB connection setup.
     */
    private final String m_servers;
    private final int m_port;
    private final String m_user;
    private final String m_password;
    private final boolean m_sslEnabled;
    private final String m_sslConfigFile;

    /**
     * Prometheus expects help text for metrics; maps from metric
     * name to the help text.
     */
    private final HashMap<String,String> m_helpText = new HashMap<>();

    /**
     * Callbacks for (only) the statistics we are monitoring.
     */
    private final EnumMap<SupportedStatsSelectors, AbstractStatsProcedureCallback> m_callbackMap =
        new EnumMap<>(SupportedStatsSelectors.class);

    /**
     * VoltDB client, connected and disconnected as needed during
     * statistics gathering.
     */
    private Client m_client = null;

    /**
     * Count of stats written this pass..
     */
    private int m_statCount = 0;

    /**
     * 'Interval' argument for @Statistics call, 0 or 1
     */
    private int m_interval = 0;

    /**
     * Create an agent for metrics reporting. This is called for each agent
     * thats defined in JSON document. One can have many clusters with servers
     * and port specified in JSON document.
      */
    public VoltDBPrometheusMetricEngine(String serverList, int port, String user, String password, boolean sslEnabled, String sslConfigFile) {
        m_servers = serverList;
        m_port = port;
        m_user = user;
        m_password = password;
        m_sslEnabled = sslEnabled;
        m_sslConfigFile = sslConfigFile;
    }

    /**
     * Initialization: sets 'interval' mode
     */
    public void setIntervalMode(boolean interval) {
        m_interval = (interval ? 1 : 0);
    }

    /**
     * Initialization: sets up callbacks for all allowed stats. Each
     * callback in turn will call us multiple times at registerMetric.
     */
    public void setSupportedStats(Set<SupportedStatsSelectors> statsSet) {
        for (SupportedStatsSelectors selector : statsSet) {
            logDebug("Initializing: %s", selector);
            m_callbackMap.put(selector, makeCallback(selector));
        }
    }

    private AbstractStatsProcedureCallback makeCallback(SupportedStatsSelectors selector) {
        try {
            StatsData data = statsData.get(selector);
            return data.cbClass.getConstructor(VoltDBPrometheusMetricEngine.class)
                               .newInstance(this);
        }
        catch (NullPointerException ex) {
            logError("Unsupported statistics '%s'", selector.name());
            throw new IllegalArgumentException(selector.name());
        }
        catch (Exception ex) {
            logError("Cannot instantiate callback for '%s' - %s", selector.name(), ex);
            throw new IllegalStateException(selector.name());
        }
    }

    public void registerMetric(String metricName, String... labels) {
        Gauge thisGauge = m_gaugeMap.get(metricName);
        if (thisGauge == null) {
            String helpText = m_helpText.get(metricName);
            if (helpText == null) {
                helpText = metricName;
            }
            if (labels != null) {
                thisGauge = Gauge.build()
                                 .name(metricName)
                                 .labelNames(labels)
                                 .help(helpText)
                                 .register();
            } else {
                thisGauge = Gauge.build()
                                 .name(metricName)
                                 .help(helpText)
                                 .register();
            }
            m_gaugeMap.put(metricName, thisGauge);
            logDebug("Adding metric %s", metricName);
        }
    }

    public void setHelp(String metricName, String help) {
        m_helpText.put(metricName, help);
    }

    public Set<String> getAllGaugeNames() {
        return m_gaugeMap.keySet();
    }

    /**
     * This method is called by our Prometheus servlet in response to
     * a 'GET' from Prometheus itself.
     *
     * We create a VoltDB client vonnection if it does not exist.
     * On any failure we dump the client, and retry on next attempt.
     *
     * We initiate asynchronous statistics collection and wait for
     * all of those to complete, before proceeding further. The
     * callback classes use reportMetric to record metric values.
     */
    public boolean gatherMetrics() {
        boolean success = false;
        int errCnt = 0;
        try {
            if (m_client == null) {
                m_client = createClient();
            }
            logDebug("Starting metrics collection for server %s", m_servers);

            m_statCount = 0;
            final long starttimeMS = System.currentTimeMillis();
            final CountDownLatch cbwaiters = new CountDownLatch(m_callbackMap.size());

            // Issue calls to VoltDB for all supported statistics;
            // callback will be executed when response arrives.
            for (Map.Entry<SupportedStatsSelectors, AbstractStatsProcedureCallback> ent : m_callbackMap.entrySet()) {
                SupportedStatsSelectors selector = ent.getKey();
                AbstractStatsProcedureCallback cb = ent.getValue();
                cb.setWaiters(cbwaiters);
                errCnt += callProcedure(cb, "@Statistics", selector.name(), m_interval);
            }

            // Let's wait for callbacks to finish.
            m_client.drain();
            cbwaiters.await();
            if (errCnt > 0) {
                m_client.close();
                m_client = null;
                logError("Error collecting metrics for server %s, will reconnect on next polling cycle",
                         m_servers);
                success = (m_statCount > 0);
            }

            else {
                logDebug("Finished metrics collection for server %s; collected %d stats in %d msec",
                         m_servers, m_statCount, System.currentTimeMillis() - starttimeMS);
                success = true;
            }

        } catch (ClientException ex) {
            // Can't create VoltDB client, try again next time
            logError(ex.getMessage());
        } catch (Exception ex) {
            // We failed in collecting, reconnect next time
            logError("Failed to poll for statistics for server %s: %s", m_servers, ex.getMessage());
            deleteClient(m_client);
            m_client = null;
        }

        return success;
    }

    /*
     * Set up VoltDB client object, connects to one or more VoltDB servers.
     */
    private Client createClient() throws ClientException {
        Client client = null;
        String srv = null;
        try {
            ClientConfig config = new ClientConfig(m_user, m_password);
            if (m_sslEnabled) {
                if (m_sslConfigFile != null) {
                    config.setTrustStoreConfigFromPropertyFile(m_sslConfigFile);
                } else {
                    config.setTrustStoreConfigFromDefault();
                }
                config.enableSSL();
            }
            config.setProcedureCallTimeout(0); // infinite
            config.setReconnectOnConnectionLoss(true);
            client = ClientFactory.createClient(config);
            for (String s : m_servers.split(",")) {
                srv = s.trim(); // for error reporting
                logInfo("Connecting to VoltDB server %s", srv);
                client.createConnection(srv, m_port);
                logInfo("Connected to VoltDB server %s", srv);
                srv = null;
            }
        }
        catch (Exception ex) {
            deleteClient(client);
            String error;
            if (srv != null) {
                error = String.format("Unable to connect to VoltDB server %s on port %s: %s", srv, m_port, ex.getMessage());
            } else {
                error = String.format("Unable to set up VoltDB client: %s", ex.getMessage());
            }
            throw new ClientException(error);
        }
        return client;
    }

    /*
     * Used by gatherMetrics to initiate collection of one statistics class
     */
    private int callProcedure(AbstractStatsProcedureCallback cb, String procName, String statsName, int interval) {
        int err = 0;
        try {
            m_client.callProcedure(cb, procName, statsName, interval);
        } catch (Exception ex) {
            logError("Failed to call %s procedure: %s", statsName, ex.getMessage());
            if (cb.cbwaiters != null) {
                cb.cbwaiters.countDown();
            }
            err = 1;
        }
        return err;
    }

    /**
     * Called from statistics callback classes to report values
     * of metrics.
     */
    public void reportMetric(String metricName, double value, String... labelValues) {
        Gauge thisGauge = m_gaugeMap.get(metricName);
        if (thisGauge != null) {
            if (labelValues != null) {
                thisGauge.labels(labelValues).set(value);
            } else {
                thisGauge.set(value);
            }
            logDebug("%s = %s", metricName, value);
        } else {
            logDebug("Couldn't find metric: %s", metricName);
        }
        m_statCount++;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#finalize()
     */
    @Override
    protected void finalize() throws Throwable {
        if (m_client != null) {
            m_client.drain();
            m_client.close();
            m_client = null;
        }
        super.finalize();
    }

    /**
     * Connection cleanup. The public method may be called from
     * the servlet to disconnect client; the private method is
     * used for internal cleanup.
     */
    public void disconnect() {
        deleteClient(m_client);
        m_client = null;
    }

    private static void deleteClient(Client client) {
        if (client != null) {
            try {
                client.drain();
                client.close();
            } catch (Exception e) {
                logWarning("Error when closing client connection: %s", e.getMessage());
            }
        }
    }

    /*
     * Logging helpers
     */
    private static void logDebug(String msg, Object... args) {
        if (logger.isDebugEnabled()) {
            if (args != null && args.length != 0) {
                msg = String.format(msg, args);
            }
            logger.debug(msg);
        }
    }

    private static void logInfo(String msg, Object... args) {
        if (args != null && args.length != 0) {
            msg = String.format(msg, args);
        }
        logger.info(msg);
    }

    private static void logWarning(String msg, Object... args) {
        if (args != null && args.length != 0) {
            msg = String.format(msg, args);
        }
        logger.warn(msg);
    }

    private static void logError(String msg, Object... args) {
        if (args != null && args.length != 0) {
            msg = String.format(msg, args);
        }
        logger.error(msg);
    }
}

/*
 * This file is part of VoltDB.
 * Copyright (C) 2019-2021 VoltDB Inc.
 */

package org.voltdb.prometheus;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.voltdb.prometheus.VoltDBPrometheusMetricEngine.SupportedStatsSelectors;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.MetricsServlet;
import io.prometheus.client.exporter.common.TextFormat;

/**
 * Servlet that collects metrics and makes them available to Prometheus.
 * The servlet is created from mainline agent code, after parsing command
 * arguments and doing other initialization. It in turn creates an
 * instance of the VoltDBPrometheusMetricEngine, which does most of
 * the actual work of metrics collection.
 *
 * The base MetricsServlet class is defined by the Prometheus client
 * library.
 */
public class PrometheusServlet extends MetricsServlet {

    /**
     * Used to do the actual metric generation
     */
    final private VoltDBPrometheusMetricEngine m_engine;

    /**
     * The engine is not reentrant, so (a) we serialize requests, and
     * (b) in order to not repeatedly incur long timeouts in connectivity
     * failures, we just repeat the previous results for requests that
     * arrive back-to-back.
     */
    final private static long MIN_GATHER_INTERVAL = 1500;
    private long m_lastGatherEndTime;
    private boolean m_lastGatherSucceeded;

    /**
     * Constructor: the main task here is to create the
     * VoltDB metrics engine.
     */
    public PrometheusServlet(String serverList, int port, String user, String password,
                             Set<SupportedStatsSelectors> statsSet, boolean interval,
                             boolean sslEnabled, String sslConfig) {
        m_engine = new VoltDBPrometheusMetricEngine(serverList, port, user, password, sslEnabled, sslConfig);
        m_engine.setSupportedStats(statsSet);
        m_engine.setIntervalMode(interval);
    }

    /**
     * Responds to a 'GET' request from Prometheus. Collects stats
     * from VoltDB (via the generic metrics agent) and calls us
     * back at reportMetric (below) to record current gauge values.
     *
     * @see io.prometheus.client.exporter.MetricsServlet#doGet(javax.servlet.http.HttpServletRequest,
     *      javax.servlet.http.HttpServletResponse)
     */
    @Override  // MetricsServlet
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        synchronized (m_engine) {
            long now = System.currentTimeMillis();
            if (now >= m_lastGatherEndTime + MIN_GATHER_INTERVAL || now < m_lastGatherEndTime) {
                m_lastGatherSucceeded = m_engine.gatherMetrics();
                m_lastGatherEndTime = System.currentTimeMillis();
            }
            if (m_lastGatherSucceeded) {
                continueGet(req, resp);
            } else {
                resp.setStatus(HttpServletResponse.SC_GATEWAY_TIMEOUT);
            }
        }
    }

    /*
     * Variant of MetricsServlet.doGet() that does not rely on the request
     * to determine the list of metric names, but instead supplies all
     * known names. Possibly temporary until I figure out how it is
     * supposed to work.
     */
    private void continueGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType(TextFormat.CONTENT_TYPE_004);
        CollectorRegistry reg = CollectorRegistry.defaultRegistry;
        Set<String> names = m_engine.getAllGaugeNames(); // this makes all the difference
        try (Writer writer = resp.getWriter()) {
            TextFormat.write004(writer, reg.filteredMetricFamilySamples(names));
            writer.flush();
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see javax.servlet.GenericServlet#init()
     */
    @Override
    public void init() throws ServletException {
        super.init();
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#finalize()
     */
    @Override
    protected void finalize() throws Throwable {
        if (m_engine != null) {
            m_engine.disconnect();
        }
        super.finalize();
    }
}

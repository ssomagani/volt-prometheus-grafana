/*
 * This file is part of VoltDB.
 * Copyright (C) 2019-2020 VoltDB Inc.
 */

package org.voltdb.prometheus;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.voltcore.logging.VoltLogger;
import org.voltdb.VoltTable;
import org.voltdb.client.ClientResponse;
import org.voltdb.client.ProcedureCallback;

/**
 * This is the base class for all statistics callback classes,
 * each one of which processes a single VoltDB statistics selector.
 *
 * Derived classes must:
 *
 * 1. From their constructors, call addMetric for each known metric.
 *    This registers metric names with the prometheus client.
 *
 * 2. Implement processResult to process the results of a call to
 *    @Statistics in VoltDB. This in turn reports current values
 *    by calling reportMetric.
 */
public abstract class AbstractStatsProcedureCallback implements ProcedureCallback {
    private static final VoltLogger logger = new VoltLogger("CONSOLE");

    private final VoltDBPrometheusMetricEngine engine;
    private final String namespace;

    protected final Map<String, Metric> metricMap = new HashMap<>();
    protected CountDownLatch cbwaiters;

    /**
     * Holds data about mapping from VoltDB statistics to Prometheus metrics.
     * The metricMap is keyed by VoltDB column name; the value holds the
     * name as known to Prometheus.
     */
    protected static class Metric {
        final private String name;
        final private double multiplier;

        /**
         * @param namespace  - prefix of metric names. must start with `voltdb_` then the name of
         *                     the statistics selector, e.g. voltdb_export.
         * @param key        - name of the metric, doesn't have to be the same as the stats column name.
         * @param unit       - metrics don't have to have a unit. If they are, prefer standard units
         *                     like "bytes", "seconds" or "ratio".
         * @param multiplier - if voltdb statistics adopts different unit, use multiplier to normalize it.
         */
        public Metric(String namespace, String key, String unit, double multiplier) {
            String name = namespace + "_" + key;
            if (unit != null) {
                name += "_" + unit;
            }
            this.name = name;
            this.multiplier = multiplier;
        }
    }
    /**
     * @param engine - metrics engine, common to all statistics classes
     * @param namespace - voltdb_something prefix for this statistics class
     */
    protected AbstractStatsProcedureCallback(VoltDBPrometheusMetricEngine engine, String namespace) {
        this.engine = engine;
        this.namespace = namespace;
    }

    /*
     * Registration functions
     * TODO: add help text support [text => engine.setHelp(metric, text)]
     */
    protected Metric addMetric(String column, String key) {
        return addMetric(column, key, null, 1.0);
    }

    protected Metric addMetric(String column, String key, String unit) {
        return addMetric(column, key, unit, 1.0);
    }

    protected Metric addMetric(String column, String key, String unit, double multiplier) {
        Metric metric = new Metric(namespace, key, unit, multiplier);
        metricMap.put(column, metric);
        return metric;
    }

    protected <E extends Enum<E>> Metric addMetric(E column, String key) {
        return addMetric(column, key, null, 1.0);
    }

    protected <E extends Enum<E>> Metric addMetric(E column, String key, String unit) {
        return addMetric(column, key, unit, 1.0);
    }

    protected <E extends Enum<E>> Metric addMetric(E column, String key, String unit, double multiplier) {
        Metric metric = new Metric(namespace, key, unit, multiplier);
        metricMap.put(column.name(), metric);
        return metric;
    }

    protected void registerAll(String... labels) {
        for (Metric metric : metricMap.values()) {
            engine.registerMetric(metric.name, labels);
        }
    }

    protected void registerMetric(Metric metric, String... labels) {
        engine.registerMetric(metric.name, labels);
    }

    /*
     * Collecting and reporting functions
     */

    public void setWaiters(CountDownLatch cbwaiters) {
        this.cbwaiters = cbwaiters;
    }

    @Override
    public void clientCallback(ClientResponse response) throws Exception {
        try {
            if (response.getStatus() == ClientResponse.SUCCESS) {
                VoltTable tbls[] = response.getResults();
                processResult(tbls);
            }
        } catch (Throwable ex) {
            logger.error("Failed to process stats for namespace " + namespace + ": " + ex.getMessage());
        } finally {
            cbwaiters.countDown();
        }
    }

    public abstract void processResult(VoltTable[] tables);

    protected void reportMetric(Metric metric, Number value, String... labelValues) {
        engine.reportMetric(metric.name, value.longValue() * metric.multiplier, labelValues);
    }
}

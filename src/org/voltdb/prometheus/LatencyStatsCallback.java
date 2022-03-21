/*
 * This file is part of VoltDB.
 * Copyright (C) 2020 VoltDB Inc.
 */

package org.voltdb.prometheus;

import java.util.Map;

import org.voltdb.StatsSource.StatsCommon;
import org.voltdb.VoltTable;
import org.voltdb.VoltType;
import org.voltdb.client.ProcedureCallback;

/**
 * Latency metrics:
 *
 *  voltdb_latency_tps
 *  voltdb_latency_median_seconds
 *  voltdb_latency_95th_seconds
 *  voltdb_latency_99th_seconds
 *  voltdb_latency_three_nines_seconds
 *  voltdb_latency_four_nines_seconds
 *  voltdb_latency_five_nines_seconds
 *  voltdb_latency_max_seconds
 *
 * Labels:
 *
 *  hostname
 */
public class LatencyStatsCallback extends AbstractStatsProcedureCallback implements ProcedureCallback {

    public enum Latency {
        INTERVAL                    ("INTERVAL", VoltType.INTEGER),
        COUNT                       ("COUNT", VoltType.INTEGER),
        TPS                         ("TPS", VoltType.INTEGER),
        P50                         ("P50", VoltType.BIGINT),
        P95                         ("P95", VoltType.BIGINT),
        P99                         ("P99", VoltType.BIGINT),
        // Those three columns in statistics are "P99.9", "P99.99" and "P99.999", which cannot be the enum names.
        // Use the alias name as workaround.
        P99_9                       ("P99.9", VoltType.BIGINT),
        P99_99                      ("P99.99", VoltType.BIGINT),
        P99_999                     ("P99.999", VoltType.BIGINT),
        MAX                         ("MAX", VoltType.BIGINT);

        public final VoltType m_type;
        public final String m_alias;
        Latency(String name, VoltType type)
        {
            m_alias = name;
            m_type = type;
        }

        public String alias() {
            return m_alias;
        }
    }
    
    public LatencyStatsCallback(VoltDBPrometheusMetricEngine engine) {
        super(engine, "voltdb_latency");

        addMetric(Latency.TPS, "tps");
        addMetric(Latency.P50, "median", "seconds", 0.000_001); // stats in microseconds
        addMetric(Latency.P95, "95th", "seconds", 0.000_001); // stats in microseconds
        addMetric(Latency.P99, "99th", "seconds", 0.000_001); // stats in microseconds
        // Those three columns in statistics are "P99.9", "P99.99" and "P99.999", which cannot be the enum names.
        addMetric(Latency.P99_9.alias(), "three_nines", "seconds", 0.000_001); // stats in microseconds
        addMetric(Latency.P99_99.alias(), "four_nines", "seconds", 0.000_001); // stats in microseconds
        addMetric(Latency.P99_999.alias(), "five_nines", "seconds", 0.000_001); // stats in microseconds

        addMetric(Latency.MAX, "max", "seconds", 0.000_001); // stats in microseconds

        registerAll("hostname");
    }

    @Override
    public void processResult(VoltTable[] tables) {
        VoltTable table = tables[0];
        while (table.advanceRow()) {
            String hostname = table.getString(StatsCommon.HOSTNAME.name());
            for (Map.Entry<String, Metric> e : metricMap.entrySet()) {
                reportMetric(e.getValue(), table.getLong(e.getKey()), hostname);
            }
        }
    }
}

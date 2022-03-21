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
 * CPU metrics:
 *
 *  voltdb_cpu_usage_percent
 *
 * Labels:
 *
 *  hostname
 */
public class CPUStatsCallback extends AbstractStatsProcedureCallback implements ProcedureCallback {
	
	public enum CPU {
        PERCENT_USED                (VoltType.BIGINT);

        public final VoltType m_type;
        CPU(VoltType type) { m_type = type; }
    }

    public CPUStatsCallback(VoltDBPrometheusMetricEngine engine) {
        super(engine, "voltdb_cpu");
        addMetric(CPU.PERCENT_USED, "usage", "percent");
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

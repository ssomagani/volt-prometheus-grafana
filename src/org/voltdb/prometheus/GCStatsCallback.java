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
 * GC metrics:
 *
 *  voltdb_gc_newgen_gc_count
 *  voltdb_gc_newgen_avg_gc_time_seconds
 *  voltdb_gc_oldgen_gc_count
 *  voltdb_gc_oldgen_avg_gc_time_seconds
 *
 * Labels:
 *
 *  hostname
 */
public class GCStatsCallback extends AbstractStatsProcedureCallback implements ProcedureCallback {

	public enum GC {
        NEWGEN_GC_COUNT             (VoltType.INTEGER),
        NEWGEN_AVG_GC_TIME          (VoltType.BIGINT),
        OLDGEN_GC_COUNT             (VoltType.INTEGER),
        OLDGEN_AVG_GC_TIME          (VoltType.BIGINT);

        public final VoltType m_type;
        GC(VoltType type) { m_type = type; }
    }
	
    public GCStatsCallback(VoltDBPrometheusMetricEngine engine) {
        super(engine, "voltdb_gc");

        addMetric(GC.NEWGEN_GC_COUNT, "newgen_gc_count");
        addMetric(GC.NEWGEN_AVG_GC_TIME, "newgen_avg_gc_time", "seconds", 0.001); // stats in milliseconds
        addMetric(GC.OLDGEN_GC_COUNT, "oldgen_gc_count");
        addMetric(GC.OLDGEN_AVG_GC_TIME, "oldgen_avg_gc_time", "seconds", 0.001); // stats in milliseconds

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

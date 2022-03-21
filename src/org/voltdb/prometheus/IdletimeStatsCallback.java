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
 * Idletime metrics:
 *
 *  voltdb_idletime_queue_empty_count
 *  voltdb_idletime_idle_ratio
 *  voltdb_idletime_avg_wait_time_seconds
 *  voltdb_idletime_min_wait_time_seconds
 *  voltdb_idletime_max_wait_time_seconds
 *  voltdb_idletime_stddev_wait_time_seconds
 *
 * Labels:
 *
 *  hostname, siteid
 */
public class IdletimeStatsCallback extends AbstractStatsProcedureCallback implements ProcedureCallback {

	 public enum Idletime {
	        COUNT                       (VoltType.BIGINT),
	        PERCENT                     (VoltType.FLOAT),
	        AVG                         (VoltType.BIGINT),
	        MIN                         (VoltType.BIGINT),
	        MAX                         (VoltType.BIGINT),
	        STDDEV                      (VoltType.BIGINT);

	        public final VoltType m_type;
	        Idletime(VoltType type) { m_type = type; }
	    }

    public enum SiteStats {
        SITE_ID                   (VoltType.INTEGER);

        public final VoltType m_type;
        SiteStats(VoltType type) { m_type = type; }
    }
    
    public IdletimeStatsCallback(VoltDBPrometheusMetricEngine engine) {
        super(engine, "voltdb_idletime");

        addMetric(Idletime.COUNT, "queue_empty_count");
        addMetric(Idletime.PERCENT, "idle_ratio");
        addMetric(Idletime.AVG, "avg_wait_time", "seconds", 0.000001); // stats in microseconds
        addMetric(Idletime.MIN, "min_wait_time", "seconds", 0.000001); // stats in microseconds
        addMetric(Idletime.MAX, "max_wait_time", "seconds", 0.000001); // stats in microseconds
        addMetric(Idletime.STDDEV, "stddev_wait_time", "seconds", 0.000001); // stats in microseconds

        registerAll("hostname", "siteid");
    }

    @Override
    public void processResult(VoltTable[] tables) {
        VoltTable table = tables[0];
        while (table.advanceRow()) {
            String hostname = table.getString(StatsCommon.HOSTNAME.name());
            String siteid = String.valueOf(table.getLong(SiteStats.SITE_ID.name()));

            for (Map.Entry<String, Metric> e : metricMap.entrySet()) {
                String col = e.getKey();
                Metric m = e.getValue();
                if (col.equalsIgnoreCase(Idletime.PERCENT.name())) {
                    reportMetric(m, table.getDouble(col), hostname, siteid);
                } else {
                    reportMetric(m, table.getLong(col), hostname, siteid);
                }
            }
        }
    }
}

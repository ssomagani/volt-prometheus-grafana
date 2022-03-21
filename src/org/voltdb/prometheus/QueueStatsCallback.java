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
 * Queue metrics:
 *
 *  voltdb_queue_depth
 *  voltdb_queue_poll_count
 *  voltdb_queue_avg_wait_seconds
 *  voltdb_queue_max_wait_seconds
 *
 * Labels:
 *
 *  hostname, siteid
 */
public class QueueStatsCallback extends AbstractStatsProcedureCallback implements ProcedureCallback {

    public enum SiteStats {
        SITE_ID                   (VoltType.INTEGER);

        public final VoltType m_type;
        SiteStats(VoltType type) { m_type = type; }
    }
    
	public enum Queue {
        CURRENT_DEPTH           (VoltType.INTEGER),
        POLL_COUNT              (VoltType.BIGINT),
        AVG_WAIT                (VoltType.BIGINT),
        MAX_WAIT                (VoltType.BIGINT);
        public final VoltType m_type;
        Queue(VoltType type) { m_type = type; }
    }
	
    public QueueStatsCallback(VoltDBPrometheusMetricEngine engine) {
        super(engine, "voltdb_queue");

        addMetric(Queue.CURRENT_DEPTH, "depth");
        addMetric(Queue.POLL_COUNT, "poll_count");
        addMetric(Queue.AVG_WAIT, "avg_wait", "seconds", 0.000001); // stats in microseconds
        addMetric(Queue.MAX_WAIT, "max_wait", "seconds", 0.000001); // stats in microseconds

        registerAll("hostname", "siteid");
    }

    @Override
    public void processResult(VoltTable[] tables) {
        VoltTable table = tables[0];
        while (table.advanceRow()) {
            String hostname = table.getString(StatsCommon.HOSTNAME.name());
            String siteid = String.valueOf(table.getLong(SiteStats.SITE_ID.name()));

            for (Map.Entry<String, Metric> e : metricMap.entrySet()) {
                reportMetric(e.getValue(), table.getLong(e.getKey()), hostname, siteid);
            }
        }
    }
}

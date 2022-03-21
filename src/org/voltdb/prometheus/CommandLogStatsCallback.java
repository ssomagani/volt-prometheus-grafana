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
 * Commandlog metrics:
 *
 *  voltdb_commandlog_outstanding_bytes
 *  voltdb_commandlog_outstanding_txns
 *  voltdb_commandlog_in_use_segments
 *  voltdb_commandlog_segments
 *  voltdb_commandlog_fsync_interval_seconds
 *
 * Label:
 *
 *  hostname
 */
public class CommandLogStatsCallback extends AbstractStatsProcedureCallback implements ProcedureCallback {
	
	public enum CommandLogCols {
        OUTSTANDING_BYTES           (VoltType.BIGINT),
        OUTSTANDING_TXNS            (VoltType.BIGINT),
        IN_USE_SEGMENT_COUNT        (VoltType.INTEGER),
        SEGMENT_COUNT               (VoltType.INTEGER),
        FSYNC_INTERVAL              (VoltType.INTEGER);

        public final VoltType m_type;
        CommandLogCols(VoltType type) { m_type = type; }
    }

    public CommandLogStatsCallback(VoltDBPrometheusMetricEngine engine) {
        super(engine, "voltdb_commandlog");

        addMetric(CommandLogCols.OUTSTANDING_BYTES, "outstanding", "bytes");
        addMetric(CommandLogCols.OUTSTANDING_TXNS, "outstanding", "txns");
        addMetric(CommandLogCols.IN_USE_SEGMENT_COUNT, "in_use_segments");
        addMetric(CommandLogCols.SEGMENT_COUNT, "segments");
        addMetric(CommandLogCols.FSYNC_INTERVAL, "fsync_interval", "seconds", 0.001); // stats measured in milliseconds

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

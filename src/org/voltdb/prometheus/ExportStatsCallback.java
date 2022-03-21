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
 * Export metrics:
 *
 *  voltdb_export_total_queued_tuples_count
 *  voltdb_export_pending_tuples_count
 *  voltdb_export_last_queued_timestamp
 *  voltdb_export_last_acked_timestamp
 *  voltdb_export_avg_latency_seconds
 *  voltdb_export_max_latency_seconds
 *  voltdb_export_missing_tuples_count
 *
 * Labels:
 *
 *  hostname, partitionid, source, target
 */
public class ExportStatsCallback extends AbstractStatsProcedureCallback implements ProcedureCallback {

	public enum Export {
        SITE_ID                     (VoltType.INTEGER),
        PARTITION_ID                (VoltType.BIGINT),
        SOURCE                      (VoltType.STRING),
        TARGET                      (VoltType.STRING),
        ACTIVE                      (VoltType.STRING),
        TUPLE_COUNT                 (VoltType.BIGINT),
        TUPLE_PENDING               (VoltType.BIGINT),
        LAST_QUEUED_TIMESTAMP       (VoltType.TIMESTAMP),
        LAST_ACKED_TIMESTAMP        (VoltType.TIMESTAMP),
        AVERAGE_LATENCY             (VoltType.BIGINT),
        MAX_LATENCY                 (VoltType.BIGINT),
        QUEUE_GAP                   (VoltType.BIGINT),
        STATUS                      (VoltType.STRING);

        public final VoltType m_type;
        Export(VoltType type) { m_type = type; }
    }

    public ExportStatsCallback(VoltDBPrometheusMetricEngine engine) {
        super(engine, "voltdb_export");

        addMetric(Export.TUPLE_COUNT, "total_queued_tuples_count");
        addMetric(Export.TUPLE_PENDING, "pending_tuples_count");
        addMetric(Export.LAST_QUEUED_TIMESTAMP, "last_queued_timestamp");
        addMetric(Export.LAST_ACKED_TIMESTAMP, "last_acked_timestamp");
        addMetric(Export.AVERAGE_LATENCY, "avg_latency", "seconds", 0.001); // stats in milliseconds
        addMetric(Export.MAX_LATENCY, "max_latency", "seconds", 0.001); // stats in milliseconds
        addMetric(Export.QUEUE_GAP, "missing_tuples_count");

        registerAll("hostname", "partitionid", "source", "target");
    }

    @Override
    public void processResult(VoltTable[] tables) {
        VoltTable table = tables[0];
        while (table.advanceRow()) {
            String hostname = table.getString(StatsCommon.HOSTNAME.name());
            String partitionid = String.valueOf(table.getLong(Export.PARTITION_ID.name()));
            String source = table.getString(Export.SOURCE.name());
            String target = table.getString(Export.TARGET.name());

            for (Map.Entry<String, Metric> e : metricMap.entrySet()) {
                String col = e.getKey();
                Metric m = e.getValue();
                if (col.equalsIgnoreCase(Export.LAST_QUEUED_TIMESTAMP.name())
                        || col.equalsIgnoreCase(Export.LAST_ACKED_TIMESTAMP.name())) {
                    reportMetric(m, table.getTimestampAsLong(col), hostname, partitionid, source, target);
                } else {
                    reportMetric(m, table.getLong(col), hostname, partitionid, source, target);
                }
            }
        }
    }
}

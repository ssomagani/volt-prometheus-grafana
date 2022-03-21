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
 * INDEX metrics:
 *
 * voltdb_index_entries_count
 * voltdb_index_memory_estimate_bytes
 *
 * Labels:
 *
 *  hostname, partitionid, indexname, tablename, indextype
 *
 */
public class IndexStatsCallback extends AbstractStatsProcedureCallback implements ProcedureCallback {

	public enum Index {
        PARTITION_ID                (VoltType.BIGINT),
        INDEX_NAME                  (VoltType.STRING),
        TABLE_NAME                  (VoltType.STRING),
        INDEX_TYPE                  (VoltType.STRING),
        IS_UNIQUE                   (VoltType.TINYINT),
        IS_COUNTABLE                (VoltType.TINYINT),
        ENTRY_COUNT                 (VoltType.BIGINT),
        MEMORY_ESTIMATE             (VoltType.BIGINT);

        public final VoltType m_type;
        Index(VoltType type) { m_type = type; }
    }
	
    public IndexStatsCallback(VoltDBPrometheusMetricEngine engine) {
        super(engine, "voltdb_index");

        addMetric(Index.ENTRY_COUNT, "entries_count");
        addMetric(Index.MEMORY_ESTIMATE, "memory_estimate", "bytes", 1024); // stats in kilobytes

        registerAll("hostname", "partitionid", "indexname", "tablename", "indextype");
    }

    @Override
    public void processResult(VoltTable[] tables) {
        VoltTable table = tables[0];
        while (table.advanceRow()) {
            String hostname = table.getString(StatsCommon.HOSTNAME.name());
            String partitionid = String.valueOf(table.getLong(Index.PARTITION_ID.name()));
            String indexname = table.getString(Index.INDEX_NAME.name());
            String tablename = table.getString(Index.TABLE_NAME.name());
            String indextype = table.getString(Index.INDEX_TYPE.name());

            for (Map.Entry<String, Metric> e : metricMap.entrySet()) {
                reportMetric(e.getValue(), table.getLong(e.getKey()),
                        hostname, partitionid, indexname, tablename, indextype);
            }
        }
    }
}

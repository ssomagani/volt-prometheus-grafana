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
 * TableStats metrics:
 *
 *  voltdb_table_tuple_count
 *  voltdb_table_tuple_allocated_memory_bytes
 *  voltdb_table_inline_tuple_bytes
 *  voltdb_table_non_inline_data_bytes
 *
 * Labels:
 *
 *  hostname, partitionid, tablename, type
 */
public class TableStatsCallback extends AbstractStatsProcedureCallback implements ProcedureCallback {

	public enum Table {
        PARTITION_ID            (VoltType.BIGINT),
        TABLE_NAME              (VoltType.STRING),
        TABLE_TYPE              (VoltType.STRING),
        TUPLE_COUNT             (VoltType.BIGINT),
        TUPLE_ALLOCATED_MEMORY  (VoltType.BIGINT),
        TUPLE_DATA_MEMORY       (VoltType.BIGINT),
        STRING_DATA_MEMORY      (VoltType.BIGINT),
        DR                      (VoltType.STRING),
        EXPORT                  (VoltType.STRING);

        public final VoltType m_type;
        Table(VoltType type) { m_type = type; }
    }

	
    public TableStatsCallback(VoltDBPrometheusMetricEngine engine) {
        super(engine, "voltdb_table");

        addMetric(Table.TUPLE_COUNT, "tuple_count");
        addMetric(Table.TUPLE_ALLOCATED_MEMORY, "tuple_allocated_memory", "bytes", 1024); // stats in kilobytes
        addMetric(Table.TUPLE_DATA_MEMORY, "inline_tuple", "bytes", 1024); // stats in kilobytes
        addMetric(Table.STRING_DATA_MEMORY, "non_inline_data", "bytes", 1024); // stats in kilobytes

        registerAll( "hostname", "partitionid", "tablename", "type");
    }

    @Override
    public void processResult(VoltTable[] tables) {
        VoltTable table = tables[0];
        while (table.advanceRow()) {
            String hostname = table.getString(StatsCommon.HOSTNAME.name());
            String partitionid = String.valueOf(table.getLong(Table.PARTITION_ID.name()));
            String tablename = table.getString(Table.TABLE_NAME.name());
            String type = table.getString(Table.TABLE_TYPE.name());

            for (Map.Entry<String, Metric> e : metricMap.entrySet()) {
                reportMetric(e.getValue(), table.getLong(e.getKey()), hostname, partitionid, tablename, type);
            }
        }
    }
}

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
 * Memory metrics:
 *
 *  voltdb_memory_rss_bytes
 *  voltdb_memory_javaused_bytes
 *  voltdb_memory_javaunused_bytes
 *  voltdb_memory_tupledata_bytes
 *  voltdb_memory_tupleallocated_bytes
 *  voltdb_memory_indexmemory_bytes
 *  voltdb_memory_stringmemory_bytes
 *  voltdb_memory_tuplecount
 *  voltdb_memory_pooledmemory_bytes
 *  voltdb_memory_physicalmemory_bytes
 *  voltdb_memory_javamaxheap_bytes
 *
 * Labels:
 *
 *  hostname
 */
public class MemoryStatsCallback extends AbstractStatsProcedureCallback implements ProcedureCallback {

	public enum Memory {
        RSS                     (VoltType.INTEGER),
        JAVAUSED                (VoltType.INTEGER),
        JAVAUNUSED              (VoltType.INTEGER),
        TUPLEDATA               (VoltType.BIGINT),
        TUPLEALLOCATED          (VoltType.BIGINT),
        INDEXMEMORY             (VoltType.BIGINT),
        STRINGMEMORY            (VoltType.BIGINT),
        TUPLECOUNT              (VoltType.BIGINT),
        POOLEDMEMORY            (VoltType.BIGINT),
        PHYSICALMEMORY          (VoltType.BIGINT),
        JAVAMAXHEAP             (VoltType.INTEGER);

        public final VoltType m_type;
        Memory(VoltType type) { m_type = type; }
    }

	
    public MemoryStatsCallback(VoltDBPrometheusMetricEngine engine) {
        super(engine, "voltdb_memory");
        for (Memory col : Memory.values()) {
            if (col == Memory.TUPLECOUNT) {
                addMetric(col, col.name().toLowerCase());
            } else {
                // memory stats is measured in kilobytes, metric wants in bytes;
                addMetric(col, col.name().toLowerCase(), "bytes", 1024);
            }
        }

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

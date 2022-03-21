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
 * ProcedureStats metrics:
 *
 *  voltdb_procedure_invocations
 *  voltdb_procedure_min_execution_time_seconds
 *  voltdb_procedure_max_execution_time_seconds
 *  voltdb_procedure_avg_execution_time_seconds
 *  voltdb_procedure_min_result_size_bytes
 *  voltdb_procedure_max_result_size_bytes
 *  voltdb_procedure_avg_result_size_bytes
 *  voltdb_procedure_min_parameter_size_bytes
 *  voltdb_procedure_max_parameter_size_bytes
 *  voltdb_procedure_avg_parameter_size_bytes
 *  voltdb_procedure_aborts
 *  voltdb_procedure_failures
 *
 * Labels:
 *
 *  hostname, partitionid, procedure
 */
public class ProcedureStatsCallback extends AbstractStatsProcedureCallback implements ProcedureCallback {

	public enum ProcedureColumns {
        PARTITION_ID            (VoltType.INTEGER),
        PROCEDURE               (VoltType.STRING),
        STATEMENT               (VoltType.STRING),
        INVOCATIONS             (VoltType.BIGINT),
        TIMED_INVOCATIONS       (VoltType.BIGINT),
        MIN_EXECUTION_TIME      (VoltType.BIGINT),
        MAX_EXECUTION_TIME      (VoltType.BIGINT),
        AVG_EXECUTION_TIME      (VoltType.BIGINT),
        MIN_RESULT_SIZE         (VoltType.INTEGER),
        MAX_RESULT_SIZE         (VoltType.INTEGER),
        AVG_RESULT_SIZE         (VoltType.INTEGER),
        MIN_PARAMETER_SET_SIZE  (VoltType.INTEGER),
        MAX_PARAMETER_SET_SIZE  (VoltType.INTEGER),
        AVG_PARAMETER_SET_SIZE  (VoltType.INTEGER),
        ABORTS                  (VoltType.BIGINT),
        FAILURES                (VoltType.BIGINT),
        TRANSACTIONAL           (VoltType.TINYINT);

        public final VoltType m_type;
        ProcedureColumns(VoltType type) { m_type = type; }
    }

    public ProcedureStatsCallback(VoltDBPrometheusMetricEngine engine) {
        super(engine, "voltdb_procedure");

        addMetric(ProcedureColumns.INVOCATIONS, "invocations");
        addMetric(ProcedureColumns.MIN_EXECUTION_TIME, "min_execution_time", "seconds", 0.000_000_001);
        addMetric(ProcedureColumns.MAX_EXECUTION_TIME, "max_execution_time", "seconds", 0.000_000_001); // stats in nanoseconds
        addMetric(ProcedureColumns.AVG_EXECUTION_TIME, "avg_execution_time", "seconds", 0.000_000_001); // stats in nanoseconds
        addMetric(ProcedureColumns.MIN_RESULT_SIZE, "min_result_size", "bytes");
        addMetric(ProcedureColumns.MAX_RESULT_SIZE, "max_result_size", "bytes");
        addMetric(ProcedureColumns.AVG_RESULT_SIZE, "avg_result_size", "bytes");
        addMetric(ProcedureColumns.MIN_PARAMETER_SET_SIZE, "min_parameter_size", "bytes");
        addMetric(ProcedureColumns.MAX_PARAMETER_SET_SIZE, "max_parameter_size", "bytes");
        addMetric(ProcedureColumns.AVG_PARAMETER_SET_SIZE, "avg_parameter_size", "bytes");
        addMetric(ProcedureColumns.ABORTS, "aborts");
        addMetric(ProcedureColumns.FAILURES, "failures");

        registerAll("hostname", "partitionid", "procedure");
    }

    @Override
    public void processResult(VoltTable[] tables) {
        VoltTable table = tables[0];
        while (table.advanceRow()) {
            String hostname = table.getString(StatsCommon.HOSTNAME.name());
            String partitionid = String.valueOf(table.getLong(ProcedureColumns.PARTITION_ID.name()));
            String procedure = table.getString(ProcedureColumns.PROCEDURE.name());

            for (Map.Entry<String, Metric> e : metricMap.entrySet()) {
                reportMetric(e.getValue(), table.getLong(e.getKey()), hostname, partitionid, procedure);
            }
        }
    }
}

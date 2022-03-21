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
 * InitatorStats metrics:
 *
 *  voltdb_initiator_invocations
 *  voltdb_initiator_execution_time_seconds
 *  voltdb_initiator_min_execution_time_seconds
 *  voltdb_initiator_max_execution_time_seconds
 *  voltdb_initiator_aborts
 *  voltdb_initiator_failures
 *
 * Labels:
 *
 *  hostname, cnxhostname, procname
 */
public class InitiatorStatsCallback extends AbstractStatsProcedureCallback implements ProcedureCallback {
	
	public enum Initiator {
        CONNECTION_ID               (VoltType.BIGINT),
        CONNECTION_HOSTNAME         (VoltType.STRING),
        PROCEDURE_NAME              (VoltType.STRING),
        INVOCATIONS                 (VoltType.BIGINT),
        AVG_EXECUTION_TIME          (VoltType.INTEGER),
        MIN_EXECUTION_TIME          (VoltType.INTEGER),
        MAX_EXECUTION_TIME          (VoltType.INTEGER),
        ABORTS                      (VoltType.BIGINT),
        FAILURES                    (VoltType.BIGINT);

        public final VoltType m_type;
        Initiator(VoltType type) { m_type = type; }
    }

    public InitiatorStatsCallback(VoltDBPrometheusMetricEngine engine) {
        super(engine, "voltdb_initiator");

        addMetric(Initiator.INVOCATIONS, "invocations");
        addMetric(Initiator.AVG_EXECUTION_TIME, "execution_time", "seconds", 0.001); //stats in milliseconds
        addMetric(Initiator.MIN_EXECUTION_TIME, "min_execution_time", "seconds", 0.001); //stats in milliseconds
        addMetric(Initiator.MAX_EXECUTION_TIME, "max_execution_time", "seconds", 0.001); //stats in milliseconds
        addMetric(Initiator.ABORTS, "aborts");
        addMetric(Initiator.FAILURES, "failures");

        registerAll("hostname", "cnxhostname", "procname");
    }

    @Override
    public void processResult(VoltTable[] tables) {
        VoltTable table = tables[0];
        while (table.advanceRow()) {
            String hostname = table.getString(StatsCommon.HOSTNAME.name());
            String cnxhostname = table.getString(Initiator.CONNECTION_HOSTNAME.name());
            String procname = table.getString(Initiator.PROCEDURE_NAME.name());
            for (Map.Entry<String, Metric> e : metricMap.entrySet()) {
                reportMetric(e.getValue(), table.getLong(e.getKey()), hostname, cnxhostname, procname);
            }
        }
    }
}

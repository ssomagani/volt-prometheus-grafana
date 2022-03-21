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
 * IOStats metrics:
 *
 *  voltdb_iostats_received_bytes
 *  voltdb_iostats_received_messages
 *  voltdb_iostats_sent_bytes
 *  voltdb_iostats_sent_messages
 *
 * Labels:
 *
 *  hostname, cnxhostname
 */
public class IOStatsCallback extends AbstractStatsProcedureCallback implements ProcedureCallback {

	public enum IoStats {
        CONNECTION_ID               (VoltType.BIGINT),
        CONNECTION_HOSTNAME         (VoltType.STRING),
        BYTES_READ                  (VoltType.BIGINT),
        MESSAGES_READ               (VoltType.BIGINT),
        BYTES_WRITTEN               (VoltType.BIGINT),
        MESSAGES_WRITTEN            (VoltType.BIGINT);

        public final VoltType m_type;
        IoStats(VoltType type) { m_type = type; }
    }
	
    public IOStatsCallback(VoltDBPrometheusMetricEngine engine) {
        super(engine, "voltdb_iostats");

        addMetric(IoStats.BYTES_READ, "received", "bytes");
        addMetric(IoStats.MESSAGES_READ, "received_messages");
        addMetric(IoStats.BYTES_WRITTEN, "sent", "bytes");
        addMetric(IoStats.MESSAGES_WRITTEN, "sent_messages");

        registerAll("hostname", "cnxhostname");
    }

    @Override
    public void processResult(VoltTable[] tables) {
        VoltTable table = tables[0];
        while (table.advanceRow()) {
            String hostname = table.getString(StatsCommon.HOSTNAME.name());
            String cnxhostname = table.getString(IoStats.CONNECTION_HOSTNAME.name());
            for (Map.Entry<String, Metric> e : metricMap.entrySet()) {
                reportMetric(e.getValue(), table.getLong(e.getKey()), hostname, cnxhostname);
            }
        }
    }
}

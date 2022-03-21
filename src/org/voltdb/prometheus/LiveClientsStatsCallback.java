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
 * LiveClient metrics:
 *
 *  voltdb_liveclients_outstanding_request_bytes
 *  voltdb_liveclients_outstanding_reponse_messages
 *  voltdb_liveclients_outstanding_txns
 *
 * Labels:
 *
 *  hostname, cnxhostname, admin
 */
public class LiveClientsStatsCallback extends AbstractStatsProcedureCallback implements ProcedureCallback {

	public enum LiveClients {
        CONNECTION_ID                   (VoltType.BIGINT),
        CLIENT_HOSTNAME                 (VoltType.STRING),
        ADMIN                           (VoltType.TINYINT),
        OUTSTANDING_REQUEST_BYTES       (VoltType.BIGINT),
        OUTSTANDING_RESPONSE_MESSAGES   (VoltType.BIGINT),
        OUTSTANDING_TRANSACTIONS        (VoltType.BIGINT);

        public final VoltType m_type;
        LiveClients(VoltType type) { m_type = type; }
    }

    public LiveClientsStatsCallback(VoltDBPrometheusMetricEngine engine) {
        super(engine, "voltdb_liveclients");

        addMetric(LiveClients.OUTSTANDING_REQUEST_BYTES, "outstanding_request", "bytes");
        addMetric(LiveClients.OUTSTANDING_RESPONSE_MESSAGES, "outstanding_reponse_messages");
        addMetric(LiveClients.OUTSTANDING_TRANSACTIONS, "outstanding_txns");

        registerAll("hostname", "cnxhostname", "admin");
    }

    @Override
    public void processResult(VoltTable[] tables) {
        VoltTable table = tables[0];
        while (table.advanceRow()) {
            String hostname = table.getString(StatsCommon.HOSTNAME.name());
            String cnxhostname = table.getString(LiveClients.CLIENT_HOSTNAME.name());
            String admin = String.valueOf(table.getLong(LiveClients.ADMIN.name()));
            for (Map.Entry<String, Metric> e : metricMap.entrySet()) {
                reportMetric(e.getValue(), table.getLong(e.getKey()), hostname, cnxhostname, admin);
            }
        }
    }
}

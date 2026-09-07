package com.crypto.execution.service;

import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.Instant;

/** FIX-122: preserve the reviewed evidence with the Proven record. Replay purge/reset
 * can reuse run IDs, so a saved Proven trade must not resolve diagnostics by run ID later. */
public final class Fix122ProvenSnapshot {
    private Fix122ProvenSnapshot() {}
    public static void capture(JdbcTemplate jdbc,long provenId,Long runId,String symbol,Instant from,Instant to) {
        jdbc.update("""
                UPDATE proven_analyzed_trade SET fix122_diagnostics_json = JSON_OBJECT(
                    'scope', 'PROVEN',
                    'enabled', (SELECT enabled FROM fix122_replay_revision WHERE test_run_id=?),
                    'rows', COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT(
                        'evaluated_at', d.evaluated_at, 'signal_id', d.signal_id,
                        'stage', d.stage, 'payload', d.payload))
                        FROM fix122_evaluation d WHERE d.symbol=? AND d.evaluated_at BETWEEN ? AND ?
                        AND ((? IS NULL AND d.test_run_id IS NULL) OR d.test_run_id=?)), JSON_ARRAY()))
                WHERE id=?
                """,runId,symbol,Timestamp.from(from),Timestamp.from(to),runId,runId,provenId);
    }
}

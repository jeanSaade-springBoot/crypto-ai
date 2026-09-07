package com.crypto.execution.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** FIX-122: bounded, non-locking committed ledger reads; no managed-position row locks.
 * STOP_LOSS is the terminal reason written by executeSignalLinkedExit. The distinct
 * POSITION_STOP_LOSS and partial-harvest reasons are deliberately not included.
 */
@Service
public class StopLossEvidenceStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate read;
    private final TransactionTemplate write;

    public StopLossEvidenceStore(DataSource dataSource, PlatformTransactionManager manager) {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.setQueryTimeout(2);
        read = new TransactionTemplate(manager);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        read.setReadOnly(true);
        read.setTimeout(2);
        write = new TransactionTemplate(manager);
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setTimeout(2);
    }

    public StopLossEvidencePolicy.Boundary latest(String symbol, Instant asOf) {
        return read.execute(status -> jdbc.query("""
                SELECT id, executed_at FROM wallet_trade
                WHERE symbol=? AND status='EXECUTED' AND side='SELL'
                  AND execution_reason='STOP_LOSS' AND executed_at<=?
                ORDER BY executed_at DESC, id DESC LIMIT 1
                """, (rs, row) -> new StopLossEvidencePolicy.Boundary(rs.getLong(1),
                rs.getTimestamp(2).toInstant()), symbol, Timestamp.from(asOf))
                .stream().findFirst().orElse(null));
    }

    public void append(Long runId, String symbol, Long signalId, Instant at, String stage, String payload) {
        // Separate audit transaction survives a caller rollback; it records an evaluation,
        // never asserts that a BUY/SELL actually executed. No FK to wallet rows is acquired.
        write.executeWithoutResult(status -> jdbc.update("""
                INSERT INTO fix122_evaluation(test_run_id,symbol,signal_id,evaluated_at,stage,payload)
                VALUES (?,?,?,?,?,?)
                """, runId, symbol, signalId, Timestamp.from(at), stage, payload));
    }

    public List<Map<String,Object>> replayDetails(long runId) {
        return jdbc.queryForList("SELECT * FROM fix122_evaluation WHERE test_run_id=? ORDER BY id", runId);
    }

    public List<Map<String,Object>> productionDetails(String symbol, Instant from, Instant to) {
        return jdbc.queryForList("""
                SELECT * FROM fix122_evaluation WHERE test_run_id IS NULL AND symbol=?
                AND evaluated_at>=? AND evaluated_at<=? ORDER BY id
                """, symbol, Timestamp.from(from), Timestamp.from(to));
    }
}

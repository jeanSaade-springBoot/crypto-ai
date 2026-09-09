package com.crypto.execution.processing;

import com.crypto.domain.TradeSignal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** FIX-127: claims are durable, completion joins the business transaction. An
 * interrupted RUNNING record is review-only; it is never blindly replayed. */
@Service
public class SignalProcessingStore {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SignalProcessingStore.class);
    private static final int QUARANTINE_BATCH_SIZE = 50;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate independent;
    public SignalProcessingStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        independent = new TransactionTemplate(manager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        independent.setTimeout(3);
    }
    public record Work(long signalId, String symbol, String interval, Instant candleOpenTime,
                       ProcessingOrigin origin, String status, int attempts, String owner) {}
    public void register(TradeSignal signal, ProcessingOrigin origin) {
        requireTransaction();
        if (signal == null || signal.getId() == null || signal.getCandleOpenTime() == null
                || signal.getSymbol() == null || signal.getInterval() == null) {
            throw new IllegalArgumentException("FIX-127 requires persisted exact signal lineage");
        }
        Instant now = Instant.now();
        // Duplicate callers must not reset status, origin, attempts or ownership.
        jdbc.update("""
                INSERT INTO signal_processing_work
                (signal_id,symbol,interval_code,candle_open_time,origin,status,attempts,next_attempt_at,updated_at)
                VALUES (?,?,?,?,?,'PENDING',0,?,?)
                ON DUPLICATE KEY UPDATE signal_id=signal_id
                """, signal.getId(), signal.getSymbol(), signal.getInterval(), Timestamp.from(signal.getCandleOpenTime()),
                origin.name(), Timestamp.from(now.plusSeconds(30)), Timestamp.from(now));
    }
    public Work claim(long id, boolean background) {
        return independent.execute(tx -> {
            Work work = locked(id);
            if (work == null || !("PENDING".equals(work.status()) || "RETRYABLE_FAILURE".equals(work.status()))) return null;
            if (background && !work.origin().automaticRecovery()) return null;
            if (work.attempts() >= 2) return null;
            String owner = UUID.randomUUID().toString();
            jdbc.update("UPDATE signal_processing_work SET status='RUNNING',attempts=attempts+1,owner_token=?,updated_at=? WHERE signal_id=?",
                    owner, Timestamp.from(Instant.now()), id);
            return new Work(id,work.symbol(),work.interval(),work.candleOpenTime(),work.origin(),"RUNNING",work.attempts()+1,owner);
        });
    }
    public Work locked(long id) {
        requireTransaction();
        List<Work> rows = jdbc.query("SELECT * FROM signal_processing_work WHERE signal_id=? FOR UPDATE", (rs,n) ->
                new Work(rs.getLong("signal_id"),rs.getString("symbol"),rs.getString("interval_code"),
                        rs.getTimestamp("candle_open_time").toInstant(),ProcessingOrigin.valueOf(rs.getString("origin")),
                        rs.getString("status"),rs.getInt("attempts"),rs.getString("owner_token")),id);
        return rows.isEmpty() ? null : rows.get(0);
    }
    public void complete(Work work, Long positionId) {
        requireTransaction();
        // Retain the last failure even after recovery; attempts=2 must remain explainable.
        int changed = jdbc.update("""
                UPDATE signal_processing_work SET status='COMPLETED',completed_at=?,updated_at=?,
                paper_position_id=?,owner_token=NULL
                WHERE signal_id=? AND status='RUNNING' AND owner_token=?
                """, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), positionId, work.signalId(),work.owner());
        if (changed != 1) throw new IllegalStateException("FIX-127 lost processing ownership");
    }
    public int finishWithoutBusiness(Work work, String status, String stage, String error) {
        return independent.execute(tx -> jdbc.update("""
                UPDATE signal_processing_work SET status=?,failure_stage=?,error_message=?,owner_token=NULL,
                updated_at=?,next_attempt_at=? WHERE signal_id=? AND status='RUNNING' AND owner_token=?
                """,status,stage,error,Timestamp.from(Instant.now()),Timestamp.from(Instant.now().plusSeconds(30)),work.signalId(),work.owner()));
    }
    public List<Long> due() {
        return jdbc.query("""
                SELECT signal_id FROM signal_processing_work WHERE status IN ('PENDING','RETRYABLE_FAILURE')
                AND origin IN ('WORKER','RECOVERY') AND attempts<2 AND next_attempt_at<=?
                ORDER BY next_attempt_at,signal_id LIMIT 50
                """,(rs,n)->rs.getLong(1),Timestamp.from(Instant.now()));
    }
    public record QuarantineCandidate(long signalId, String owner, Instant updatedAt) {}

    /** FIX-128: discovery is non-locking and bounded; never bulk UPDATE the RUNNING
     * secondary-index range. The observed owner/version is rechecked under a PK lock. */
    public List<QuarantineCandidate> quarantineCandidates(Instant cutoff) {
        return independent.execute(tx -> jdbc.query("""
                SELECT signal_id,owner_token,updated_at FROM signal_processing_work
                WHERE status='RUNNING' AND updated_at<? ORDER BY signal_id LIMIT ?
                """, (rs,n) -> new QuarantineCandidate(rs.getLong(1),rs.getString(2),rs.getTimestamp(3).toInstant()),
                Timestamp.from(cutoff),QUARANTINE_BATCH_SIZE));
    }

    /** FIX-128: one candidate, one independent transaction. Lock the primary key
     * first, then conditionally update. A completed, renewed or differently owned
     * record must never be overwritten by an earlier discovery snapshot. */
    public boolean quarantineCandidate(QuarantineCandidate candidate, Instant cutoff) {
        return Boolean.TRUE.equals(independent.execute(tx -> {
            Work current = locked(candidate.signalId());
            if (current == null || !"RUNNING".equals(current.status())
                    || !java.util.Objects.equals(current.owner(),candidate.owner())) return false;
            return jdbc.update("""
                    UPDATE signal_processing_work SET status='REVIEW_REQUIRED',failure_stage='INTERRUPTED_ATTEMPT',
                    error_message='[FIX-128] Interrupted processing requires review; no automatic replay',
                    owner_token=NULL,updated_at=?
                    WHERE signal_id=? AND status='RUNNING' AND updated_at=? AND updated_at<?
                    """,Timestamp.from(Instant.now()),candidate.signalId(),Timestamp.from(candidate.updatedAt()),
                    Timestamp.from(cutoff)) == 1;
        }));
    }

    public int quarantineInterrupted() {
        Instant cutoff = Instant.now().minusSeconds(300);
        int changed = 0;
        // Each call completes commit/rollback before the next candidate. A failed
        // row remains untouched and may be examined on a later scheduled scan.
        for (QuarantineCandidate candidate : quarantineCandidates(cutoff)) {
            try {
                if (quarantineCandidate(candidate,cutoff)) {
                    changed++;
                    log.warn("[FIX-128][QUARANTINED] signalId={}; review only, no automatic replay",candidate.signalId());
                }
            } catch (RuntimeException ex) {
                log.error("[FIX-128][QUARANTINE_ROW_FAILED] signalId={}; continuing scan",candidate.signalId(),ex);
            }
        }
        return changed;
    }
    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("FIX-127 operation requires a transaction");
    }
}

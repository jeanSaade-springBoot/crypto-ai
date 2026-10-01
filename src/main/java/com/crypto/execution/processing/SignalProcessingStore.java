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
        register(signal,origin,null);
    }
    /** FIX-132: explicit source ownership is committed with registration, never
     * reconstructed from a candle match or the age of a work row. */
    public void register(TradeSignal signal, ProcessingOrigin origin, Long sourceEventId) {
        requireTransaction();
        if (signal == null || signal.getId() == null || signal.getCandleOpenTime() == null
                || signal.getSymbol() == null || signal.getInterval() == null) {
            throw new IllegalArgumentException("FIX-127 requires persisted exact signal lineage");
        }
        if(sourceEventId!=null) {
            var delivery=jdbc.queryForList("SELECT symbol,interval_code,candle_open_time,closed FROM shared_market_event_delivery WHERE source_event_id=?",sourceEventId);
            if(delivery.size()!=1)throw new IllegalStateException("FIX-132 missing delivery ownership");
            var d=delivery.getFirst();
            if(!signal.getSymbol().equals(d.get("symbol")) || !signal.getInterval().equals(d.get("interval_code"))
                || !signal.getCandleOpenTime().equals(((Timestamp)d.get("candle_open_time")).toInstant())
                || !(Boolean.TRUE.equals(d.get("closed")) || "1".equals(String.valueOf(d.get("closed")))))
                throw new IllegalStateException("FIX-132 processing ownership lineage mismatch");
        }
        Instant now = Instant.now();
        // Duplicate callers must not reset status, origin, attempts or ownership.
        jdbc.update("""
                INSERT INTO signal_processing_work
                (signal_id,symbol,interval_code,candle_open_time,origin,status,attempts,next_attempt_at,updated_at,source_event_id)
                VALUES (?,?,?,?,?,'PENDING',0,?,?,?)
                ON DUPLICATE KEY UPDATE signal_id=signal_id
                """, signal.getId(), signal.getSymbol(), signal.getInterval(), Timestamp.from(signal.getCandleOpenTime()),
                origin.name(), Timestamp.from(now.plusSeconds(30)), Timestamp.from(now),sourceEventId);
        Long registered=jdbc.queryForObject("SELECT source_event_id FROM signal_processing_work WHERE signal_id=?",Long.class,signal.getId());
        if(!java.util.Objects.equals(registered,sourceEventId))throw new IllegalStateException("FIX-132 refuses processing ownership reassignment");
        if(sourceEventId!=null)log.info("[FIX-132][PROCESSING_OWNER] event={}, signal={}",sourceEventId,signal.getId());
    }
    public boolean exists(long id) { return jdbc.queryForObject("SELECT COUNT(*) FROM signal_processing_work WHERE signal_id=?",Integer.class,id)>0; }
    public void requireSourceOwner(long id, Long expected) {
        Long actual=jdbc.queryForObject("SELECT source_event_id FROM signal_processing_work WHERE signal_id=?",Long.class,id);
        if(!java.util.Objects.equals(actual,expected))throw new IllegalStateException("[FIX-132][OWNER_MISMATCH] signal="+id);
        if(expected!=null) {
            var rows=jdbc.queryForList("SELECT s.* FROM shared_market_event_delivery d JOIN shared_market_consumer_state s ON s.symbol=d.symbol WHERE d.source_event_id=? AND d.analysis_status='RUNNING'",expected);
            if(rows.size()!=1 || !com.crypto.shared.SharedCutoverPolicy.approved(rows.getFirst()))
                throw new IllegalStateException("[FIX-132][OWNER_NOT_READY] signal="+id);
        }
    }
    public Work claim(long id, boolean background) {
        return independent.execute(tx -> {
            Work work = locked(id);
            if (work == null || !("PENDING".equals(work.status()) || "RETRYABLE_FAILURE".equals(work.status()) || "SYMBOL_LOCK_RETRY".equals(work.status()))) return null;
            if (background && !work.origin().automaticRecovery()) return null;
            // FIX-140: four attempts only for attributed, proven symbol-lock retries.
            if ("SYMBOL_LOCK_RETRY".equals(work.status())) {
                if(work.attempts()>=4)return null;
                Integer due=jdbc.queryForObject("SELECT COUNT(*) FROM signal_processing_work WHERE signal_id=? AND source_event_id IS NOT NULL AND next_attempt_at<=?",Integer.class,id,Timestamp.from(Instant.now()));
                if(due!=1)return null;
            } else if (work.attempts() >= 2) return null;
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
    /** FIX-140: retry evidence is persisted after rollback, under the exact attempt owner.
     * No connection is held during the 1/3/10-second scheduling delay. */
    public int finishSymbolLockFailure(Work work,String status,String error) {
        long delay=switch(work.attempts()) {case 1->1;case 2->3;default->10;};
        return independent.execute(tx->jdbc.update("UPDATE signal_processing_work SET status=?,failure_stage='INITIAL_SYMBOL_LOCK_ROLLED_BACK',error_message=?,owner_token=NULL,updated_at=?,next_attempt_at=? WHERE signal_id=? AND status='RUNNING' AND owner_token=?",
            status,error,Timestamp.from(Instant.now()),Timestamp.from(Instant.now().plusSeconds(delay)),work.signalId(),work.owner()));
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

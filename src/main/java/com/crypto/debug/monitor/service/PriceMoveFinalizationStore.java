package com.crypto.debug.monitor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** FIX-130: enqueue never acquires the global execution gate. A busy worker
 * delays retrospective results only, never snapshot handoff or new-block collection.
 * The gate does NOT expire into automatic takeover: uncertain ownership needs review.
 */
@Service
public class PriceMoveFinalizationStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate shortTx;
    private final ObjectMapper json = new ObjectMapper();
    public record Job(long id, String owner, int attempts, PriceMoveBlockSnapshot snapshot) {}
    public PriceMoveFinalizationStore(DataSource source, PlatformTransactionManager manager) {
        jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(2);
        shortTx = new TransactionTemplate(manager);
        shortTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        shortTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        shortTx.setTimeout(2);
    }
    public void enqueue(PriceMoveBlockSnapshot snapshot) {
        final String payload;
        try { payload = json.writeValueAsString(snapshot); }
        catch (Exception ex) { throw new IllegalArgumentException("Snapshot serialization failed", ex); }
        // REQUIRES_NEW completes commit before the caller resets its tracker.
        shortTx.executeWithoutResult(tx -> {
            jdbc.update("""
                INSERT INTO price_move_finalization_work
                (symbol,block_start,snapshot_json,status,attempts,next_attempt_at,created_at,updated_at)
                VALUES (?,?,?,'FINALIZATION_PENDING',0,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE id=id
                """, snapshot.symbol(),Timestamp.from(Instant.parse(snapshot.blockStart())),payload);
            String saved = jdbc.queryForObject("SELECT snapshot_json FROM price_move_finalization_work WHERE symbol=? AND block_start=?",
                    String.class,snapshot.symbol(),Timestamp.from(Instant.parse(snapshot.blockStart())));
            // Exact idempotence only: conflicting snapshots cannot silently replace committed facts.
            if (!payload.equals(saved)) throw new IllegalStateException("Conflicting completed-block snapshot");
        });
    }
    public Job claim() {
        return shortTx.execute(tx -> {
            var gate = jdbc.queryForMap("SELECT active_job_id FROM price_move_finalization_gate WHERE id=1 FOR UPDATE");
            if (gate.get("active_job_id") != null) {
                // Mark stale ownership visibly, but retain the gate to prevent overlapping queries.
                jdbc.update("""
                    UPDATE price_move_finalization_work SET status='REVIEW_REQUIRED',
                    last_error='Ownership exceeded 30 minutes; stop all finalizers before recovery',updated_at=CURRENT_TIMESTAMP(6)
                    WHERE id=? AND status='FINALIZING' AND started_at < ?
                    """,gate.get("active_job_id"),Timestamp.from(Instant.now().minusSeconds(1800)));
                return null;
            }
            var ids = jdbc.queryForList("""
                SELECT id FROM price_move_finalization_work
                WHERE status IN ('FINALIZATION_PENDING','RETRYABLE_FAILURE') AND next_attempt_at<=CURRENT_TIMESTAMP(6)
                ORDER BY next_attempt_at,id LIMIT 1
                """,Long.class);
            if (ids.isEmpty()) return null;
            long id=ids.get(0); String owner=UUID.randomUUID().toString();
            jdbc.update("UPDATE price_move_finalization_work SET status='FINALIZING',owner_token=?,attempts=attempts+1,started_at=CURRENT_TIMESTAMP(6),updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",owner,id);
            jdbc.update("UPDATE price_move_finalization_gate SET active_job_id=?,owner_token=? WHERE id=1",id,owner);
            var row=jdbc.queryForMap("SELECT snapshot_json,attempts FROM price_move_finalization_work WHERE id=?",id);
            try { return new Job(id,owner,((Number)row.get("attempts")).intValue(),json.readValue((String)row.get("snapshot_json"),PriceMoveBlockSnapshot.class)); }
            catch (Exception ex) {
                jdbc.update("UPDATE price_move_finalization_work SET status='REVIEW_REQUIRED',last_error='Invalid snapshot JSON',updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",id);
                jdbc.update("UPDATE price_move_finalization_gate SET active_job_id=NULL,owner_token=NULL WHERE id=1");
                return null; // No finalization started; malformed work must not poison other symbols.
            }
        });
    }
    // Called INSIDE the same transaction that writes final explanations. A failed
    // ownership check rolls back explanations too; a later worker cannot be overwritten.
    public void complete(Job job) {
        jdbc.queryForMap("SELECT active_job_id FROM price_move_finalization_gate WHERE id=1 FOR UPDATE");
        if (jdbc.update("UPDATE price_move_finalization_work SET status='FINALIZED',finished_at=CURRENT_TIMESTAMP(6),updated_at=CURRENT_TIMESTAMP(6),last_error=NULL WHERE id=? AND owner_token=? AND status='FINALIZING'",job.id(),job.owner()) != 1)
            throw new IllegalStateException("Finalization ownership changed");
        if (jdbc.update("UPDATE price_move_finalization_gate SET active_job_id=NULL,owner_token=NULL WHERE id=1 AND active_job_id=? AND owner_token=?",job.id(),job.owner()) != 1)
            throw new IllegalStateException("Finalization gate ownership changed");
    }
    public void failed(Job job, boolean rollbackProven, String error) {
        shortTx.executeWithoutResult(tx -> {
            jdbc.queryForMap("SELECT active_job_id FROM price_move_finalization_gate WHERE id=1 FOR UPDATE");
            // Only a proven rollback can automatically retry and release the global gate.
            String status=rollbackProven && job.attempts()<3 ? "RETRYABLE_FAILURE" : "REVIEW_REQUIRED";
            int changed=jdbc.update("UPDATE price_move_finalization_work SET status=?,next_attempt_at=?,last_error=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=? AND owner_token=? AND status='FINALIZING'",
                    status,Timestamp.from(Instant.now().plusSeconds(60L*job.attempts())),error.substring(0,Math.min(1000,error.length())),job.id(),job.owner());
            if (changed==1 && rollbackProven)
                jdbc.update("UPDATE price_move_finalization_gate SET active_job_id=NULL,owner_token=NULL WHERE id=1 AND active_job_id=? AND owner_token=?",job.id(),job.owner());
        });
    }
}

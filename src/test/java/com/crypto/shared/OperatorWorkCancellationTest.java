package com.crypto.shared;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Query semantics on H2 only; actual V101/guarded script require MySQL validation. */
class OperatorWorkCancellationTest {
    JdbcTemplate jdbc;
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("""
            CREATE TABLE signal_processing_work(signal_id BIGINT PRIMARY KEY,status VARCHAR(30),
              symbol VARCHAR(30),interval_code VARCHAR(10),candle_open_time TIMESTAMP(6),
              origin VARCHAR(20),attempts INT,owner_token VARCHAR(36),next_attempt_at TIMESTAMP(6),
              updated_at TIMESTAMP(6),completed_at TIMESTAMP(6),paper_position_id BIGINT,
              failure_stage VARCHAR(40),error_message VARCHAR(20000),source_event_id BIGINT)
            """);
        jdbc.execute("CREATE TABLE signal_processing_work_cancellation AS SELECT * FROM signal_processing_work WHERE 1=0");
        jdbc.execute("ALTER TABLE signal_processing_work_cancellation ADD cancelled_at TIMESTAMP(6)");
        jdbc.execute("ALTER TABLE signal_processing_work_cancellation ADD operator_name VARCHAR(128)");
        jdbc.execute("ALTER TABLE signal_processing_work_cancellation ADD review_reference VARCHAR(128)");
        jdbc.execute("ALTER TABLE signal_processing_work_cancellation ADD decision VARCHAR(64)");
        jdbc.update("""
            INSERT INTO signal_processing_work(signal_id,status,symbol,interval_code,candle_open_time,
              origin,attempts,next_attempt_at,updated_at,failure_stage,error_message)
            VALUES(8065,'REVIEW_REQUIRED','SOLUSDT','1m',CURRENT_TIMESTAMP(6),
              'WORKER',1,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),'PROCESSING_OR_COMMIT',?)
            ""","Data too long for column entry_reason; UNKNOWN past UPDATE effects "+"x".repeat(4000));
    }
    boolean blocked() {return !jdbc.queryForList(OperatorWorkCancellation.UNATTRIBUTED_BLOCKERS).isEmpty();}
    void auditedCancellation() {
        jdbc.execute("""
            INSERT INTO signal_processing_work_cancellation
            SELECT w.*,CURRENT_TIMESTAMP(6),'Jean','FIX-145-TEST',
              'ABANDON_NO_REPLAY_EFFECTS_UNKNOWN' FROM signal_processing_work w
            """);
        jdbc.execute("""
            UPDATE signal_processing_work SET status='OPERATOR_CANCELLED',
             updated_at=(SELECT cancelled_at FROM signal_processing_work_cancellation WHERE signal_id=8065)
            WHERE signal_id=8065
            """);
    }
    @Test void exactAuditExemptsOnlyCancelledWorkWithoutClaimingCompletion() {
        assertTrue(blocked());auditedCancellation();assertFalse(blocked());
        assertNull(jdbc.queryForObject("SELECT completed_at FROM signal_processing_work",java.sql.Timestamp.class));
        assertEquals("REVIEW_REQUIRED",jdbc.queryForObject("SELECT status FROM signal_processing_work_cancellation",String.class));
        assertTrue(jdbc.queryForObject("SELECT error_message FROM signal_processing_work_cancellation",String.class).length()>4000);
    }
    @Test void bareCancellationStillBlocks() {
        jdbc.update("UPDATE signal_processing_work SET status='OPERATOR_CANCELLED'");assertTrue(blocked());
    }
    @Test void changedAttemptOrErrorStillBlocks() {
        auditedCancellation();jdbc.update("UPDATE signal_processing_work SET attempts=2");assertTrue(blocked());
        jdbc.update("UPDATE signal_processing_work SET attempts=1,error_message='changed'");assertTrue(blocked());
    }
    @Test void missingAuditOrBlankOperatorStillBlocks() {
        auditedCancellation();jdbc.update("UPDATE signal_processing_work_cancellation SET operator_name=' '");assertTrue(blocked());
        jdbc.update("DELETE FROM signal_processing_work_cancellation");assertTrue(blocked());
    }
    @Test void uncertaintyCannotBeRelabeledAsCompletionInAudit() {
        auditedCancellation();jdbc.update("UPDATE signal_processing_work_cancellation SET decision='COMPLETED'");assertTrue(blocked());
    }
    @Test void validCancellationDoesNotHideAnotherUnresolvedWorkRow() {
        auditedCancellation();
        jdbc.update("INSERT INTO signal_processing_work(signal_id,status) VALUES(8110,'REVIEW_REQUIRED')");
        assertEquals(8110L,((Number)jdbc.queryForList(OperatorWorkCancellation.UNATTRIBUTED_BLOCKERS).getFirst().get("signal_id")).longValue());
    }
    @Test void approvalUsesSameAuditAndNeverExemptsAttributedCancellation() {
        assertFalse(jdbc.queryForList(OperatorWorkCancellation.APPROVAL_BLOCKERS,"SOLUSDT").isEmpty());
        auditedCancellation();
        assertTrue(jdbc.queryForList(OperatorWorkCancellation.APPROVAL_BLOCKERS,"SOLUSDT").isEmpty());
        jdbc.update("UPDATE signal_processing_work SET source_event_id=123");
        assertFalse(jdbc.queryForList(OperatorWorkCancellation.APPROVAL_BLOCKERS,"SOLUSDT").isEmpty());
    }
    @Test void approvalRejectsBareCancellationAndChangedAudit() {
        jdbc.update("UPDATE signal_processing_work SET status='OPERATOR_CANCELLED'");
        assertFalse(jdbc.queryForList(OperatorWorkCancellation.APPROVAL_BLOCKERS,"SOLUSDT").isEmpty());
        jdbc.update("UPDATE signal_processing_work SET status='REVIEW_REQUIRED'");
        auditedCancellation();jdbc.update("UPDATE signal_processing_work_cancellation SET review_reference=' '");
        assertFalse(jdbc.queryForList(OperatorWorkCancellation.APPROVAL_BLOCKERS,"SOLUSDT").isEmpty());
    }
}

-- FIX-073: durable replay heartbeat distinguishes a live async worker from an orphaned
-- PENDING/RUNNING row after a JVM restart. It is isolated test metadata only.
ALTER TABLE analysis_test_run
    ADD COLUMN heartbeat_at TIMESTAMP(6) NULL AFTER current_step;

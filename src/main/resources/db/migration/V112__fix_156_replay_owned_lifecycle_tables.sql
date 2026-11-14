-- FIX-156: Replay-only lifecycle storage.  These structures intentionally
-- mirror the historical Replay test-table columns so the local Replay engine
-- can be moved without a Production/wallet schema mutation.  The copied
-- tables are owned exclusively by Replay from this migration onward.
-- Rollback is feature disable; do not delete recorded Replay evidence.
CREATE TABLE replay_position LIKE wallet_position_test;
CREATE TABLE replay_execution LIKE wallet_execution_test;
CREATE TABLE replay_position_management LIKE position_management_test;

ALTER TABLE replay_position
    ADD INDEX idx_replay_position_run_time (test_run_id, entry_time);
ALTER TABLE replay_execution
    ADD INDEX idx_replay_execution_run_time (test_run_id, execution_time);
ALTER TABLE replay_position_management
    ADD INDEX idx_replay_position_management_run_time (test_run_id, generated_at);

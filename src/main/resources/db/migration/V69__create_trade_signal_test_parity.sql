-- FIX-069: Replay keeps a full production-shaped TradeSignal snapshot for each generated signal.
-- The table intentionally clones production trade_signal first, then adds only replay metadata.
CREATE TABLE trade_signal_test LIKE trade_signal;
-- Production enforces one signal per symbol/interval/candle. Replay must allow the same
-- candle to be regenerated in multiple independent test runs, so run-scoping replaces
-- the production-only uniqueness boundary.
ALTER TABLE trade_signal_test DROP INDEX uk_trade_signal_symbol_interval_candle;
ALTER TABLE trade_signal_test
    ADD COLUMN test_run_id BIGINT NOT NULL AFTER id,
    ADD COLUMN source_signal_id BIGINT NULL AFTER test_run_id,
    ADD COLUMN replay_generated TINYINT(1) NOT NULL DEFAULT 1 AFTER source_signal_id,
    ADD COLUMN generation_error VARCHAR(1000) NULL AFTER replay_generated,
    ADD CONSTRAINT fk_trade_signal_test_run FOREIGN KEY (test_run_id) REFERENCES analysis_test_run(id) ON DELETE CASCADE,
    ADD INDEX idx_trade_signal_test_run_time (test_run_id, generated_at),
    ADD INDEX idx_trade_signal_test_run_interval (test_run_id, interval_code);

-- Safety archive remains backend-only even though the archive UI is removed.
-- Composite key permits test-table IDs to restart from 1 after Clear Data.
CREATE TABLE trade_signal_test_archive LIKE trade_signal_test;
ALTER TABLE trade_signal_test_archive
    DROP PRIMARY KEY,
    MODIFY COLUMN id BIGINT NOT NULL,
    ADD COLUMN archive_batch_id BIGINT NOT NULL FIRST,
    ADD PRIMARY KEY (archive_batch_id, id),
    ADD INDEX idx_trade_signal_test_archive_run_time (archive_batch_id, test_run_id, generated_at);

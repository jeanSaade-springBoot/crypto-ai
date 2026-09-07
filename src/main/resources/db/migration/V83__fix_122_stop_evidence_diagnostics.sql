-- FIX-122: append-only evaluation diagnostics include blocked entries and preserve
-- run identity when existing Replay tables are archived. No wallet foreign-key locks.
CREATE TABLE fix122_evaluation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    test_run_id BIGINT NULL,
    symbol VARCHAR(30) NOT NULL,
    signal_id BIGINT NULL,
    evaluated_at TIMESTAMP(6) NOT NULL,
    stage VARCHAR(40) NOT NULL,
    payload JSON NOT NULL,
    INDEX idx_fix122_run (test_run_id,id),
    INDEX idx_fix122_production (symbol,evaluated_at)
);
CREATE TABLE fix122_replay_revision (
    test_run_id BIGINT NOT NULL PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
-- Bounded latest terminal-stop lookup. This does not change signal history queries.
CREATE INDEX idx_wallet_trade_fix122_stop
    ON wallet_trade(symbol,status,side,execution_reason,executed_at,id);

-- Saved Proven evidence survives Replay purge and run-ID reuse.
ALTER TABLE proven_analyzed_trade ADD COLUMN fix122_diagnostics_json JSON NULL;

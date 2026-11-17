-- Replay-only late-entry research ledger. It is intentionally unrelated to
-- paper positions, wallet trades, and Production execution tables.
CREATE TABLE replay_candidate_recovery_ledger (
    id BIGINT NOT NULL AUTO_INCREMENT,
    test_run_id BIGINT NOT NULL,
    sequence_no INT NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    event_time TIMESTAMP(6) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    candidate_signal_id BIGINT NOT NULL,
    release_signal_id BIGINT NULL,
    blocker VARCHAR(80) NOT NULL,
    candidate_price DECIMAL(30,12) NOT NULL,
    decision_price DECIMAL(30,12) NULL,
    virtual_entry_price DECIMAL(30,12) NULL,
    virtual_exit_price DECIMAL(30,12) NULL,
    virtual_notional_usdt DECIMAL(30,12) NULL,
    fees_usdt DECIMAL(30,12) NULL,
    slippage_percent DECIMAL(20,8) NOT NULL,
    realized_pnl_usdt DECIMAL(30,12) NULL,
    realized_pnl_percent DECIMAL(20,8) NULL,
    unrealized_pnl_usdt DECIMAL(30,12) NULL,
    unrealized_pnl_percent DECIMAL(20,8) NULL,
    cumulative_pnl_usdt DECIMAL(30,12) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_replay_candidate_ledger_sequence (test_run_id, sequence_no),
    KEY idx_replay_candidate_ledger_run_time (test_run_id, event_time),
    CONSTRAINT fk_replay_candidate_ledger_run FOREIGN KEY (test_run_id)
        REFERENCES analysis_test_run(id) ON DELETE CASCADE
);

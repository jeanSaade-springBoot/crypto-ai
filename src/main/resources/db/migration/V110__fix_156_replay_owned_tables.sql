-- FIX-156: additive Replay-owned evidence only; no existing table is altered.
CREATE TABLE replay_policy_profile (
    run_id BIGINT NOT NULL PRIMARY KEY,
    profile_hash VARCHAR(128) NOT NULL,
    effective_at TIMESTAMP(6) NOT NULL,
    dynamic_profit_lock_enabled BOOLEAN NOT NULL,
    profit_lock_activation_percent DECIMAL(12,6) NOT NULL,
    profit_lock_initial_percent DECIMAL(12,6) NOT NULL,
    profit_lock_trail_step_percent DECIMAL(12,6) NOT NULL,
    fixed_notional_usdt DECIMAL(30,12) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_replay_policy_profile_hash (profile_hash)
);

-- FIX-156: immutable fixture manifest; payloads stay outside Production tables.
CREATE TABLE replay_fixture (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    fixture_type VARCHAR(64) NOT NULL,
    fixture_hash VARCHAR(128) NOT NULL,
    payload_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_replay_fixture_run_type_hash (run_id, fixture_type, fixture_hash),
    INDEX idx_replay_fixture_run_type (run_id, fixture_type)
);

-- FIX-156: Replay-only parity/outcome publication; never a Production trading record.
CREATE TABLE replay_run_output (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    result_type VARCHAR(64) NOT NULL,
    result_status VARCHAR(64) NOT NULL,
    details_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_replay_run_output_run_type (run_id, result_type)
);

CREATE TABLE replay_decision_trace (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    decision_key VARCHAR(160) NOT NULL,
    decision_at TIMESTAMP(6) NOT NULL,
    decision_path VARCHAR(64) NOT NULL,
    expected_json JSON NULL,
    actual_json JSON NULL,
    result_status VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_replay_decision_trace (run_id, decision_key),
    INDEX idx_replay_decision_trace_run_at (run_id, decision_at)
);

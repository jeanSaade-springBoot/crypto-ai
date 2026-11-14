-- FIX-156: forward-only Replay-owned details. V110 is already applied and is never edited.
-- Owner: Replay. Retention: investigation policy. Rollback: disable Replay feature;
-- preserve immutable evidence rather than mutating or deleting Production data.
CREATE TABLE replay_policy_profile_detail (
    run_id BIGINT NOT NULL PRIMARY KEY,
    policy_fingerprint VARCHAR(128) NOT NULL,
    execution_profile VARCHAR(64) NOT NULL,
    require_new_buy_transition BOOLEAN NOT NULL,
    minimum_usdt_reserve DECIMAL(30,12) NOT NULL,
    base_trade_amount_usdt DECIMAL(30,12) NOT NULL,
    maximum_daily_new_positions INT NOT NULL,
    exchange_constraint_fixture_hash VARCHAR(128) NOT NULL,
    minimum_executable_notional_usdt DECIMAL(30,12) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_replay_policy_detail_fingerprint (policy_fingerprint)
);

-- FIX-156: immutable run certificate. Owner: Replay; indexed by the two earned dimensions.
CREATE TABLE replay_run_certificate (
    run_id BIGINT NOT NULL PRIMARY KEY,
    analysis_parity_status VARCHAR(64) NOT NULL,
    lifecycle_parity_status VARCHAR(64) NOT NULL,
    profile_hash VARCHAR(128) NOT NULL,
    baseline_hash VARCHAR(128) NOT NULL,
    certificate_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_replay_run_certificate_status (analysis_parity_status, lifecycle_parity_status)
);

-- FIX-156: immutable qualified-price coverage evidence. Owner: Replay; one row per run/symbol.
CREATE TABLE replay_price_coverage (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    covered_from TIMESTAMP(6) NOT NULL,
    covered_to TIMESTAMP(6) NOT NULL,
    accepted_event_count BIGINT NOT NULL,
    gap_count BIGINT NOT NULL,
    coverage_status VARCHAR(64) NOT NULL,
    details_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_replay_price_coverage_run_symbol (run_id, symbol),
    INDEX idx_replay_price_coverage_run_status (run_id, coverage_status)
);

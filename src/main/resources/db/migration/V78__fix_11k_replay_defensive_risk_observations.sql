-- FIX-11K Phase A: replay-only observation persistence.
-- This table has no foreign key or trigger into Production wallet/signal tables and cannot execute trades.
CREATE TABLE defensive_risk_reduction_observation_test (
    id BIGINT NOT NULL AUTO_INCREMENT,
    test_run_id BIGINT NOT NULL,
    position_test_id BIGINT NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    observed_at TIMESTAMP(6) NOT NULL,
    source_signal_id BIGINT NULL,
    current_price DECIMAL(30,12) NOT NULL,
    entry_price DECIMAL(30,12) NOT NULL,
    highest_price_since_entry DECIMAL(30,12) NOT NULL,
    current_profit_percent DECIMAL(18,8) NOT NULL,
    peak_profit_percent DECIMAL(18,8) NOT NULL,
    giveback_from_peak_percent DECIMAL(18,8) NOT NULL,
    consecutive_final_1m_strong_sell INT NOT NULL,
    five_minute_signal_id BIGINT NULL,
    five_minute_original_decision VARCHAR(30) NULL,
    five_minute_final_decision VARCHAR(30) NULL,
    five_minute_confluence_status VARCHAR(30) NULL,
    one_hour_signal_id BIGINT NULL,
    one_hour_final_decision VARCHAR(30) NULL,
    observation_code VARCHAR(60) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_fix11k_observation_run_time (test_run_id, observed_at),
    KEY idx_fix11k_observation_symbol_time (symbol, observed_at)
);

CREATE TABLE defensive_risk_reduction_observation_test_archive LIKE defensive_risk_reduction_observation_test;
ALTER TABLE defensive_risk_reduction_observation_test_archive
    DROP PRIMARY KEY,
    MODIFY id BIGINT NOT NULL,
    ADD COLUMN archive_batch_id BIGINT NOT NULL FIRST,
    ADD PRIMARY KEY (archive_batch_id, id),
    ADD KEY idx_fix11k_archive_run_time (test_run_id, observed_at);

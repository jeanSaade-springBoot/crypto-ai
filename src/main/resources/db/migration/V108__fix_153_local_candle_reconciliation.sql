-- FIX-153: durable automatic local-candle reconciliation. These rows are
-- evidence and retry state only; they are never a trading approval/fence.
CREATE TABLE local_reconciliation_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    subject_type VARCHAR(32) NOT NULL,
    subject_key VARCHAR(160) NOT NULL,
    symbol VARCHAR(30) NULL,
    interval_code VARCHAR(10) NULL,
    candle_open_time TIMESTAMP(6) NULL,
    signal_id BIGINT NULL,
    status VARCHAR(48) NOT NULL,
    failure_reason VARCHAR(128) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NULL,
    exchange_time_snapshot TIMESTAMP(6) NULL,
    binance_open_time TIMESTAMP(6) NULL,
    binance_close_time TIMESTAMP(6) NULL,
    last_observation VARCHAR(64) NULL,
    late_live_close_at TIMESTAMP(6) NULL,
    resolved_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_fix153_reconciliation_subject (subject_type, subject_key),
    KEY idx_fix153_reconciliation_due (subject_type, status, next_attempt_at),
    KEY idx_fix153_reconciliation_candle (symbol, interval_code, candle_open_time),
    KEY idx_fix153_reconciliation_retention (status, resolved_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A terminal analysis outcome is separate from a TradeSignal. It prevents a
-- stale/unavailable/recovered candle from being silently treated as pending.
CREATE TABLE candle_analysis_outcome (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(30) NOT NULL,
    interval_code VARCHAR(10) NOT NULL,
    candle_open_time TIMESTAMP(6) NOT NULL,
    outcome VARCHAR(48) NOT NULL,
    reason_code VARCHAR(96) NOT NULL,
    signal_id BIGINT NULL,
    origin VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_fix153_candle_analysis_outcome (symbol, interval_code, candle_open_time),
    KEY idx_fix153_outcome_signal (signal_id),
    KEY idx_fix153_outcome_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

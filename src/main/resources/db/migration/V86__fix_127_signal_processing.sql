-- FIX-127: no historical backfill. Signal existence is not processing completion.
-- No foreign keys: ledger claims must not acquire signal/position parent locks.
CREATE TABLE signal_processing_work (
    signal_id BIGINT NOT NULL PRIMARY KEY,
    symbol VARCHAR(30) NOT NULL,
    interval_code VARCHAR(10) NOT NULL,
    candle_open_time TIMESTAMP(6) NOT NULL,
    origin VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    owner_token VARCHAR(36) NULL,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    paper_position_id BIGINT NULL,
    failure_stage VARCHAR(40) NULL,
    error_message MEDIUMTEXT NULL,
    KEY idx_fix127_due (status,next_attempt_at,signal_id),
    KEY idx_fix127_window (symbol,candle_open_time,signal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

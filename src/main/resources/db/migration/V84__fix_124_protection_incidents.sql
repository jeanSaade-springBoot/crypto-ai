-- FIX-124: Production incident history, independent of failed wallet transactions.
-- Exact candle lineage is retained; these rows are NOT Replay decision inputs.
CREATE TABLE fix124_protection_incident (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(30) NOT NULL,
    interval_code VARCHAR(10) NOT NULL,
    candle_open_time TIMESTAMP(6) NOT NULL,
    observed_at TIMESTAMP(6) NOT NULL,
    price DECIMAL(30,12) NOT NULL,
    attempts INT NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    error_message MEDIUMTEXT NULL,
    recorded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_fix124_symbol_observed (symbol, observed_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

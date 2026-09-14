-- FIX-129: reporting-only measurements; no FK or lock on trading tables.
CREATE TABLE fix129_stage_timing (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 correlation_id VARCHAR(36) NOT NULL,
 symbol VARCHAR(30) NOT NULL,
 interval_code VARCHAR(10) NULL,
 candle_open_time TIMESTAMP(6) NULL,
 observed_at TIMESTAMP(6) NULL,
 block_start TIMESTAMP(6) NULL,
 stage VARCHAR(40) NOT NULL,
 started_at TIMESTAMP(6) NOT NULL,
 finished_at TIMESTAMP(6) NOT NULL,
 elapsed_ms BIGINT NOT NULL,
 outcome VARCHAR(20) NOT NULL,
 thread_name VARCHAR(100) NOT NULL,
 INDEX idx_fix129_symbol_started (symbol, started_at),
 INDEX idx_fix129_correlation (correlation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

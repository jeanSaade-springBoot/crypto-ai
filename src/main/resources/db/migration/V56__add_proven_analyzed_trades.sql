CREATE TABLE proven_analyzed_trade (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_test_run_id BIGINT NULL,
    source_trade_id BIGINT NULL,
    symbol VARCHAR(30) NOT NULL,
    entry_time TIMESTAMP(6) NOT NULL,
    entry_price DECIMAL(30,12) NOT NULL,
    exit_time TIMESTAMP(6) NULL,
    exit_price DECIMAL(30,12) NULL,
    exit_reason VARCHAR(100) NULL,
    realized_pnl_usdt DECIMAL(20,8) NULL,
    realized_pnl_percent DECIMAL(20,8) NULL,
    position_percent INT NOT NULL DEFAULT 0,
    marked_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_proven_analyzed_trade_source (source_test_run_id, source_trade_id),
    INDEX idx_proven_analyzed_trade_symbol_time (symbol, entry_time)
);

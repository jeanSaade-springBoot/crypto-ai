CREATE TABLE proven_trade_leg_archive (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    proven_trade_id BIGINT NOT NULL,
    source_test_run_id BIGINT NULL,
    source_trade_id BIGINT NULL,
    symbol VARCHAR(30) NOT NULL,
    side VARCHAR(10) NOT NULL,
    execution_time TIMESTAMP(6) NOT NULL,
    execution_price DECIMAL(30,12) NOT NULL,
    exit_reason VARCHAR(100) NULL,
    realized_pnl_usdt DECIMAL(20,8) NULL,
    realized_pnl_percent DECIMAL(20,8) NULL,
    archived_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_proven_trade_leg_archive (proven_trade_id, side),
    INDEX idx_proven_trade_leg_archive_symbol_time (symbol, execution_time),
    INDEX idx_proven_trade_leg_archive_source (source_test_run_id, source_trade_id)
);

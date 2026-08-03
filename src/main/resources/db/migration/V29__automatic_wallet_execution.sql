CREATE TABLE wallet_managed_position (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(30) NOT NULL,
    quantity DECIMAL(30,12) NOT NULL DEFAULT 0,
    average_entry_price_usdt DECIMAL(30,12) NOT NULL DEFAULT 0,
    total_cost_usdt DECIMAL(30,12) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    opened_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_wallet_managed_position_symbol_status (symbol, status)
);

ALTER TABLE wallet_trade
    ADD COLUMN execution_key VARCHAR(100) NULL AFTER signal_id,
    ADD UNIQUE KEY uk_wallet_trade_execution_key (execution_key);

UPDATE wallet_settings
SET automatic_execution_enabled = 0
WHERE id = 1;

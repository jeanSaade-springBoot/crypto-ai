CREATE TABLE wallet_asset (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(20) NOT NULL,
    quantity DECIMAL(30,12) NOT NULL DEFAULT 0,
    average_buy_price_usdt DECIMAL(30,12) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_asset_symbol (symbol)
);

CREATE TABLE wallet_settings (
    id BIGINT NOT NULL,
    base_trade_amount_usdt DECIMAL(30,12) NOT NULL DEFAULT 100,
    minimum_usdt_reserve DECIMAL(30,12) NOT NULL DEFAULT 0,
    automatic_execution_enabled TINYINT(1) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
);

CREATE TABLE wallet_cash_flow (
    id BIGINT NOT NULL AUTO_INCREMENT,
    flow_type VARCHAR(20) NOT NULL,
    amount_usdt DECIMAL(30,12) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    notes VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_wallet_cash_flow_time (occurred_at)
);

CREATE TABLE wallet_trade (
    id BIGINT NOT NULL AUTO_INCREMENT,
    signal_id BIGINT NULL,
    symbol VARCHAR(30) NOT NULL,
    side VARCHAR(10) NOT NULL,
    quantity DECIMAL(30,12) NOT NULL,
    price_usdt DECIMAL(30,12) NOT NULL,
    gross_amount_usdt DECIMAL(30,12) NOT NULL,
    fee_usdt DECIMAL(30,12) NOT NULL DEFAULT 0,
    net_amount_usdt DECIMAL(30,12) NOT NULL,
    cost_basis_usdt DECIMAL(30,12) NULL,
    realized_pnl_usdt DECIMAL(30,12) NULL,
    realized_pnl_percent DECIMAL(20,8) NULL,
    execution_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    status VARCHAR(20) NOT NULL DEFAULT 'EXECUTED',
    executed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    notes VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_wallet_trade_signal FOREIGN KEY (signal_id) REFERENCES trade_signal(id),
    KEY idx_wallet_trade_symbol_time (symbol, executed_at),
    KEY idx_wallet_trade_signal (signal_id)
);

CREATE TABLE wallet_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    portfolio_value_usdt DECIMAL(30,12) NOT NULL,
    net_invested_usdt DECIMAL(30,12) NOT NULL,
    total_pnl_usdt DECIMAL(30,12) NOT NULL,
    total_return_percent DECIMAL(20,8) NOT NULL,
    realized_pnl_usdt DECIMAL(30,12) NOT NULL,
    unrealized_pnl_usdt DECIMAL(30,12) NOT NULL,
    available_usdt DECIMAL(30,12) NOT NULL,
    captured_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_wallet_snapshot_time (captured_at)
);

INSERT INTO wallet_asset(symbol, quantity, average_buy_price_usdt, enabled)
VALUES ('USDT', 0, 1, 1)
ON DUPLICATE KEY UPDATE symbol = VALUES(symbol);

INSERT INTO wallet_settings(id, base_trade_amount_usdt, minimum_usdt_reserve, automatic_execution_enabled)
VALUES (1, 100, 0, 0)
ON DUPLICATE KEY UPDATE id = VALUES(id);

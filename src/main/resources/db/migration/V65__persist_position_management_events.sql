-- FIX-053: Persist management-path events needed by the active-position dashboard.
-- Production timestamps remain UTC. The browser is responsible for KSA/local display.
CREATE TABLE IF NOT EXISTS position_management_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    wallet_position_id BIGINT NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    old_value_usdt DECIMAL(30,12) NULL,
    new_value_usdt DECIMAL(30,12) NULL,
    market_price_usdt DECIMAL(30,12) NULL,
    reason VARCHAR(2000) NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_position_management_event_position_time (wallet_position_id, occurred_at),
    KEY idx_position_management_event_symbol_time (symbol, occurred_at),
    CONSTRAINT fk_position_management_event_position
        FOREIGN KEY (wallet_position_id) REFERENCES wallet_managed_position(id) ON DELETE CASCADE
);

CREATE TABLE position_analysis (
    id BIGINT NOT NULL AUTO_INCREMENT,
    wallet_position_id BIGINT NOT NULL,
    trade_signal_id BIGINT NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    interval_code VARCHAR(10) NOT NULL,
    entry_price_usdt DECIMAL(30,12) NOT NULL,
    current_price_usdt DECIMAL(30,12) NOT NULL,
    unrealized_pnl_usdt DECIMAL(30,12) NOT NULL,
    unrealized_pnl_percent DECIMAL(20,8) NOT NULL,
    holding_minutes BIGINT NOT NULL,
    trend_deterioration_score INT NOT NULL,
    momentum_exhaustion_score INT NOT NULL,
    profit_protection_score INT NOT NULL,
    risk_event_score INT NOT NULL,
    opportunity_cost_score INT NOT NULL,
    exit_score INT NOT NULL,
    recommendation VARCHAR(20) NOT NULL,
    confidence INT NOT NULL,
    explanation VARCHAR(2000) NOT NULL,
    details_json JSON NULL,
    advisory_only BOOLEAN NOT NULL DEFAULT TRUE,
    analyzed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_position_analysis_wallet_position
        FOREIGN KEY (wallet_position_id) REFERENCES wallet_managed_position(id),
    CONSTRAINT fk_position_analysis_trade_signal
        FOREIGN KEY (trade_signal_id) REFERENCES trade_signal(id),
    CONSTRAINT uk_position_analysis_position_signal
        UNIQUE (wallet_position_id, trade_signal_id),
    INDEX idx_position_analysis_position_time (wallet_position_id, analyzed_at),
    INDEX idx_position_analysis_symbol_time (symbol, analyzed_at)
);

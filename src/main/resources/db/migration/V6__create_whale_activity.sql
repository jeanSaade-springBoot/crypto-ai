CREATE TABLE whale_activity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    blockchain VARCHAR(30) NOT NULL,
    transaction_hash VARCHAR(180) NOT NULL,
    wallet_address VARCHAR(220) NOT NULL,
    counterparty_address VARCHAR(220) NULL,
    wallet_label VARCHAR(180) NULL,
    counterparty_label VARCHAR(180) NULL,
    symbol VARCHAR(30) NOT NULL,
    asset VARCHAR(20) NOT NULL,
    movement_type VARCHAR(40) NOT NULL,
    amount DECIMAL(38,12) NULL,
    usd_value DECIMAL(24,2) NOT NULL,
    transaction_score DECIMAL(10,8) NOT NULL,
    transaction_confidence DECIMAL(10,8) NOT NULL,
    price_at_signal DECIMAL(30,12) NULL,
    evaluation_horizon VARCHAR(30) NOT NULL,
    evaluation_due_at TIMESTAMP(6) NOT NULL,
    price_at_evaluation DECIMAL(30,12) NULL,
    market_return DECIMAL(14,10) NULL,
    evaluation_result VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    prediction_quality DECIMAL(10,8) NULL,
    evaluated_at TIMESTAMP(6) NULL,
    whale_total_signals BIGINT NOT NULL DEFAULT 0,
    whale_correct_signals BIGINT NOT NULL DEFAULT 0,
    whale_incorrect_signals BIGINT NOT NULL DEFAULT 0,
    whale_inconclusive_signals BIGINT NOT NULL DEFAULT 0,
    whale_accuracy DECIMAL(10,8) NOT NULL DEFAULT 0,
    whale_average_quality DECIMAL(10,8) NOT NULL DEFAULT 0,
    whale_learned_weight DECIMAL(10,8) NOT NULL DEFAULT 0.15,
    observed_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_whale_activity_tx_wallet_horizon UNIQUE
        (blockchain, transaction_hash, wallet_address, evaluation_horizon),
    INDEX idx_whale_activity_due (evaluation_result, evaluation_due_at),
    INDEX idx_whale_activity_symbol_time (symbol, observed_at),
    INDEX idx_whale_activity_wallet_performance
        (wallet_address, symbol, evaluation_horizon, evaluated_at)
);

INSERT INTO sentiment_provider (
    provider_code,
    display_name,
    enabled,
    weight,
    collection_interval_seconds,
    last_status,
    api_key_env_var
)
VALUES (
    'WHALE_ALERT',
    'Whale Alert',
    FALSE,
    0.20,
    300,
    'NEVER_RUN',
    'WHALE_ALERT_API_KEY'
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    weight = 0.20,
    collection_interval_seconds = VALUES(collection_interval_seconds),
    api_key_env_var = VALUES(api_key_env_var);

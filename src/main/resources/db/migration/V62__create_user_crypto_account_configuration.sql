-- FIX-031: keep exchange/account configuration isolated per authenticated app_user.
-- Trading signals/strategy remain shared; this table only stores the user's execution-account boundary.
CREATE TABLE crypto_account_configuration (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    exchange_code VARCHAR(20) NOT NULL DEFAULT 'BINANCE',
    account_label VARCHAR(100) NOT NULL DEFAULT 'Primary account',
    execution_mode VARCHAR(20) NOT NULL DEFAULT 'PAPER',
    api_key_encrypted TEXT NULL,
    api_secret_encrypted TEXT NULL,
    api_key_hint VARCHAR(32) NULL,
    max_order_usdt DECIMAL(20,8) NOT NULL DEFAULT 10.00000000,
    max_total_exposure_usdt DECIMAL(20,8) NOT NULL DEFAULT 50.00000000,
    max_open_positions INT NOT NULL DEFAULT 3,
    max_daily_loss_usdt DECIMAL(20,8) NOT NULL DEFAULT 10.00000000,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_crypto_account_user_exchange UNIQUE (user_id, exchange_code),
    CONSTRAINT fk_crypto_account_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

ALTER TABLE crypto_ai.wallet_settings
    ADD COLUMN dynamic_profit_lock_enabled TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN profit_lock_activation_percent DECIMAL(8,4) NOT NULL DEFAULT 70.0000,
    ADD COLUMN profit_lock_initial_percent DECIMAL(8,4) NOT NULL DEFAULT 40.0000,
    ADD COLUMN profit_lock_trail_step_percent DECIMAL(8,4) NOT NULL DEFAULT 10.0000;

ALTER TABLE crypto_ai.wallet_managed_position
    ADD COLUMN highest_price_usdt DECIMAL(30,12) NULL,
    ADD COLUMN profit_lock_active TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN profit_lock_price_usdt DECIMAL(30,12) NULL,
    ADD COLUMN profit_lock_progress_percent DECIMAL(12,6) NULL,
    ADD COLUMN profit_lock_activated_at TIMESTAMP(6) NULL;

ALTER TABLE crypto_ai.wallet_settings
    ADD COLUMN performance_window_type VARCHAR(20) NOT NULL DEFAULT 'LAST_TRADES' AFTER maximum_daily_new_positions,
    ADD COLUMN performance_trade_count INT NOT NULL DEFAULT 20 AFTER performance_window_type,
    ADD COLUMN performance_period_days INT NOT NULL DEFAULT 1 AFTER performance_trade_count,
    ADD COLUMN performance_start_date DATE NULL AFTER performance_period_days,
    ADD COLUMN performance_end_date DATE NULL AFTER performance_start_date;

UPDATE crypto_ai.wallet_settings
SET performance_window_type = 'LAST_TRADES',
    performance_trade_count = 20,
    performance_period_days = 1
WHERE id = 1;

-- FIX-032: user-scoped LIVE_MICRO safety configuration only.
-- These columns do not change signal generation, Paper Wallet execution or Replay behavior.
ALTER TABLE crypto_account_configuration
    ADD COLUMN safety_enabled TINYINT(1) NOT NULL DEFAULT 1 AFTER max_daily_loss_usdt,
    ADD COLUMN consecutive_loss_pause_count INT NOT NULL DEFAULT 3 AFTER safety_enabled,
    ADD COLUMN consecutive_loss_pause_minutes INT NOT NULL DEFAULT 120 AFTER consecutive_loss_pause_count,
    ADD COLUMN consecutive_loss_manual_stop_count INT NOT NULL DEFAULT 4 AFTER consecutive_loss_pause_minutes,
    ADD COLUMN rolling_loss_window_minutes INT NOT NULL DEFAULT 240 AFTER consecutive_loss_manual_stop_count,
    ADD COLUMN max_rolling_loss_usdt DECIMAL(20,8) NOT NULL DEFAULT 10.00000000 AFTER rolling_loss_window_minutes,
    ADD COLUMN same_symbol_loss_count INT NOT NULL DEFAULT 2 AFTER max_rolling_loss_usdt,
    ADD COLUMN same_symbol_quarantine_minutes INT NOT NULL DEFAULT 240 AFTER same_symbol_loss_count,
    ADD COLUMN max_slippage_percent DECIMAL(10,4) NOT NULL DEFAULT 0.3000 AFTER same_symbol_quarantine_minutes,
    ADD COLUMN binance_failure_pause_count INT NOT NULL DEFAULT 2 AFTER max_slippage_percent;

-- FIX-032 requested LIVE_MICRO default daily circuit breaker. Existing FIX-031 rows still
-- contain the original untouched 10 USDT placeholder, so promote only that original default.
UPDATE crypto_account_configuration
SET max_daily_loss_usdt = 20.00000000
WHERE max_daily_loss_usdt = 10.00000000;

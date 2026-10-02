-- FIX-141: persist BTC context freshness evidence alongside the existing btc_context_* columns.
-- Added only where the existing model cannot represent the evidence (see FIX-141 design, section 5).
ALTER TABLE trade_signal
    ADD COLUMN btc_context_candle_close_time TIMESTAMP(6) NULL AFTER btc_signal_generated_at,
    ADD COLUMN btc_context_age_seconds BIGINT NULL AFTER btc_context_candle_close_time,
    ADD COLUMN btc_context_freshness_threshold_seconds BIGINT NULL AFTER btc_context_age_seconds;

-- FIX-056: keep decision-time price separate from execution-time fill price.
-- All timestamps remain UTC in MySQL; frontend/local conversion is presentation-only.
ALTER TABLE wallet_trade
    ADD COLUMN decision_price_usdt DECIMAL(30,12) NULL AFTER price_usdt,
    ADD COLUMN execution_price_observed_at TIMESTAMP(6) NULL AFTER decision_price_usdt;

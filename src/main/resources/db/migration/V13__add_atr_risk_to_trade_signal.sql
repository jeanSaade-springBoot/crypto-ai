ALTER TABLE trade_signal
    ADD COLUMN atr_at_signal DECIMAL(30,12) NULL,
    ADD COLUMN atr_percent DECIMAL(20,8) NULL,
    ADD COLUMN risk_reward_ratio DECIMAL(20,8) NULL,
    ADD COLUMN candle_range_atr_multiple DECIMAL(20,8) NULL,
    ADD COLUMN volatility_level VARCHAR(20) NULL,
    ADD COLUMN atr_overextended BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN atr_explanation VARCHAR(1000) NULL;

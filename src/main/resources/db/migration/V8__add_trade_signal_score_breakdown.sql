ALTER TABLE trade_signal
    ADD COLUMN ema_cross_score INT NOT NULL DEFAULT 0,
    ADD COLUMN price_ema200_score INT NOT NULL DEFAULT 0,
    ADD COLUMN ema_alignment_score INT NOT NULL DEFAULT 0,
    ADD COLUMN sma20_score INT NOT NULL DEFAULT 0,
    ADD COLUMN rsi_score INT NOT NULL DEFAULT 0,
    ADD COLUMN macd_score INT NOT NULL DEFAULT 0,
    ADD COLUMN bollinger_score INT NOT NULL DEFAULT 0,
    ADD COLUMN relative_volume_score INT NOT NULL DEFAULT 0,
    ADD COLUMN volume_sma20_score INT NOT NULL DEFAULT 0,
    ADD COLUMN raw_score INT NOT NULL DEFAULT 0,
    ADD COLUMN maximum_available_score INT NOT NULL DEFAULT 0,
    ADD COLUMN sentiment_breakdown JSON NULL;

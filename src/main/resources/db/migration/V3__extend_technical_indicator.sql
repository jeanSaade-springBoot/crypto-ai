ALTER TABLE technical_indicator
    ADD COLUMN ema_200 DECIMAL(30, 12),
    ADD COLUMN macd DECIMAL(30, 12),
    ADD COLUMN macd_signal DECIMAL(30, 12),
    ADD COLUMN macd_histogram DECIMAL(30, 12),
    ADD COLUMN relative_volume DECIMAL(20, 8);
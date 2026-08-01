ALTER TABLE trade_signal
    ADD COLUMN trend_direction_score INT NOT NULL DEFAULT 0 AFTER sma20_score,
    ADD COLUMN trend_structure_score INT NOT NULL DEFAULT 0 AFTER trend_direction_score,
    ADD COLUMN trend_strength_score INT NOT NULL DEFAULT 0 AFTER trend_structure_score,
    ADD COLUMN trend_price_location_score INT NOT NULL DEFAULT 0 AFTER trend_strength_score;

ALTER TABLE trade_signal
    ADD COLUMN atr_entry_type VARCHAR(40) NULL AFTER atr_overextended,
    ADD COLUMN atr_recommended_position_percent INT NOT NULL DEFAULT 100 AFTER atr_entry_type,
    ADD COLUMN atr_immediate_entry_allowed BOOLEAN NOT NULL DEFAULT TRUE AFTER atr_recommended_position_percent,
    ADD COLUMN atr_retracement_entry_price DECIMAL(30, 12) NULL AFTER atr_immediate_entry_allowed;

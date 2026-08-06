ALTER TABLE wallet_managed_position
    ADD COLUMN entry_signal_id BIGINT NULL AFTER id,
    ADD COLUMN entry_confidence INT NULL AFTER total_cost_usdt,
    ADD COLUMN entry_total_score INT NULL AFTER entry_confidence,
    ADD COLUMN entry_trend_score INT NULL AFTER entry_total_score,
    ADD COLUMN entry_structure_score INT NULL AFTER entry_trend_score,
    ADD COLUMN entry_momentum_score INT NULL AFTER entry_structure_score,
    ADD COLUMN entry_volume_score INT NULL AFTER entry_momentum_score,
    ADD COLUMN entry_sentiment_score INT NULL AFTER entry_volume_score,
    ADD COLUMN entry_fundamental_score INT NULL AFTER entry_sentiment_score,
    ADD COLUMN entry_decision VARCHAR(30) NULL AFTER entry_fundamental_score,
    ADD COLUMN entry_decision_path_json JSON NULL AFTER entry_decision,
    ADD COLUMN entry_analysis_snapshot_json JSON NULL AFTER entry_decision_path_json,
    ADD COLUMN stop_loss_usdt DECIMAL(30,12) NULL AFTER entry_analysis_snapshot_json,
    ADD COLUMN take_profit_usdt DECIMAL(30,12) NULL AFTER stop_loss_usdt,
    ADD CONSTRAINT fk_wallet_managed_position_entry_signal
        FOREIGN KEY (entry_signal_id) REFERENCES trade_signal(id),
    ADD INDEX idx_wallet_managed_position_entry_signal (entry_signal_id);

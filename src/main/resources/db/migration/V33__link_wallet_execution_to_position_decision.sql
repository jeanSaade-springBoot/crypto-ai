ALTER TABLE wallet_trade
    ADD COLUMN position_analysis_id BIGINT NULL AFTER signal_id,
    ADD COLUMN execution_reason VARCHAR(40) NULL AFTER execution_type,
    ADD COLUMN execution_message VARCHAR(1000) NULL AFTER notes,
    ADD CONSTRAINT fk_wallet_trade_position_analysis
        FOREIGN KEY (position_analysis_id) REFERENCES position_analysis(id),
    ADD INDEX idx_wallet_trade_position_analysis (position_analysis_id),
    ADD INDEX idx_wallet_trade_execution_reason (execution_reason);

UPDATE wallet_trade
SET execution_reason = CASE
    WHEN side = 'BUY' THEN 'ENTRY_BUY'
    WHEN side = 'SELL' THEN 'SIGNAL_SELL'
    ELSE 'LEGACY_EXECUTION'
END
WHERE execution_reason IS NULL;

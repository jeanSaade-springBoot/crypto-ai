-- FIX-028: immutable production exit audit. This table is diagnostic only; it does not
-- participate in BUY/SELL decisions. It preserves the true terminal trigger separately
-- from the market-context signal that happened to be current when the exit executed.
CREATE TABLE production_exit_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    paper_position_id BIGINT NULL,
    wallet_position_id BIGINT NULL,
    symbol VARCHAR(30) NOT NULL,
    close_trigger VARCHAR(40) NOT NULL,
    source_signal_id BIGINT NULL,
    source_signal_decision VARCHAR(30) NULL,
    source_signal_original_decision VARCHAR(30) NULL,
    position_analysis_id BIGINT NULL,
    position_recommendation VARCHAR(20) NULL,
    entry_price_usdt DECIMAL(30,12) NULL,
    exit_price_usdt DECIMAL(30,12) NOT NULL,
    stop_loss_usdt DECIMAL(30,12) NULL,
    take_profit_usdt DECIMAL(30,12) NULL,
    close_explanation VARCHAR(2000) NULL,
    audited_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_production_exit_audit_paper_position (paper_position_id),
    KEY idx_production_exit_audit_symbol_time (symbol, audited_at),
    KEY idx_production_exit_audit_source_signal (source_signal_id),
    KEY idx_production_exit_audit_wallet_position (wallet_position_id)
);

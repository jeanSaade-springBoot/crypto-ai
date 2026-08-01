ALTER TABLE trade_signal
    ADD COLUMN market_context_snapshot JSON NULL AFTER strategy_breakdown;

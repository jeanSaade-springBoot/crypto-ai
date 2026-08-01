ALTER TABLE trade_signal
    ADD COLUMN analysis_breakdown JSON NULL AFTER sentiment_breakdown;

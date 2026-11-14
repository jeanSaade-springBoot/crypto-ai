-- FIX-153: nullable provenance preserves historical evidence without relabelling it.
ALTER TABLE candle ADD COLUMN ingestion_source VARCHAR(16) NULL,
 ADD COLUMN close_transition_token VARCHAR(36) NULL;
-- NULL permits legacy/duplicate receipts; the first valid runtime receipt keeps slot 1 forever.
ALTER TABLE market_price_event ADD COLUMN ordering_slot TINYINT NULL,
 ADD COLUMN outcome_reason VARCHAR(64) NULL,
 ADD UNIQUE KEY uk_fix153_price_receipt(symbol, observed_at, ordering_slot),
 ADD KEY idx_fix153_pending(source, delivery_status, created_at, id);
-- Historical generated_at is an as-of clock, not physical analysis latency.
ALTER TABLE trade_signal ADD COLUMN signal_mode VARCHAR(24) NULL;
-- Existing protection/UI lookups order by generated_at within one symbol/timeframe.
-- This local access path avoids fetching/sorting the entire per-lane signal history.
CREATE INDEX idx_fix153_signal_latest ON trade_signal(symbol, interval_code, generated_at DESC, id DESC);

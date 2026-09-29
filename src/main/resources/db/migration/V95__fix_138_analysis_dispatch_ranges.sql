-- FIX-138: fixed lane + status + close-time range for live/historical discovery.
-- Distinct names from the operator-added FIX-137 indexes; do not rebuild/drop them.
CREATE INDEX idx_fix138_analysis_close
 ON shared_market_event_delivery(symbol,interval_code,phase,analysis_status,candle_close_time,symbol_sequence);
-- The independent monitor visits only timed-out RUNNING rows, never all deliveries.
CREATE INDEX idx_fix138_analysis_timeout
 ON shared_market_event_delivery(analysis_status,analysis_started_at,source_event_id);

-- FIX-151: trader-local eligibility access, not the collector/source schema.
-- The flag has no time dependency and is maintained by MySQL on every phase or
-- status change. Existing evidence and all review/ownership state stay unchanged.
-- A virtual column avoids storing the flag in every clustered table row.
ALTER TABLE shared_market_event_delivery
 ADD COLUMN phase_walk_pending TINYINT GENERATED ALWAYS AS
   (CASE WHEN phase<4 AND status<>'REVIEW_REQUIRED' THEN 1 ELSE 0 END) VIRTUAL;

-- FIX-151: skip completed/review rows without losing global sequence ordering.
CREATE INDEX idx_fix151_pending_sequence
 ON shared_market_event_delivery(symbol,phase_walk_pending,symbol_sequence);

-- FIX-151: one covering close-window access path for both exposure phase groups.
-- Closed equality and the close-time range come before phase/status filters so
-- old pending history does not have to be examined to count current windows.
CREATE INDEX idx_fix151_exposure_close
 ON shared_market_event_delivery(symbol,interval_code,closed,candle_close_time,phase,analysis_status);

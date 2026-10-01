-- FIX-140: terminal retirement is distinct from successful analysis completion.
-- No UPDATE/DELETE of existing deliveries, candles, signals, or wallet work is performed by migration.
ALTER TABLE shared_market_event_delivery
 ADD COLUMN analysis_retired_at TIMESTAMP(6) NULL,
 ADD COLUMN analysis_retirement_operation VARCHAR(36) NULL,
 ADD COLUMN analysis_retirement_reason VARCHAR(255) NULL;

-- FIX-132: historical work must not reserve signal identity ahead of a fresh close.
ALTER TABLE shared_market_event_delivery ADD COLUMN analysis_not_before TIMESTAMP(6) NULL;

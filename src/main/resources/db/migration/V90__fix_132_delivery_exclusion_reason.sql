-- FIX-132: additive review refinement; retain V89 checksum for existing candidates.
ALTER TABLE shared_market_event_delivery ADD COLUMN eligibility_reason VARCHAR(80) NULL;

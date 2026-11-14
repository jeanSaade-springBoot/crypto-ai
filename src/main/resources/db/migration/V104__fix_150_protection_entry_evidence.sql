-- FIX-150: claim ownership is not evidence that protection business code ran.
-- Existing attempts remain explicitly unknown; never infer their effects in backfill.
ALTER TABLE shared_market_event_delivery
 ADD COLUMN protection_apply_state VARCHAR(20) NOT NULL DEFAULT 'LEGACY_UNKNOWN';
-- Bounded health history uses reviewer equality followed by newest audit IDs.
CREATE INDEX idx_fix150_recovery_audit ON shared_delivery_resolution_audit(reviewer,id);

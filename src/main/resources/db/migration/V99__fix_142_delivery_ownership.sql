-- FIX-142: durable phase-stream fencing; never infer legacy ownership from age.
ALTER TABLE shared_market_consumer_state
 ADD COLUMN active_delivery_event_id BIGINT NULL,
 ADD COLUMN active_delivery_owner_token VARCHAR(80) NULL,
 ADD COLUMN active_delivery_process_owner VARCHAR(36) NULL,
 ADD COLUMN active_delivery_state VARCHAR(30) NOT NULL DEFAULT 'NONE',
 ADD COLUMN active_delivery_started_at TIMESTAMP(6) NULL,
 ADD COLUMN active_delivery_updated_at TIMESTAMP(6) NULL;
ALTER TABLE shared_market_event_delivery ADD COLUMN protection_outcome VARCHAR(20) NULL;
UPDATE shared_market_event_delivery SET protection_outcome='LEGACY_UNKNOWN'
 WHERE protection_completed_at IS NOT NULL AND protection_outcome IS NULL;
CREATE TABLE shared_delivery_resolution_audit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 source_event_id BIGINT NOT NULL, symbol VARCHAR(30) NOT NULL,
 operation VARCHAR(40) NOT NULL, old_owner_token VARCHAR(80) NULL,
 new_owner_token VARCHAR(80) NULL, reviewer VARCHAR(160) NOT NULL,
 evidence VARCHAR(2000) NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;
CREATE INDEX idx_fix142_price_expiry ON shared_market_event_delivery
 (symbol,interval_code,protection_completed_at,protection_started_at,observed_at);
CREATE INDEX idx_fix142_price_sequence ON shared_market_event_delivery
 (symbol,interval_code,protection_completed_at,symbol_sequence);
CREATE INDEX idx_fix142_lane_phase_close ON shared_market_event_delivery
 (symbol,interval_code,phase,candle_close_time,symbol_sequence);

-- FIX-142: price-window selection must not traverse all earlier untouched rows.
CREATE INDEX idx_fix142_price_window ON shared_market_event_delivery
 (symbol,interval_code,observed_at,symbol_sequence);

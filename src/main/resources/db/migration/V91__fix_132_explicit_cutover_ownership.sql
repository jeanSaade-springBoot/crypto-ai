-- FIX-132: old READY records are ambiguous; never infer operator approval.
ALTER TABLE shared_market_consumer_state
 ADD COLUMN cutover_source VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN_LEGACY',
 ADD COLUMN approved_by VARCHAR(160) NULL,
 ADD COLUMN approved_at TIMESTAMP(6) NULL,
 ADD COLUMN approval_reference VARCHAR(255) NULL,
 ADD COLUMN approved_sequence BIGINT NULL,
 ADD COLUMN approved_cutover_at TIMESTAMP(6) NULL;
ALTER TABLE shared_market_consumer_state ALTER COLUMN status SET DEFAULT 'PENDING_CUTOVER';
UPDATE shared_market_consumer_state SET status='PENDING_CUTOVER' WHERE status='READY';
ALTER TABLE signal_processing_work ADD COLUMN source_event_id BIGINT NULL;
CREATE INDEX idx_fix132_work_event ON signal_processing_work(source_event_id);
CREATE TABLE shared_market_cutover_approval (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, symbol VARCHAR(30) NOT NULL,
 cutover_sequence BIGINT NOT NULL, cutover_at TIMESTAMP(6) NOT NULL,
 approved_by VARCHAR(160) NOT NULL, approved_at TIMESTAMP(6) NOT NULL,
 approval_reference VARCHAR(255) NOT NULL,
 UNIQUE KEY uk_fix132_approval(symbol,cutover_sequence,cutover_at,approval_reference)
) ENGINE=InnoDB;

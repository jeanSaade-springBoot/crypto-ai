ALTER TABLE wallet_managed_position
    ADD COLUMN entry_stage VARCHAR(30) NOT NULL DEFAULT 'NONE',
    ADD COLUMN allocated_position_percent INT NOT NULL DEFAULT 0,
    ADD COLUMN entry_quality_score INT NOT NULL DEFAULT 0,
    ADD COLUMN last_scale_in_at TIMESTAMP(6) NULL;

-- Existing pre-migration OPEN positions were created before staged allocation existed.
-- Treat them as fully allocated so deployment cannot accidentally scale into them.
UPDATE wallet_managed_position
SET entry_stage = 'LEGACY_FULL',
    allocated_position_percent = 100
WHERE status = 'OPEN'
  AND quantity > 0;

-- FIX-118 behavioral phase: preserve earned Profit Lock history across TP extension
-- without letting protection calculated against the superseded TP remain executable.
ALTER TABLE wallet_managed_position
    ADD COLUMN profit_lock_state VARCHAR(40) NOT NULL DEFAULT 'INACTIVE' AFTER profit_lock_activated_at,
    ADD COLUMN profit_lock_rebase_started_at TIMESTAMP(6) NULL AFTER profit_lock_state;

-- Mandatory legacy backfill: every position that already has an earned lock remains ACTIVE.
UPDATE wallet_managed_position
SET profit_lock_state = 'ACTIVE'
WHERE profit_lock_active = 1;

-- Replay carries the same persisted lifecycle state. Archive remains schema-identical because
-- regression archival uses INSERT ... SELECT * semantics.
ALTER TABLE wallet_position_test
    ADD COLUMN profit_lock_state VARCHAR(40) NOT NULL DEFAULT 'INACTIVE' AFTER profit_lock_price_usdt,
    ADD COLUMN profit_lock_rebase_started_at TIMESTAMP(6) NULL AFTER profit_lock_state;
UPDATE wallet_position_test
SET profit_lock_state = 'ACTIVE'
WHERE profit_lock_active = 1;

ALTER TABLE wallet_position_test_archive
    ADD COLUMN profit_lock_state VARCHAR(40) NOT NULL DEFAULT 'INACTIVE' AFTER profit_lock_price_usdt,
    ADD COLUMN profit_lock_rebase_started_at TIMESTAMP(6) NULL AFTER profit_lock_state;
UPDATE wallet_position_test_archive
SET profit_lock_state = 'ACTIVE'
WHERE profit_lock_active = 1;

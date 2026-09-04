-- FIX-11T: Production + Replay Near-TP Failure Protection state.
-- All state is position-scoped and persisted so restarts cannot duplicate bearish streaks
-- or forget a completed partial harvest. Missing/stale signal evidence never confirms a sell.
ALTER TABLE wallet_managed_position
    ADD COLUMN near_tp_state VARCHAR(40) NOT NULL DEFAULT 'INACTIVE' AFTER last_scale_in_at,
    ADD COLUMN near_tp_best_price DECIMAL(30,12) NULL AFTER near_tp_state,
    ADD COLUMN near_tp_bearish_streak INT NOT NULL DEFAULT 0 AFTER near_tp_best_price,
    ADD COLUMN near_tp_last_1m_signal_id BIGINT NULL AFTER near_tp_bearish_streak,
    ADD COLUMN near_tp_harvest_used TINYINT(1) NOT NULL DEFAULT 0 AFTER near_tp_last_1m_signal_id,
    ADD COLUMN near_tp_harvested_quantity DECIMAL(30,12) NULL AFTER near_tp_harvest_used;

-- Replay carries the exact same trading-rule state. The archive table must evolve in lockstep
-- because RegressionTestService archives rows with INSERT ... SELECT * semantics.
ALTER TABLE wallet_position_test
    ADD COLUMN near_tp_state VARCHAR(40) NOT NULL DEFAULT 'INACTIVE' AFTER profit_lock_price_usdt,
    ADD COLUMN near_tp_best_price DECIMAL(30,12) NULL AFTER near_tp_state,
    ADD COLUMN near_tp_bearish_streak INT NOT NULL DEFAULT 0 AFTER near_tp_best_price,
    ADD COLUMN near_tp_last_1m_signal_id BIGINT NULL AFTER near_tp_bearish_streak,
    ADD COLUMN near_tp_harvest_used TINYINT(1) NOT NULL DEFAULT 0 AFTER near_tp_last_1m_signal_id,
    ADD COLUMN near_tp_harvested_quantity DECIMAL(30,12) NULL AFTER near_tp_harvest_used;

ALTER TABLE wallet_position_test_archive
    ADD COLUMN near_tp_state VARCHAR(40) NOT NULL DEFAULT 'INACTIVE' AFTER profit_lock_price_usdt,
    ADD COLUMN near_tp_best_price DECIMAL(30,12) NULL AFTER near_tp_state,
    ADD COLUMN near_tp_bearish_streak INT NOT NULL DEFAULT 0 AFTER near_tp_best_price,
    ADD COLUMN near_tp_last_1m_signal_id BIGINT NULL AFTER near_tp_bearish_streak,
    ADD COLUMN near_tp_harvest_used TINYINT(1) NOT NULL DEFAULT 0 AFTER near_tp_last_1m_signal_id,
    ADD COLUMN near_tp_harvested_quantity DECIMAL(30,12) NULL AFTER near_tp_harvest_used;

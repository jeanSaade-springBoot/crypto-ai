-- DEBUG ONLY: Upgrade the V47 threshold/tick monitor into a stateful market move tracker.
-- This table remains isolated from signal generation, execution and wallet logic.

ALTER TABLE price_move_monitor_settings
    ADD COLUMN minimum_duration_minutes INT NOT NULL DEFAULT 1 AFTER window_minutes,
    ADD COLUMN retracement_close_percent DECIMAL(12,6) NOT NULL DEFAULT 30.000000 AFTER minimum_duration_minutes,
    ADD COLUMN cooldown_minutes INT NOT NULL DEFAULT 10 AFTER retracement_close_percent;

UPDATE price_move_monitor_settings
SET minimum_duration_minutes = 1,
    retracement_close_percent = 30.000000,
    cooldown_minutes = 10
WHERE id = 1;

-- V47 rows were threshold-tick events and are intentionally cleared so the
-- new week starts with only one-row-per-completed-move tracker semantics.
DELETE FROM price_move_event;

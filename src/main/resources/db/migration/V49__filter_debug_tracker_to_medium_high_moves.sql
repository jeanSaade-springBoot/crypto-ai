ALTER TABLE price_move_event
    ADD COLUMN importance_level VARCHAR(10) NULL AFTER duration_seconds;

UPDATE price_move_event
SET importance_level = CASE
    WHEN ABS(change_percent) >= 1.500000 THEN 'HIGH'
    WHEN ABS(change_percent) >= 0.750000 THEN 'MEDIUM'
    ELSE 'LOW'
END;

DELETE FROM price_move_event
WHERE duration_seconds <= 300
   OR ABS(change_percent) < 0.750000;

ALTER TABLE price_move_event
    MODIFY COLUMN importance_level VARCHAR(10) NOT NULL;

UPDATE price_move_monitor_settings
SET minimum_duration_minutes = GREATEST(minimum_duration_minutes, 6)
WHERE id = 1;

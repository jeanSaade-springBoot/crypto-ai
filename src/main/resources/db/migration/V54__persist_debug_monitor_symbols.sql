ALTER TABLE price_move_monitor_settings
    ADD COLUMN selected_symbols VARCHAR(1000) NOT NULL DEFAULT 'BNBUSDT' AFTER retention_days;

UPDATE price_move_monitor_settings
SET selected_symbols = 'BNBUSDT'
WHERE selected_symbols IS NULL OR TRIM(selected_symbols) = '';

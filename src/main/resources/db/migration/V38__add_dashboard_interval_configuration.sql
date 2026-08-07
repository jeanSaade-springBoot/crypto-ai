ALTER TABLE crypto_ai.wallet_settings
    ADD COLUMN dashboard_intervals VARCHAR(100) NOT NULL DEFAULT '1m,5m,1h,4h,1d'
    AFTER performance_end_date;

UPDATE crypto_ai.wallet_settings
SET dashboard_intervals = '1m,5m,1h,4h,1d'
WHERE dashboard_intervals IS NULL OR dashboard_intervals = '';

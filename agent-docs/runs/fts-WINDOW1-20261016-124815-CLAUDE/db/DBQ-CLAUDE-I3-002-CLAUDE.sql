SELECT TABLE_NAME, ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='crypto_ai'
  AND TABLE_NAME IN ('fix129_stage_timing','wallet_snapshot','wallet_asset','daily_statistics','candle')
ORDER BY TABLE_NAME, ORDINAL_POSITION;
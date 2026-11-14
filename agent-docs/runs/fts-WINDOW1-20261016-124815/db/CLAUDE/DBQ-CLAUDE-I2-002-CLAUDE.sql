SELECT TABLE_NAME, ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'crypto_ai'
  AND TABLE_NAME IN ('candle','position_management_event','position_analysis')
ORDER BY TABLE_NAME, ORDINAL_POSITION;
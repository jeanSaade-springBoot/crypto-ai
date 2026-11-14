SELECT table_name, ordinal_position, column_name, column_type
FROM information_schema.columns
WHERE table_schema = 'crypto_ai'
  AND table_name IN ('candle')
ORDER BY ordinal_position
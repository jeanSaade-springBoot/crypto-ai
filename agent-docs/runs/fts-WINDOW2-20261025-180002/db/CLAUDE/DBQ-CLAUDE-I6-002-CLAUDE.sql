SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'crypto_ai' AND table_name IN ('paper_position','wallet_trade','wallet')
  AND (column_name LIKE '%fee%' OR column_name LIKE '%commission%')
ORDER BY table_name, column_name
LIMIT 30;
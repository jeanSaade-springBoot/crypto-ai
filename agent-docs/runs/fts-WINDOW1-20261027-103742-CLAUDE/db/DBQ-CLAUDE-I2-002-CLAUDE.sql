SELECT table_name, ordinal_position, column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'crypto_ai'
  AND (table_name = 'position_management_event' OR table_name LIKE '%execution%' OR table_name LIKE '%decision%')
ORDER BY table_name, ordinal_position
LIMIT 300
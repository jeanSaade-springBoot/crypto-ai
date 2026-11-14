SELECT table_name, ordinal_position, column_name, column_type FROM information_schema.columns
WHERE table_schema='crypto_ai' AND (table_name='buy_htf_assessment_audit' OR table_name LIKE '%execution%audit%' OR table_name LIKE '%decision%audit%' OR table_name LIKE '%signal_work%')
ORDER BY table_name, ordinal_position
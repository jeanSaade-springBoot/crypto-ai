SELECT installed_rank, version, description, script, checksum, installed_on, success
FROM flyway_schema_history
WHERE version IN ('106','107','108','109');
SELECT table_name, create_time
FROM information_schema.tables
WHERE table_schema = 'crypto_ai'
  AND (table_name LIKE '%buy%eval%' OR table_name LIKE '%higher%' OR table_name LIKE '%htf%' OR table_name LIKE '%fix154%' OR table_name LIKE '%fix_154%');
SELECT DATE(generated_at) AS d, COUNT(*) AS n, SUM(data_quality_valid IS NULL) AS dq_null, SUM(data_quality_valid = 1) AS dq_true, SUM(data_quality_valid = 0) AS dq_false
FROM trade_signal
WHERE generated_at BETWEEN '2026-10-08 00:00:00' AND '2026-10-16 00:00:00'
GROUP BY DATE(generated_at)
ORDER BY d
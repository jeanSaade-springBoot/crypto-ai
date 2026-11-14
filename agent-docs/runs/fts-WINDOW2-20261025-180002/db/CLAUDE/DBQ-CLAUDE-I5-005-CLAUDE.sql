SELECT installed_rank, version, description, installed_on, success
FROM crypto_ai.flyway_schema_history
WHERE installed_rank > 60
ORDER BY installed_rank
LIMIT 120;
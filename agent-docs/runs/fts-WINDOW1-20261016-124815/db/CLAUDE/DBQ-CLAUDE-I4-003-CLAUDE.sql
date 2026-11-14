SELECT installed_rank, version, description, installed_on, success
FROM crypto_ai.flyway_schema_history
WHERE installed_on >= '2026-09-20 00:00:00'
ORDER BY installed_rank;
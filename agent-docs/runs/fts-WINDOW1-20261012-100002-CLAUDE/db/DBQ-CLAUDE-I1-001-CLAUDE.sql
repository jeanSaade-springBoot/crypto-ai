SELECT installed_rank, version, description, installed_on, success
FROM crypto_ai.flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 15;
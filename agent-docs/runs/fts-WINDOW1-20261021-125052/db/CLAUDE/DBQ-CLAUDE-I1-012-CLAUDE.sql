SELECT installed_rank, version, description, type, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 25
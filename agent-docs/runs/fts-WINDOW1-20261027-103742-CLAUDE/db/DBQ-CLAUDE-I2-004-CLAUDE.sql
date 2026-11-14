SELECT *
FROM position_management_event
WHERE wallet_position_id IN (166,171,172,187,191,201,211)
ORDER BY wallet_position_id, occurred_at
LIMIT 100
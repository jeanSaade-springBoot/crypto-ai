SELECT *
FROM crypto_ai.position_management_event
WHERE wallet_position_id IN (211,201,191,187,172,171,166)
ORDER BY wallet_position_id DESC, id
LIMIT 300;
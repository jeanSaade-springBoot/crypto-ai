SELECT id, signal_id, execution_key, side, execution_reason, executed_at,
       LEFT(execution_message, 3000) AS execution_message_head
FROM wallet_trade
WHERE id IN (451,426,403,392,358,357,344)
ORDER BY id DESC;
SELECT id, signal_id, symbol, execution_reason, UNIX_TIMESTAMP(executed_at) AS exec_epoch,
       LEFT(execution_message, 2000) AS msg
FROM wallet_trade
WHERE id IN (344,358,392,403,426,451)
ORDER BY id;
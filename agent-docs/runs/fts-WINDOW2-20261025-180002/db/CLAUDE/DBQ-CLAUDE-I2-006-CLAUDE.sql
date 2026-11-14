SELECT id, signal_id, position_analysis_id, execution_key, symbol, side, price_usdt, decision_price_usdt, execution_price_observed_at, executed_at, created_at, execution_reason, LEFT(execution_message, 800) AS exec_msg_head
FROM crypto_ai.wallet_trade
WHERE id IN (451,454,426,429,403,407,392,401,357,358,359,344,346)
ORDER BY id DESC
LIMIT 13;
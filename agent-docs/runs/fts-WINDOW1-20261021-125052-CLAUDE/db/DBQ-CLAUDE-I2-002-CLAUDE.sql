SELECT id, signal_id, execution_key, symbol, side, price_usdt, decision_price_usdt, execution_price_observed_at, execution_type, execution_reason, executed_at, created_at, LEFT(execution_message, 4000) AS execution_message
FROM wallet_trade
WHERE id IN (451,454,426,429,403,407,392,401,358,359,344,346)
ORDER BY id
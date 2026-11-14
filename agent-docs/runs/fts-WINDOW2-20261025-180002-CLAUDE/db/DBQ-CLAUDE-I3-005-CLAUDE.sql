SELECT w.id, w.symbol, w.executed_at, w.price_usdt, w.signal_id, w.execution_key,
 (SELECT e.price FROM crypto_ai.market_price_event e WHERE e.symbol = w.symbol AND e.delivery_status = 'APPLIED' AND e.observed_at <= w.executed_at ORDER BY e.observed_at DESC LIMIT 1) AS latest_tick_price,
 (SELECT e.observed_at FROM crypto_ai.market_price_event e WHERE e.symbol = w.symbol AND e.delivery_status = 'APPLIED' AND e.observed_at <= w.executed_at ORDER BY e.observed_at DESC LIMIT 1) AS latest_tick_at
FROM crypto_ai.wallet_trade w
WHERE w.side = 'SELL' AND w.execution_reason = 'SIGNAL_SELL'
ORDER BY w.executed_at DESC
LIMIT 40;
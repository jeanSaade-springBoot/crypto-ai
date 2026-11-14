SELECT w.id AS wallet_id, w.symbol, w.execution_reason, w.signal_id, w.price_usdt, w.executed_at, p.id AS position_id, p.status, p.realized_pnl, p.close_reason, p.closed_at
FROM crypto_ai.wallet_trade w
LEFT JOIN crypto_ai.paper_position p ON p.signal_id = w.signal_id
WHERE w.id IN (358, 305, 255, 199, 119, 72)
ORDER BY w.id
LIMIT 12;
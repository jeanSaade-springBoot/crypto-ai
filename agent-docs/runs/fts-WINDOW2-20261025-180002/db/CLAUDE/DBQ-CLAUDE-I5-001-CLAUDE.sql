SELECT p.id AS position_id, e.id AS entry_signal, s.id, s.generated_at, s.decision, s.latest_price, s.total_score
FROM crypto_ai.paper_position p
JOIN crypto_ai.trade_signal e ON e.id = p.signal_id
JOIN crypto_ai.trade_signal s ON s.symbol = p.symbol AND s.interval_code = '1m'
 AND s.generated_at BETWEEN e.generated_at - INTERVAL 30 MINUTE AND e.generated_at
WHERE p.id IN (182,172,162,158,143,137)
ORDER BY p.id DESC, s.generated_at
LIMIT 220;
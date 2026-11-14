SELECT p.id AS position_id, 'ENTRY' AS role, s.id AS signal_id, s.symbol, s.interval_code, s.decision, s.total_score, s.trend_score, s.volume_score, s.momentum_score, s.sentiment_score, s.fundamental_score, s.latest_price, s.stop_loss, s.take_profit, s.generated_at, LEFT(s.explanation, 1500) AS explanation_head
FROM crypto_ai.paper_position p JOIN crypto_ai.trade_signal s ON s.id = p.signal_id
WHERE p.id IN (182,172,162,158,143,137)
UNION ALL
SELECT p.id, 'EXIT', s.id, s.symbol, s.interval_code, s.decision, s.total_score, s.trend_score, s.volume_score, s.momentum_score, s.sentiment_score, s.fundamental_score, s.latest_price, s.stop_loss, s.take_profit, s.generated_at, LEFT(s.explanation, 1500)
FROM crypto_ai.paper_position p JOIN crypto_ai.trade_signal s ON s.id = p.exit_signal_id
WHERE p.id IN (182,172,162,158,143,137)
ORDER BY position_id DESC, role
LIMIT 12;
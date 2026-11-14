SELECT p.id AS paper_id, s.id, s.interval_code, s.decision, s.total_score, s.latest_price, s.stop_loss, s.take_profit, s.generated_at
FROM paper_position p
JOIN trade_signal s
  ON s.symbol = p.symbol
 AND ( (s.interval_code = '1m' AND s.generated_at BETWEEN p.opened_at - INTERVAL 10 MINUTE AND p.closed_at + INTERVAL 10 SECOND)
    OR (s.interval_code = '5m' AND s.generated_at BETWEEN p.opened_at - INTERVAL 30 MINUTE AND p.closed_at + INTERVAL 10 SECOND)
    OR (s.interval_code = '1h' AND s.generated_at BETWEEN p.opened_at - INTERVAL 3 HOUR AND p.closed_at + INTERVAL 10 SECOND) )
WHERE p.id IN (182,172,162,158,143,137)
ORDER BY p.id DESC, s.interval_code, s.generated_at
LIMIT 600
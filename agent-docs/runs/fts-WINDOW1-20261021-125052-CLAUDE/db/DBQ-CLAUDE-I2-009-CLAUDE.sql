SELECT p.id AS paper_id, p.symbol, p.exit_price, p.stop_loss, p.take_profit,
       MIN(e.price) AS min_60m, MAX(e.price) AS max_60m,
       MIN(CASE WHEN e.price >= p.take_profit THEN e.observed_at END) AS first_tp_touch,
       MIN(CASE WHEN e.price <= p.stop_loss THEN e.observed_at END) AS first_sl_touch
FROM paper_position p
JOIN market_price_event e
  ON e.symbol = p.symbol
 AND e.observed_at BETWEEN p.closed_at AND p.closed_at + INTERVAL 60 MINUTE
WHERE p.id IN (182,172,162,158,143,137)
GROUP BY p.id, p.symbol, p.exit_price, p.stop_loss, p.take_profit
ORDER BY p.id DESC
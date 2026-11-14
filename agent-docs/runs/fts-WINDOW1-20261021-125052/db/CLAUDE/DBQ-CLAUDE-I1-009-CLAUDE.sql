SELECT p.id AS paper_id, e.status, COUNT(*) AS n, MIN(e.observed_at) AS first_obs, MAX(e.observed_at) AS last_obs, MIN(e.price) AS min_price, MAX(e.price) AS max_price
FROM paper_position p
JOIN market_price_event e
  ON e.symbol = p.symbol
 AND e.observed_at BETWEEN p.opened_at - INTERVAL 30 SECOND AND p.closed_at + INTERVAL 5 SECOND
WHERE p.id IN (182,172,162,158,143,137)
GROUP BY p.id, e.status
ORDER BY p.id DESC, e.status
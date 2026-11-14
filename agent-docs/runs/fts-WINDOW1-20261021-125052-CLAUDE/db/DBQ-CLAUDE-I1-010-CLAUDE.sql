SELECT e.*
FROM market_price_event e
WHERE e.symbol = 'EGLDUSDT'
  AND e.observed_at BETWEEN '2026-10-11 22:39:30' AND '2026-10-11 22:43:40'
ORDER BY e.observed_at
LIMIT 400;
SELECT p.id AS paper_id, e.*
FROM paper_position p
JOIN market_price_event e
  ON e.symbol = p.symbol
 AND e.observed_at BETWEEN p.closed_at - INTERVAL 40 SECOND AND p.closed_at + INTERVAL 2 SECOND
WHERE p.id IN (182,172,162,158,137)
ORDER BY p.id DESC, e.observed_at
LIMIT 300
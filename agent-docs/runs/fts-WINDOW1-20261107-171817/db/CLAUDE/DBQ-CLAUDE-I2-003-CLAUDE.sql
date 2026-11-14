SELECT id, observed_at, price, source, source_received_at, delivery_status, ordering_slot,
       outcome_reason, created_at
FROM market_price_event
WHERE symbol = 'EGLDUSDT'
  AND observed_at BETWEEN '2026-10-11 22:39:30' AND '2026-10-11 22:43:40'
ORDER BY observed_at, id
LIMIT 200;

SELECT p.id AS position_id, e.delivery_status, e.source, COUNT(*) AS n,
       MIN(e.observed_at) AS first_obs, MAX(e.observed_at) AS last_obs
FROM paper_position p
JOIN market_price_event e ON e.symbol = p.symbol
 AND e.observed_at BETWEEN p.opened_at AND p.closed_at
WHERE p.id IN (182,172,162,158,143,137)
GROUP BY p.id, e.delivery_status, e.source
ORDER BY p.id DESC;
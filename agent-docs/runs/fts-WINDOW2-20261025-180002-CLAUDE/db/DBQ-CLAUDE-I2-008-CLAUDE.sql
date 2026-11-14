SELECT p.id AS position_id, e.source, e.delivery_status, e.outcome_reason, COUNT(*) AS n, MIN(e.observed_at) AS first_obs, MAX(e.observed_at) AS last_obs
FROM crypto_ai.paper_position p
JOIN crypto_ai.market_price_event e ON e.symbol = p.symbol AND e.observed_at BETWEEN p.opened_at AND p.closed_at
WHERE p.id IN (182,172,162,158,143,137)
GROUP BY p.id, e.source, e.delivery_status, e.outcome_reason
ORDER BY p.id DESC, n DESC
LIMIT 60;
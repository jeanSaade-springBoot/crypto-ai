SELECT id, observed_at, price, source, source_received_at, delivery_status, outcome_reason
FROM market_price_event
WHERE symbol = 'EGLDUSDT'
  AND observed_at BETWEEN '2026-10-11 22:39:30' AND '2026-10-11 22:43:40'
ORDER BY observed_at, id
LIMIT 600
SELECT * FROM (
  SELECT p.id AS position_id, e.id, e.observed_at, e.price, e.delivery_status, e.source,
         ROW_NUMBER() OVER (PARTITION BY p.id ORDER BY e.observed_at ASC) AS rn_first,
         ROW_NUMBER() OVER (PARTITION BY p.id ORDER BY e.observed_at DESC) AS rn_last
  FROM crypto_ai.paper_position p
  JOIN crypto_ai.market_price_event e ON e.symbol = p.symbol AND e.observed_at BETWEEN p.opened_at AND p.closed_at
  WHERE p.id IN (172,162,158)
) t
WHERE rn_first <= 5 OR rn_last <= 15
ORDER BY position_id DESC, observed_at
LIMIT 60;
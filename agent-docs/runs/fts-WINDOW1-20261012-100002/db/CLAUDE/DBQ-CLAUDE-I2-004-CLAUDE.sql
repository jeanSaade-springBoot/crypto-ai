SELECT * FROM (
  SELECT pp.id AS paper_position_id, e.id, e.observed_at, e.source_received_at, e.created_at, e.price, e.delivery_status, e.ordering_slot, e.outcome_reason,
         ROW_NUMBER() OVER (PARTITION BY pp.id ORDER BY e.observed_at DESC, e.id DESC) AS rn
  FROM crypto_ai.paper_position pp
  JOIN crypto_ai.market_price_event e ON e.symbol = pp.symbol AND e.observed_at BETWEEN pp.closed_at - INTERVAL 120 SECOND AND pp.closed_at + INTERVAL 5 SECOND
  WHERE pp.id IN (172,162,158,143,137,131)) x
WHERE rn <= 20 ORDER BY paper_position_id, observed_at, id;
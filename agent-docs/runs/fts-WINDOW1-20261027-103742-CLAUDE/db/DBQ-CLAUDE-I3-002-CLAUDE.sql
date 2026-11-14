SELECT pp.id AS trade_id, mpe.id, mpe.observed_at, mpe.price, mpe.delivery_status, mpe.outcome_reason, mpe.created_at
FROM paper_position pp
JOIN market_price_event mpe
  ON mpe.symbol = pp.symbol
 AND mpe.observed_at BETWEEN pp.closed_at - INTERVAL 45 SECOND AND pp.closed_at + INTERVAL 2 SECOND
WHERE pp.id IN (182,172,162,158,143,137)
ORDER BY pp.id, mpe.observed_at, mpe.id
LIMIT 200
SELECT pp.id AS trade_id, mpe.delivery_status, mpe.outcome_reason, COUNT(*) AS n, MIN(mpe.observed_at) AS first_utc, MAX(mpe.observed_at) AS last_utc
FROM paper_position pp
JOIN market_price_event mpe
  ON mpe.symbol = pp.symbol
 AND mpe.observed_at BETWEEN pp.opened_at - INTERVAL 5 MINUTE AND pp.closed_at + INTERVAL 1 MINUTE
WHERE pp.id IN (182,172,162,158,143,137)
  AND mpe.delivery_status NOT IN ('APPLIED','LEGACY')
GROUP BY pp.id, mpe.delivery_status, mpe.outcome_reason
LIMIT 50
SELECT trade_id, COUNT(*) AS obs, SUM(gap_ms > 2500) AS gaps_over_2_5s, SUM(gap_ms > 5000) AS gaps_over_5s, SUM(gap_ms > 10000) AS gaps_over_10s, MAX(gap_ms) AS max_gap_ms
FROM (
  SELECT pp.id AS trade_id,
         TIMESTAMPDIFF(MICROSECOND, LAG(mpe.observed_at) OVER (PARTITION BY pp.id ORDER BY mpe.observed_at, mpe.id), mpe.observed_at) / 1000 AS gap_ms
  FROM paper_position pp
  JOIN market_price_event mpe
    ON mpe.symbol = pp.symbol
   AND mpe.observed_at BETWEEN pp.opened_at AND pp.closed_at
   AND mpe.delivery_status IN ('APPLIED','LEGACY')
  WHERE pp.id IN (182,172,162,158,143,137)
) g
GROUP BY trade_id
ORDER BY trade_id
LIMIT 10
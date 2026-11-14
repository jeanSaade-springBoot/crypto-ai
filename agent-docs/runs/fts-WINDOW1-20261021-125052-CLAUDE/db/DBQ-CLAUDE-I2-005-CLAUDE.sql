SELECT p.id AS paper_id, e.source, e.delivery_status, e.outcome_reason, COUNT(*) AS n, MIN(e.observed_at) AS first_obs, MAX(e.observed_at) AS last_obs, MIN(e.price) AS min_price, MAX(e.price) AS max_price, MIN(TIMESTAMPDIFF(MICROSECOND, e.observed_at, e.created_at)) AS min_ingest_lag_us, MAX(TIMESTAMPDIFF(MICROSECOND, e.observed_at, e.created_at)) AS max_ingest_lag_us
FROM paper_position p
JOIN market_price_event e
  ON e.symbol = p.symbol
 AND e.observed_at BETWEEN p.opened_at - INTERVAL 30 SECOND AND p.closed_at + INTERVAL 5 SECOND
WHERE p.id IN (182,172,162,158,143,137)
GROUP BY p.id, e.source, e.delivery_status, e.outcome_reason
ORDER BY p.id DESC;
SELECT x.paper_id, MAX(x.gap_ms) AS max_gap_ms
FROM (
  SELECT p.id AS paper_id,
         TIMESTAMPDIFF(MICROSECOND, LAG(e.observed_at) OVER (PARTITION BY p.id ORDER BY e.observed_at), e.observed_at) / 1000 AS gap_ms
  FROM paper_position p
  JOIN market_price_event e
    ON e.symbol = p.symbol
   AND e.observed_at BETWEEN p.opened_at AND p.closed_at
  WHERE p.id IN (182,172,162,158,143,137)
) x
GROUP BY x.paper_id
SELECT p.id AS pid, e.source, e.delivery_status, e.outcome_reason, COUNT(*) AS n,
       MIN(e.price) AS min_px, MAX(e.price) AS max_px,
       UNIX_TIMESTAMP(MIN(e.observed_at)) AS first_epoch, UNIX_TIMESTAMP(MAX(e.observed_at)) AS last_epoch,
       MAX(TIMESTAMPDIFF(MICROSECOND, e.observed_at, e.created_at))/1000 AS max_observe_to_persist_ms
FROM paper_position p
JOIN market_price_event e
  ON e.symbol = p.symbol AND e.observed_at BETWEEN p.opened_at AND p.closed_at
WHERE p.id IN (182,172,162,158,143,137)
GROUP BY p.id, e.source, e.delivery_status, e.outcome_reason
ORDER BY p.id DESC
LIMIT 100;
SELECT pid, MAX(gap_ms) AS max_gap_ms, AVG(gap_ms) AS avg_gap_ms, COUNT(*) AS n
FROM (
  SELECT p.id AS pid,
         TIMESTAMPDIFF(MICROSECOND,
           LAG(e.observed_at) OVER (PARTITION BY p.id ORDER BY e.observed_at), e.observed_at)/1000 AS gap_ms
  FROM paper_position p
  JOIN market_price_event e
    ON e.symbol = p.symbol AND e.observed_at BETWEEN p.opened_at AND p.closed_at
  WHERE p.id IN (182,172,162,158,143,137)
) x
GROUP BY pid;
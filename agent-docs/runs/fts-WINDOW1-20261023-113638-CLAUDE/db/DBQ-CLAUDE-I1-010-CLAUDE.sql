SELECT pp_id, COUNT(*) AS n, MAX(gap_s) AS max_gap_s, SUM(gap_s > 10) AS gaps_over_10s
FROM (
  SELECT pp.id AS pp_id,
         TIMESTAMPDIFF(SECOND, LAG(m.observed_at) OVER (PARTITION BY pp.id ORDER BY m.observed_at, m.id), m.observed_at) AS gap_s
  FROM paper_position pp
  JOIN market_price_event m ON m.symbol = pp.symbol
   AND m.observed_at BETWEEN pp.opened_at AND pp.closed_at
  WHERE pp.id IN (182,172,162,158,143,137)
) t GROUP BY pp_id ORDER BY pp_id DESC;
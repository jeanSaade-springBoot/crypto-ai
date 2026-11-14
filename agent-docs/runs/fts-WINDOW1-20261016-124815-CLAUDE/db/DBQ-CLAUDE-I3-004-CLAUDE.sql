SELECT w.pos, COUNT(*) AS events, COUNT(DISTINCT m.symbol) AS symbols,
       MAX(TIMESTAMPDIFF(MICROSECOND, m.observed_at, m.created_at))/1000 AS max_lag_ms,
       SUM(TIMESTAMPDIFF(MICROSECOND, m.observed_at, m.created_at) > 500000) AS lag_over_500ms,
       SUM(m.delivery_status <> 'APPLIED') AS non_applied
FROM (SELECT 182 pos, '2026-10-12 10:05:17' a, '2026-10-12 10:05:24' b UNION ALL
      SELECT 172, '2026-10-12 07:15:42', '2026-10-12 07:15:48' UNION ALL
      SELECT 158, '2026-10-12 03:30:03', '2026-10-12 03:30:09' UNION ALL
      SELECT 143, '2026-10-11 22:43:34', '2026-10-11 22:43:39' UNION ALL
      SELECT 137, '2026-10-11 21:00:07', '2026-10-11 21:00:15') w
JOIN crypto_ai.market_price_event m ON m.observed_at BETWEEN w.a AND w.b
GROUP BY w.pos ORDER BY w.pos DESC;
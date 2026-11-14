SELECT SECOND(e.observed_at) AS sec_of_min, COUNT(*) AS n, ROUND(AVG(TIMESTAMPDIFF(MICROSECOND, e.observed_at, e.created_at))/1000,1) AS avg_lag_ms, ROUND(MAX(TIMESTAMPDIFF(MICROSECOND, e.observed_at, e.created_at))/1000,1) AS max_lag_ms
FROM paper_position p JOIN market_price_event e ON e.symbol=p.symbol AND e.observed_at BETWEEN p.opened_at AND p.closed_at
WHERE p.id IN (182,172,162,158,143,137)
GROUP BY SECOND(e.observed_at) ORDER BY sec_of_min
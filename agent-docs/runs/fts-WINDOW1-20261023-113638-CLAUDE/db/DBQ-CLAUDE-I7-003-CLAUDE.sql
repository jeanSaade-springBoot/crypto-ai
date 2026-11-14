SELECT DATE(e.created_at) d, COUNT(*) n,
       ROUND(MIN(TIMESTAMPDIFF(MICROSECOND,e.observed_at,e.created_at))/1000,0) min_lag_ms,
       ROUND(AVG(TIMESTAMPDIFF(MICROSECOND,e.observed_at,e.created_at))/1000,0) avg_lag_ms
FROM market_price_event e WHERE e.delivery_status='APPLIED' AND e.created_at >= '2026-10-06 00:00:00'
GROUP BY DATE(e.created_at) ORDER BY d LIMIT 30;
SELECT COUNT(*) buy_fills,
       ROUND(AVG(TIMESTAMPDIFF(MICROSECOND,wt.execution_price_observed_at,wt.executed_at))/1000,0) avg_fill_age_ms,
       ROUND(MAX(TIMESTAMPDIFF(MICROSECOND,wt.execution_price_observed_at,wt.executed_at))/1000,0) max_fill_age_ms,
       SUM(TIMESTAMPDIFF(MICROSECOND,wt.execution_price_observed_at,wt.executed_at) > 5000000) over_5s
FROM wallet_trade wt WHERE wt.side='BUY' AND wt.executed_at >= '2026-10-06 00:00:00';
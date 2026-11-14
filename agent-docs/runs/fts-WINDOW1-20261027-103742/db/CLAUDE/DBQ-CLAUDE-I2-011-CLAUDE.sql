SELECT DATE(generated_at) AS utc_day, interval_code, COUNT(*) AS signals, SUM(decision IN ('BUY','STRONG_BUY')) AS buys, MAX(generated_at) AS last_generated_utc
FROM trade_signal
WHERE generated_at >= '2026-10-10 00:00:00' AND generated_at < '2026-10-27 08:00:00'
GROUP BY DATE(generated_at), interval_code
ORDER BY utc_day, interval_code
LIMIT 120
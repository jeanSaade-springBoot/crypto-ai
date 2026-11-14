SELECT interval_code,
 COUNT(*) n,
 MIN(generated_at) min_generated_utc,
 MAX(generated_at) max_generated_utc
FROM trade_signal
WHERE generated_at >= UTC_TIMESTAMP() - INTERVAL 4 HOUR
  AND interval_code IN ('1m','5m','1h')
GROUP BY interval_code
ORDER BY interval_code
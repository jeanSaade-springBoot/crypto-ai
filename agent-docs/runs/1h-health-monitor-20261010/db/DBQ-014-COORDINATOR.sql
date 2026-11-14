SELECT interval_code,
 COUNT(*) n_closed,
 MIN(close_time) min_close_utc,
 MAX(close_time) max_close_utc
FROM candle
WHERE closed=1
  AND interval_code IN ('1m','5m','1h')
  AND close_time >= UTC_TIMESTAMP() - INTERVAL 4 HOUR
GROUP BY interval_code
ORDER BY interval_code
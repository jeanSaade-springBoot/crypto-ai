SELECT FLOOR(UNIX_TIMESTAMP(candle_open_time)/3600)*3600 AS hour_epoch,
       (UNIX_TIMESTAMP(candle_open_time)+60) % 300 = 0 AS five_min_boundary,
       COUNT(*) AS n, COUNT(DISTINCT symbol) AS symbols,
       ROUND(AVG(UNIX_TIMESTAMP(generated_at) - UNIX_TIMESTAMP(candle_open_time) - 60),3) AS avg_s,
       ROUND(MAX(UNIX_TIMESTAMP(generated_at) - UNIX_TIMESTAMP(candle_open_time) - 60),3) AS max_s
FROM trade_signal
WHERE interval_code='1m' AND signal_mode='LIVE'
  AND candle_open_time BETWEEN FROM_UNIXTIME(1791734400) AND FROM_UNIXTIME(1791792000)
GROUP BY hour_epoch, five_min_boundary
ORDER BY hour_epoch, five_min_boundary
LIMIT 100;
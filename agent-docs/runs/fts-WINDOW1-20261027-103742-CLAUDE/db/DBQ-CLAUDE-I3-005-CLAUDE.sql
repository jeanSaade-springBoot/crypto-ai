SELECT DATE(m1.generated_at) AS utc_day,
       COUNT(*) AS boundary_1m_signals,
       SUM(m1.generated_at < m5.generated_at) AS one_m_first,
       SUM(m1.generated_at < m5.generated_at AND m1.decision IN ('BUY','STRONG_BUY','WATCH')) AS one_m_first_supportive,
       ROUND(AVG(TIMESTAMPDIFF(MICROSECOND, m1.generated_at, m5.generated_at)) / 1000, 1) AS avg_5m_minus_1m_ms
FROM trade_signal m1
JOIN trade_signal m5
  ON m5.symbol = m1.symbol
 AND m5.interval_code = '5m'
 AND m5.candle_open_time = m1.candle_open_time - INTERVAL 4 MINUTE
WHERE m1.interval_code = '1m'
  AND MINUTE(m1.candle_open_time) % 5 = 4
  AND m1.generated_at >= '2026-10-11 00:00:00' AND m1.generated_at < '2026-10-13 00:00:00'
GROUP BY DATE(m1.generated_at)
ORDER BY utc_day
LIMIT 10
SELECT symbol,
 MAX(generated_at) last_signal_utc,
 TIMESTAMPDIFF(MINUTE, MAX(generated_at), UTC_TIMESTAMP()) minutes_stale
FROM trade_signal
WHERE generated_at >= UTC_TIMESTAMP() - INTERVAL 4 HOUR
  AND interval_code='1h'
GROUP BY symbol
ORDER BY last_signal_utc, symbol
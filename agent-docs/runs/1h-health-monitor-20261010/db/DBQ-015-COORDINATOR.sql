SELECT symbol, open_time, close_time, closed, provenance
FROM candle
WHERE interval_code='1h'
  AND open_time='2026-10-10 14:00:00'
ORDER BY symbol
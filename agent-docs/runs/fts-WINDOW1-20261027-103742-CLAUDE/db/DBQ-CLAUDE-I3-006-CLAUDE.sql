SELECT symbol, interval_code, open_time, close_time, closed, ingestion_source, created_at, updated_at, close_price
FROM candle
WHERE interval_code = '5m'
  AND ((symbol = 'EGLDUSDT' AND open_time IN ('2026-10-11 20:50:00','2026-10-11 22:30:00','2026-10-11 22:35:00'))
    OR (symbol = 'XRPUSDT' AND open_time = '2026-10-12 02:30:00')
    OR (symbol = 'ACEUSDT' AND open_time IN ('2026-10-12 09:45:00','2026-10-12 09:50:00','2026-10-12 09:55:00')))
ORDER BY symbol, open_time
LIMIT 10
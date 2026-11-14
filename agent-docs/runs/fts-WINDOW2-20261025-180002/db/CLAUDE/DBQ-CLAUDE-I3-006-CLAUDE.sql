SELECT s.id, s.symbol, s.candle_open_time, s.latest_price, c.close_price, c.closed, c.ingestion_source, c.updated_at
FROM crypto_ai.trade_signal s
LEFT JOIN crypto_ai.candle c ON c.symbol = s.symbol AND c.interval_code = '1m' AND c.open_time = s.candle_open_time
WHERE s.id IN (176927,172587,168277,166819,159487,156850)
ORDER BY s.id DESC
LIMIT 6;
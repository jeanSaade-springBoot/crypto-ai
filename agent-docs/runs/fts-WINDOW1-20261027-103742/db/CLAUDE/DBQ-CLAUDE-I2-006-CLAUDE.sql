SELECT pp.id AS trade_id, c.symbol, c.open_time, c.close_time, c.open_price, c.high_price, c.low_price, c.close_price, c.volume, c.closed, c.ingestion_source, c.created_at, c.updated_at
FROM paper_position pp
JOIN candle c
  ON c.symbol = pp.symbol
 AND c.interval_code = '1m'
 AND c.open_time BETWEEN pp.opened_at - INTERVAL 5 MINUTE AND pp.closed_at + INTERVAL 2 MINUTE
WHERE pp.id IN (182,172,162,158,143,137)
ORDER BY pp.id, c.open_time
LIMIT 250
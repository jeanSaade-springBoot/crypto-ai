SELECT pp.id AS paper_position_id, c.open_time, c.close_time, c.open_price, c.high_price, c.low_price, c.close_price, c.quote_asset_volume, c.number_of_trades, c.closed, c.ingestion_source, c.updated_at
FROM crypto_ai.paper_position pp
JOIN crypto_ai.candle c ON c.symbol = pp.symbol AND c.interval_code = '1m' AND c.open_time BETWEEN pp.opened_at - INTERVAL 3 MINUTE AND pp.closed_at
WHERE pp.id IN (172,162,158,143,137,131) ORDER BY pp.id, c.open_time LIMIT 260;
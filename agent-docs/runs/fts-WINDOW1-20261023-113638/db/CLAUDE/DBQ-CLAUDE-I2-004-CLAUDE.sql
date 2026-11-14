SELECT id, observed_at, price, source, delivery_status, outcome_reason, source_sequence, source_received_at, created_at
FROM market_price_event WHERE symbol='EGLDUSDT'
 AND observed_at BETWEEN '2026-10-11 22:43:15' AND '2026-10-11 22:43:45' ORDER BY observed_at, id LIMIT 60;
SELECT interval_code, open_time, open_price, high_price, low_price, close_price, volume, closed, ingestion_source, created_at, updated_at
FROM candle WHERE symbol='EGLDUSDT' AND interval_code='1m'
 AND open_time BETWEEN '2026-10-11 22:41:00' AND '2026-10-11 22:44:00' ORDER BY open_time;
SELECT pp.id, MIN(m.price) min_px_in_life, MAX(m.price) max_px_in_life
FROM paper_position pp JOIN market_price_event m ON m.symbol=pp.symbol
 AND m.observed_at BETWEEN pp.opened_at AND pp.closed_at
WHERE pp.id IN (143,162) GROUP BY pp.id;
SELECT p.id AS position_id, c.id AS candle_id, c.symbol, c.interval_code,
       c.open_time, c.close_time, c.closed, c.open_price, c.high_price,
       c.low_price, c.close_price, c.volume, c.created_at,
       s.generated_at,
       TIMESTAMPDIFF(MICROSECOND, c.close_time, s.generated_at)/1e6 AS close_to_signal_s,
       TIMESTAMPDIFF(MICROSECOND, s.generated_at, p.opened_at)/1e6 AS signal_to_open_s
FROM paper_position p
JOIN trade_signal s ON s.id = p.signal_id
JOIN candle c ON c.symbol = s.symbol AND c.interval_code = s.interval_code
             AND c.open_time = s.candle_open_time
WHERE p.id IN (182,172,162,158,143,137)
ORDER BY p.id DESC;
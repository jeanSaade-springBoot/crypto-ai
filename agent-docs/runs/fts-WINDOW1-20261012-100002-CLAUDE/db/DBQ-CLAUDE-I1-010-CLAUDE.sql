SELECT pp.id AS paper_position_id, c.*
FROM crypto_ai.paper_position pp
JOIN crypto_ai.trade_signal s ON s.id = pp.signal_id
JOIN crypto_ai.candle c
  ON c.symbol = s.symbol AND c.interval_code = s.interval_code AND c.open_time = s.candle_open_time
WHERE pp.id IN (172,162,158,143,137,131)
ORDER BY pp.id;
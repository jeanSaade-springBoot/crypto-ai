SELECT p.id AS position_id, s.id, s.candle_open_time, s.generated_at, s.decision, s.original_decision, s.total_score, s.latest_price, s.signal_mode, s.data_quality_valid
FROM crypto_ai.paper_position p
JOIN crypto_ai.trade_signal s ON s.symbol = p.symbol AND s.interval_code = '1m' AND s.generated_at BETWEEN p.opened_at AND p.closed_at
WHERE p.id IN (182,172,162,158,143,137)
ORDER BY p.id DESC, s.generated_at
LIMIT 200;
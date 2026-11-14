SELECT p.id AS position_id, s.id, s.interval_code, s.candle_open_time, s.generated_at, s.decision, s.original_decision, s.total_score, s.trend_score, s.market_regime, s.signal_mode, s.data_quality_valid
FROM crypto_ai.paper_position p
JOIN crypto_ai.trade_signal s ON s.symbol = p.symbol AND s.interval_code IN ('5m','1h') AND s.generated_at BETWEEN p.opened_at - INTERVAL 65 MINUTE AND p.closed_at
WHERE p.id IN (182,172,162,158,143,137)
ORDER BY p.id DESC, s.interval_code, s.generated_at
LIMIT 250;
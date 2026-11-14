SELECT pp.id AS trade_id, ts.id, ts.interval_code, ts.candle_open_time, ts.generated_at, ts.decision, ts.original_decision, ts.total_score, ts.confidence_score, ts.signal_mode, ts.data_quality_valid, ts.latest_price
FROM paper_position pp
JOIN trade_signal ts
  ON ts.symbol = pp.symbol
 AND ts.interval_code IN ('1m','5m','1h')
 AND ts.generated_at BETWEEN pp.opened_at - INTERVAL 10 MINUTE AND pp.closed_at + INTERVAL 1 MINUTE
WHERE pp.id IN (182,172,162,158,143,137)
ORDER BY pp.id, ts.generated_at
LIMIT 400
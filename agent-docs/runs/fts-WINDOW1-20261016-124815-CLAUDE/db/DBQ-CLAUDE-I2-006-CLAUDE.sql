SELECT pp.id AS position_id, ts.id AS signal_id, ts.interval_code, ts.candle_open_time, ts.generated_at,
       ts.decision, ts.original_decision, ts.total_score, ts.confidence_score, ts.market_regime,
       ts.selected_strategy, ts.latest_price, ts.signal_mode, ts.data_quality_valid
FROM crypto_ai.paper_position pp
JOIN crypto_ai.trade_signal ts
  ON ts.symbol = pp.symbol AND ts.interval_code IN ('1m','5m','1h')
 AND ts.generated_at BETWEEN pp.opened_at - INTERVAL 10 MINUTE AND pp.closed_at
WHERE pp.id IN (182,172,162,158,143,137)
ORDER BY pp.id DESC, ts.generated_at, ts.interval_code
LIMIT 400;
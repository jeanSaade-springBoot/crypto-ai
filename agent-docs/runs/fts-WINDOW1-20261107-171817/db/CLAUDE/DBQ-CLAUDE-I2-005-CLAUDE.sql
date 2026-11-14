SELECT x.position_id, x.moment, x.iv, s.id, s.candle_open_time, s.generated_at, s.decision,
       s.original_decision, s.total_score, s.trend_score, s.momentum_score, s.confidence_score,
       s.final_entry_allowed, s.atr_immediate_entry_allowed, s.atr_recommended_position_percent,
       s.data_quality_valid, s.market_regime
FROM (
  SELECT p.id AS position_id, 'ENTRY' AS moment, iv.iv,
         (SELECT t.id FROM trade_signal t WHERE t.symbol = p.symbol AND t.interval_code = iv.iv
            AND t.generated_at <= es.generated_at ORDER BY t.generated_at DESC LIMIT 1) AS sid
  FROM paper_position p JOIN trade_signal es ON es.id = p.signal_id
  CROSS JOIN (SELECT '5m' AS iv UNION ALL SELECT '1h') iv
  WHERE p.id IN (182,172,162,158,143,137)
  UNION ALL
  SELECT p.id, 'EXIT', iv.iv,
         (SELECT t.id FROM trade_signal t WHERE t.symbol = p.symbol AND t.interval_code = iv.iv
            AND t.generated_at <= p.closed_at ORDER BY t.generated_at DESC LIMIT 1)
  FROM paper_position p
  CROSS JOIN (SELECT '5m' AS iv UNION ALL SELECT '1h') iv
  WHERE p.id IN (182,172,162,158,143,137)
) x JOIN trade_signal s ON s.id = x.sid
ORDER BY x.position_id DESC, x.moment, x.iv;
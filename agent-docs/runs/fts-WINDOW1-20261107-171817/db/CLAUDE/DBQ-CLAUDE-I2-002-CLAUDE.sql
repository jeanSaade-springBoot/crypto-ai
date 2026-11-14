SELECT a.paper_position_id, s.id, s.symbol, s.interval_code, s.candle_open_time, s.generated_at,
       s.decision, s.original_decision, s.signal_mode, s.selected_strategy, s.market_regime,
       s.total_score, s.trend_score, s.momentum_score, s.volume_score, s.confidence_score,
       s.data_quality_valid, s.confluence_status, s.confluence_higher_decision, s.latest_price
FROM production_exit_audit a JOIN trade_signal s ON s.id = a.source_signal_id
WHERE a.paper_position_id IN (182,172,162,158,143,137)
ORDER BY a.paper_position_id DESC;
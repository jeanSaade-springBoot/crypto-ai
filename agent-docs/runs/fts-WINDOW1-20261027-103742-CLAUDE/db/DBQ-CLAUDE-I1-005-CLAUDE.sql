SELECT ts.id, ts.symbol, ts.`interval`, ts.candle_open_time, ts.generated_at, ts.created_at, ts.signal_mode, ts.decision, ts.original_decision, ts.total_score, ts.confidence_score, ts.primary_blocking_stage, ts.final_entry_allowed, ts.data_quality_valid, ts.market_regime, ts.selected_strategy, ts.confluence_status, ts.confluence_higher_decision, ts.btc_context_status, ts.latest_price, ts.stop_loss, ts.take_profit, ts.risk_reward_ratio, ts.atr_percent, ts.atr_entry_type, ts.entry_authority
FROM trade_signal ts
WHERE ts.id IN (
  SELECT signal_id FROM paper_position WHERE id IN (182,172,162,158,143,137)
  UNION
  SELECT exit_signal_id FROM paper_position WHERE id IN (182,172,162,158,143,137) AND exit_signal_id IS NOT NULL
)
ORDER BY ts.symbol, ts.generated_at
LIMIT 20
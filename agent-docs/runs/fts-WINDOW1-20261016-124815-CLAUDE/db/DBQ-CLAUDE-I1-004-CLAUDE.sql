SELECT pp.id AS position_id,
       CASE WHEN ts.id = pp.signal_id THEN 'ENTRY' ELSE 'EXIT' END AS role,
       ts.id AS signal_id, ts.symbol, ts.interval_code, ts.candle_open_time, ts.generated_at, ts.created_at,
       ts.decision, ts.original_decision, ts.total_score, ts.confidence_score, ts.raw_confidence_score,
       ts.effective_confidence_score, ts.final_entry_allowed, ts.primary_blocking_stage, ts.entry_authority,
       ts.latest_price, ts.stop_loss, ts.take_profit, ts.risk_reward_ratio, ts.atr_percent, ts.atr_entry_type,
       ts.atr_recommended_position_percent, ts.market_regime, ts.market_regime_confidence, ts.selected_strategy,
       ts.strategy_entry_allowed, ts.confluence_status, ts.confluence_entry_allowed, ts.confluence_higher_interval,
       ts.confluence_higher_decision, ts.btc_context_status, ts.btc_context_entry_allowed, ts.btc_correlation,
       ts.liquidity_status, ts.liquidity_entry_allowed, ts.derivatives_status, ts.derivatives_entry_allowed
FROM crypto_ai.paper_position pp
JOIN crypto_ai.trade_signal ts ON ts.id IN (pp.signal_id, pp.exit_signal_id)
WHERE pp.id IN (182,172,162,158,143,137)
ORDER BY pp.id DESC, role;
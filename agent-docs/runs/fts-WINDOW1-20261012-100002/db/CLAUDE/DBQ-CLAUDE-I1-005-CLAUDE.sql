SELECT pp.id AS paper_position_id,
       CASE WHEN s.id = pp.signal_id THEN 'ENTRY' ELSE 'EXIT' END AS role,
       s.id, s.symbol, s.interval_code, s.candle_open_time, s.generated_at, s.created_at,
       s.signal_mode, s.decision, s.original_decision, s.total_score, s.confidence_score,
       s.raw_confidence_score, s.effective_confidence_score, s.final_entry_allowed,
       s.primary_blocking_stage, s.entry_authority, s.entry_authority_max_position_percent,
       s.selected_strategy, s.market_regime, s.market_regime_confidence,
       s.latest_price, s.stop_loss, s.take_profit, s.risk_reward_ratio,
       s.atr_percent, s.atr_entry_type, s.atr_overextended,
       s.confluence_status, s.confluence_higher_interval, s.confluence_higher_decision,
       s.btc_context_status, s.btc_context_entry_allowed, s.btc_correlation,
       s.btc_context_age_seconds, s.liquidity_status, s.derivatives_status
FROM crypto_ai.paper_position pp
JOIN crypto_ai.trade_signal s ON s.id IN (pp.signal_id, pp.exit_signal_id)
WHERE pp.id IN (172,162,158,143,137,131)
ORDER BY pp.id, role;
SELECT p.id AS position_id, 'ENTRY' AS role, s.id AS signal_id, s.symbol,
       s.interval_code, s.candle_open_time, s.generated_at, s.created_at,
       s.decision, s.original_decision, s.signal_mode, s.selected_strategy, s.market_regime,
       s.confirmed_regime, s.total_score, s.confidence_score, s.effective_confidence_score,
       s.final_entry_allowed, s.primary_blocking_stage, s.data_quality_valid,
       s.confluence_status, s.confluence_entry_allowed, s.confluence_higher_decision,
       s.btc_context_status, s.btc_context_entry_allowed, s.btc_correlation,
       s.btc_context_age_seconds, s.liquidity_status, s.derivatives_status,
       s.latest_price, s.stop_loss, s.take_profit, s.atr_percent, s.risk_reward_ratio,
       s.atr_entry_type, s.atr_overextended, s.entry_authority
FROM paper_position p JOIN trade_signal s ON s.id = p.signal_id
WHERE p.id IN (182,172,162,158,143,137)
UNION ALL
SELECT p.id, 'EXIT', s.id, s.symbol, s.interval_code, s.candle_open_time, s.generated_at,
       s.created_at, s.decision, s.original_decision, s.signal_mode, s.selected_strategy,
       s.market_regime, s.confirmed_regime, s.total_score, s.confidence_score,
       s.effective_confidence_score, s.final_entry_allowed, s.primary_blocking_stage,
       s.data_quality_valid, s.confluence_status, s.confluence_entry_allowed,
       s.confluence_higher_decision, s.btc_context_status, s.btc_context_entry_allowed,
       s.btc_correlation, s.btc_context_age_seconds, s.liquidity_status, s.derivatives_status,
       s.latest_price, s.stop_loss, s.take_profit, s.atr_percent, s.risk_reward_ratio,
       s.atr_entry_type, s.atr_overextended, s.entry_authority
FROM paper_position p JOIN trade_signal s ON s.id = p.exit_signal_id
WHERE p.id IN (182,172,162,158,143,137)
ORDER BY position_id DESC, role;
SELECT ts.id, ts.symbol, ts.interval_code, ts.candle_open_time, ts.generated_at, ts.created_at, ts.signal_mode, ts.data_quality_valid, ts.decision, ts.original_decision, ts.total_score, ts.confidence_score, ts.effective_confidence_score, ts.primary_blocking_stage, ts.final_entry_allowed, ts.market_regime, ts.selected_strategy, ts.confluence_status, ts.confluence_higher_interval, ts.confluence_higher_decision, ts.btc_context_status, ts.latest_price, ts.stop_loss, ts.take_profit, ts.risk_reward_ratio, ts.atr_at_signal, ts.atr_percent, ts.atr_entry_type, ts.atr_overextended, ts.entry_authority
FROM trade_signal ts
WHERE ts.id IN (176710,171589,167668,165412,165415,159408,156723,156850,159487,166819,168277,172587,176927)
ORDER BY ts.symbol, ts.generated_at
LIMIT 20
SELECT id, symbol, interval_code, candle_open_time, generated_at, created_at, decision, original_decision, total_score, confidence_score, primary_blocking_stage, final_entry_allowed, confluence_status, confluence_higher_interval, confluence_higher_decision, btc_context_status, data_quality_valid, atr_entry_type
FROM trade_signal
WHERE id IN (176529,176655,176792,176934,159285,159407,159408,156721,156723,165289,165415,165412)
ORDER BY symbol, generated_at
LIMIT 20
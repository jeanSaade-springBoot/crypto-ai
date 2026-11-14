SELECT id, symbol, interval_code, candle_open_time, generated_at, signal_mode, decision, original_decision, total_score, confidence_score, final_entry_allowed, primary_blocking_stage,
       entry_authority, entry_authority_max_position_percent, selected_strategy, market_regime, latest_price, stop_loss, take_profit, risk_reward_ratio, atr_percent, atr_entry_type,
       atr_overextended, confluence_status, confluence_higher_interval, confluence_higher_decision, data_quality_valid
FROM crypto_ai.trade_signal
WHERE id IN (155629,155652,156071,156325,156723,156850,156852,156853,159408,159487,165412,165415,166819,166821,166047,167668,168251,168277,171589,172201,172587,172588)
ORDER BY symbol, generated_at;
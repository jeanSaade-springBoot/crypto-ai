SELECT id, symbol, interval_code, decision, original_decision, confluence_status,
       UNIX_TIMESTAMP(candle_open_time) AS candle_open_epoch,
       UNIX_TIMESTAMP(generated_at) AS generated_epoch,
       TIMESTAMPDIFF(MICROSECOND, candle_open_time + INTERVAL 60 SECOND, generated_at)/1000000 AS close_to_signal_s,
       confluence_higher_interval, confluence_higher_decision,
       TIMESTAMPDIFF(SECOND, confluence_higher_signal_generated_at, generated_at) AS htf_age_s,
       btc_context_age_seconds, latest_price, stop_loss, take_profit, risk_reward_ratio,
       atr_at_signal, atr_percent, atr_entry_type, atr_recommended_position_percent,
       atr_immediate_entry_allowed, final_entry_allowed, primary_blocking_stage,
       market_regime, selected_strategy, entry_authority, signal_mode, data_quality_valid
FROM trade_signal
WHERE id IN (156723,159408,165412,167668,168277,171589,176710);
SELECT id, symbol, interval_code, generated_at, decision, latest_price, atr_at_signal, atr_percent, atr_entry_type, atr_recommended_position_percent, atr_immediate_entry_allowed, risk_reward_ratio, candle_range_atr_multiple
FROM crypto_ai.trade_signal
WHERE id IN (159285, 165415, 171559, 167616, 156721, 176655)
ORDER BY id
LIMIT 6;
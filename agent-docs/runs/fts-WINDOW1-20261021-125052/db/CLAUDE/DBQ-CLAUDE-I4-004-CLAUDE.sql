SELECT id, symbol, interval_code, candle_open_time, generated_at, decision, original_decision, total_score, confidence_score, latest_price, stop_loss, take_profit, atr_at_signal, atr_immediate_entry_allowed, final_entry_allowed, market_regime, btc_context_status, liquidity_status, entry_authority, entry_authority_max_position_percent
FROM trade_signal
WHERE (symbol='DOGEUSDT' AND id BETWEEN 167580 AND 167680)
   OR (symbol='ACEUSDT' AND id BETWEEN 176600 AND 176720)
ORDER BY symbol, generated_at
LIMIT 80
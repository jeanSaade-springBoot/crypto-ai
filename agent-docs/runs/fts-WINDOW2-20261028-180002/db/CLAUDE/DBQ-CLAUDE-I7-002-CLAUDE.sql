SELECT id, symbol, interval_code, decision, original_decision, latest_price, stop_loss, take_profit,
       risk_reward_ratio, atr_at_signal, atr_recommended_position_percent, atr_immediate_entry_allowed,
       final_entry_allowed, UNIX_TIMESTAMP(candle_open_time) AS candle_open_epoch,
       UNIX_TIMESTAMP(generated_at) AS gen_epoch
FROM trade_signal
WHERE id IN (159285,159407,165415)
   OR (symbol IN ('EGLDUSDT','XRPUSDT') AND interval_code='1h'
       AND ((symbol='EGLDUSDT' AND generated_at BETWEEN FROM_UNIXTIME(1791740000) AND FROM_UNIXTIME(1791747602))
         OR (symbol='XRPUSDT'  AND generated_at BETWEEN FROM_UNIXTIME(1791754000) AND FROM_UNIXTIME(1791761702))))
ORDER BY symbol, interval_code, generated_at
LIMIT 30;
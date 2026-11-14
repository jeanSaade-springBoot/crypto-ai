SHOW COLUMNS FROM trade_signal LIKE '%allowed%';
SELECT id, interval_code, generated_at, decision, original_decision,
       atr_immediate_entry_allowed, final_entry_allowed, atr_recommended_position_percent
FROM trade_signal
WHERE id IN (159285,159407,159408,165412,165415)
ORDER BY id
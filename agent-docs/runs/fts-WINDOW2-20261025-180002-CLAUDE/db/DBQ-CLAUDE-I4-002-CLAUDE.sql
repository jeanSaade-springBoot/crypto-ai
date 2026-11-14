SELECT id, interval_code, generated_at, decision, original_decision, final_entry_allowed, atr_immediate_entry_allowed, atr_recommended_position_percent, confluence_higher_interval, confluence_higher_decision, confluence_higher_signal_generated_at, LEFT(confluence_explanation, 500) AS conf_expl
FROM crypto_ai.trade_signal
WHERE id IN (159285, 159407, 159408)
ORDER BY id
LIMIT 3;
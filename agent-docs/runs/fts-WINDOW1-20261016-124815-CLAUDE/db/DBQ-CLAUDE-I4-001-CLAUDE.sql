SELECT id, symbol, interval_code, candle_open_time, generated_at, created_at, decision, original_decision,
       final_entry_allowed, primary_blocking_stage, strategy_entry_allowed, atr_immediate_entry_allowed, atr_entry_type,
       trend_score, confidence_score, raw_confidence_score, confluence_status, confluence_higher_interval,
       confluence_higher_decision, btc_context_status, risk_reward_ratio,
       LEFT(final_decision_explanation, 500) AS fde_head,
       LEFT(CAST(decision_path AS CHAR), 3000) AS decision_path_head
FROM crypto_ai.trade_signal
WHERE id IN (176529,176655,176692,176710,176934,171559,171589,172201,167616,167617,167646,167668,
             165389,165412,165415,159285,159407,159408,156695,156721,156723,155320)
ORDER BY symbol, generated_at, id;
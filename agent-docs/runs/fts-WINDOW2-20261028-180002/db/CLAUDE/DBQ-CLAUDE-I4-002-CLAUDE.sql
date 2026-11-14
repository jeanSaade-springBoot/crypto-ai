SELECT id, symbol, interval_code, decision, original_decision, selected_strategy, market_regime,
       confluence_status, confluence_entry_allowed, confluence_higher_interval, confluence_higher_decision,
       confluence_higher_trend_score, UNIX_TIMESTAMP(generated_at) AS gen_epoch, LEFT(confluence_explanation, 400) AS expl
FROM trade_signal
WHERE id IN (176655,176792,176934,172588,166821,156852)
ORDER BY id;
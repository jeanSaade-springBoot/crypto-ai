SELECT id, symbol, status, started_at, last_evidence_at, latest_signal_id, evidence_count, buy_count, watch_count,
       five_minute_decision, one_hour_decision, execution_source, decision_code, LEFT(decision_explanation, 400) AS expl, executed_at, updated_at
FROM crypto_ai.execution_opportunity
WHERE symbol IN ('ACEUSDT','SHIBUSDT','DOGEUSDT','XRPUSDT','EGLDUSDT')
ORDER BY symbol, updated_at DESC
LIMIT 50;
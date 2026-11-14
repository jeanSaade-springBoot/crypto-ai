SELECT id, symbol, direction, status, started_at, last_evidence_at, anchor_entry_price, best_entry_price, latest_signal_id, evidence_count, buy_count, watch_count, neutral_count, bearish_count, evidence_score, opportunity_health, average_signal_score, average_confidence, five_minute_decision, one_hour_decision, execution_source, recommended_position_percent, decision_code, LEFT(decision_explanation, 600) AS expl_head, executed_at
FROM crypto_ai.execution_opportunity
WHERE latest_signal_id IN (176710,171589,167668,165412,159408,156723)
   OR (symbol IN ('ACEUSDT','SHIBUSDT','DOGEUSDT','XRPUSDT','EGLDUSDT') AND executed_at BETWEEN '2026-10-11 20:50:00' AND '2026-10-12 10:00:00')
ORDER BY executed_at
LIMIT 30;
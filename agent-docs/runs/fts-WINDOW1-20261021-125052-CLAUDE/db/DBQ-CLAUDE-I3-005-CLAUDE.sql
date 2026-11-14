SELECT id, symbol, status, started_at, last_evidence_at, latest_signal_id, evidence_score, five_minute_decision, one_hour_decision, execution_source, decision_code, LEFT(decision_explanation,1500) AS expl, executed_at, updated_at
FROM execution_opportunity
WHERE (symbol='DOGEUSDT' AND updated_at BETWEEN '2026-10-12 03:58:00' AND '2026-10-12 04:03:00')
   OR (symbol='ACEUSDT' AND updated_at BETWEEN '2026-10-12 09:54:00' AND '2026-10-12 09:58:00')
ORDER BY symbol, updated_at
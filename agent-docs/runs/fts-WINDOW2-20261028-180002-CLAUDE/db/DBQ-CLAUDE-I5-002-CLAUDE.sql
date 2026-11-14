SELECT id, symbol, status, latest_signal_id, UNIX_TIMESTAMP(started_at) AS started_epoch,
       anchor_entry_price, best_entry_price, evidence_count, buy_count, watch_count, neutral_count,
       bearish_count, evidence_score, opportunity_health, five_minute_decision, one_hour_decision,
       execution_source, recommended_position_percent, decision_code,
       UNIX_TIMESTAMP(executed_at) AS executed_epoch, LEFT(decision_explanation, 800) AS expl
FROM execution_opportunity
WHERE latest_signal_id IN (156723,159408,165412,167668,171589,176710)
   OR (symbol='EGLDUSDT' AND started_at BETWEEN '2026-10-11 14:00:00' AND '2026-10-11 20:00:00')
ORDER BY symbol, started_at
LIMIT 60;
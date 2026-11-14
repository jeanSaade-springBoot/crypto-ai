SELECT id, symbol, status, started_at, last_evidence_at, latest_signal_id, evidence_count, buy_count, watch_count,
       bearish_count, five_minute_decision, one_hour_decision, execution_source, decision_code,
       LEFT(decision_explanation, 400) AS expl, executed_at, updated_at
FROM crypto_ai.execution_opportunity
WHERE (symbol='DOGEUSDT' AND updated_at BETWEEN '2026-10-12 03:50:00' AND '2026-10-12 04:03:00')
   OR (symbol='ACEUSDT'  AND updated_at BETWEEN '2026-10-12 09:54:00' AND '2026-10-12 09:58:00')
   OR (symbol='SHIBUSDT' AND updated_at BETWEEN '2026-10-12 06:30:00' AND '2026-10-12 06:37:00')
   OR (symbol='XRPUSDT'  AND updated_at BETWEEN '2026-10-12 02:25:00' AND '2026-10-12 02:36:00')
   OR (symbol='EGLDUSDT' AND updated_at BETWEEN '2026-10-11 20:50:00' AND '2026-10-11 20:56:00')
   OR (symbol='EGLDUSDT' AND updated_at BETWEEN '2026-10-11 22:34:00' AND '2026-10-11 22:41:00')
ORDER BY symbol, updated_at LIMIT 200;
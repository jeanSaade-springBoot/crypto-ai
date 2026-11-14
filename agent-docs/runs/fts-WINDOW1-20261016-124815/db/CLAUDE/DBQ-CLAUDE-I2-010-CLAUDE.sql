SELECT pp.id AS position_id, eo.id AS opportunity_id, eo.symbol, eo.status, eo.started_at, eo.last_evidence_at,
       eo.anchor_entry_price, eo.best_entry_price, eo.latest_signal_id, eo.evidence_count, eo.buy_count,
       eo.watch_count, eo.neutral_count, eo.bearish_count, eo.evidence_score, eo.opportunity_health,
       eo.average_signal_score, eo.average_confidence, eo.five_minute_decision, eo.one_hour_decision,
       eo.execution_source, eo.recommended_position_percent, eo.decision_code,
       LEFT(eo.decision_explanation, 400) AS decision_explanation_head, eo.executed_at
FROM crypto_ai.paper_position pp
JOIN crypto_ai.execution_opportunity eo
  ON eo.symbol = pp.symbol
 AND eo.executed_at BETWEEN pp.opened_at - INTERVAL 30 SECOND AND pp.opened_at + INTERVAL 30 SECOND
WHERE pp.id IN (182,172,162,158,143,137)
UNION ALL
SELECT 158, NULL, w.symbol, w.status, w.candle_open_time, w.updated_at, NULL, NULL, w.signal_id, w.attempts,
       NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, w.interval_code, w.origin, w.failure_stage,
       w.paper_position_id, NULL, LEFT(w.error_message, 400), w.completed_at
FROM crypto_ai.signal_processing_work w
WHERE w.signal_id = 165415;
SELECT o.id, o.symbol, o.status, o.started_at, o.last_evidence_at, o.latest_signal_id, o.evidence_score, o.opportunity_health, o.five_minute_decision, o.one_hour_decision, o.execution_source, o.recommended_position_percent, o.decision_code, LEFT(o.decision_explanation, 2000) AS decision_explanation, o.executed_at, o.updated_at
FROM execution_opportunity o
JOIN paper_position p
  ON p.symbol = o.symbol
 AND o.executed_at BETWEEN p.opened_at - INTERVAL 10 SECOND AND p.opened_at + INTERVAL 10 SECOND
WHERE p.id IN (182,172,162,158,143,137)
ORDER BY o.executed_at
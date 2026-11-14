SELECT p.id, p.symbol, o.execution_source, o.decision_code, s.decision, s.confidence_score,
       ROUND((s.take_profit - s.latest_price)/NULLIF(s.latest_price - s.stop_loss,0),3) AS planned_rr,
       p.realized_pnl, p.close_reason
FROM paper_position p
JOIN trade_signal s ON s.id=p.signal_id
JOIN execution_opportunity o ON o.symbol=p.symbol AND o.executed_at=p.opened_at
WHERE p.status='CLOSED' AND p.id >= 99
ORDER BY p.id
LIMIT 120
SELECT o.execution_source, o.decision_code, COUNT(*) AS n, SUM(p.realized_pnl < 0) AS losers, ROUND(SUM(p.realized_pnl),4) AS pnl_sum, ROUND(AVG(p.realized_pnl),4) AS pnl_avg
FROM paper_position p
JOIN execution_opportunity o ON o.symbol=p.symbol AND o.executed_at=p.opened_at
WHERE p.status='CLOSED' AND p.closed_at IS NOT NULL AND UNIX_TIMESTAMP(p.opened_at) >= UNIX_TIMESTAMP('2026-10-11 00:00:00')
GROUP BY o.execution_source, o.decision_code
ORDER BY n DESC
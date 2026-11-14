SELECT id, symbol, status, realized_pnl, close_reason, opened_at, closed_at
FROM paper_position
WHERE closed_at >= '2026-10-11 20:55:00'
ORDER BY closed_at DESC
LIMIT 60;
SELECT status, MAX(opened_at) AS max_opened, MAX(closed_at) AS max_closed, COUNT(*) AS n
FROM paper_position
GROUP BY status
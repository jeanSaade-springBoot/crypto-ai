SELECT status, close_reason, COUNT(*) AS n, SUM(realized_pnl < 0) AS losers, SUM(realized_pnl) AS pnl_sum, MIN(closed_at) AS first_close, MAX(closed_at) AS last_close
FROM crypto_ai.paper_position WHERE status <> 'OPEN' GROUP BY status, close_reason ORDER BY status, n DESC;
SELECT id, symbol, status, close_reason, realized_pnl, opened_at, closed_at
FROM crypto_ai.paper_position WHERE closed_at >= '2026-10-11 20:00:00' AND realized_pnl < 0 ORDER BY closed_at DESC LIMIT 40;
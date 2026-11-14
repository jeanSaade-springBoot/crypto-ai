SELECT id, symbol, status, close_reason, realized_pnl, opened_at, closed_at
FROM paper_position WHERE status='STOPPED' ORDER BY closed_at DESC LIMIT 10;
SELECT status, COUNT(*) n, MIN(opened_at) first_open, MAX(opened_at) last_open, MAX(closed_at) last_close
FROM paper_position GROUP BY status;
SELECT DATE(opened_at) d, COUNT(*) n, SUM(realized_pnl<0) losses FROM paper_position
WHERE opened_at >= '2026-10-11 00:00:00' GROUP BY DATE(opened_at) ORDER BY d LIMIT 30;
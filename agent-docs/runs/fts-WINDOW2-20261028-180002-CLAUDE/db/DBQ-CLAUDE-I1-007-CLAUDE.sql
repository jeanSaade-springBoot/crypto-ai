SELECT DATE(opened_at) AS d_utc, status, COUNT(*) AS n, SUM(realized_pnl < 0) AS neg
FROM paper_position
WHERE opened_at > '2026-10-12 10:05:20'
  AND opened_at <= '2026-10-28 15:00:05'
GROUP BY DATE(opened_at), status
ORDER BY d_utc
LIMIT 100;
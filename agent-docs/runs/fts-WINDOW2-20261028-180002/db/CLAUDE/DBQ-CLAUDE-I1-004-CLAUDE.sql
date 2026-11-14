SELECT status,
       COUNT(*) AS n,
       SUM(realized_pnl < 0) AS neg,
       MIN(closed_at) AS first_closed_utc,
       MAX(closed_at) AS last_closed_utc,
       SUM(closed_at > '2026-10-12 10:05:20.486035') AS closed_after_trade182
FROM paper_position
WHERE closed_at IS NOT NULL
GROUP BY status;
SELECT status,
       COUNT(*) AS n,
       SUM(realized_pnl < 0) AS neg,
       MIN(closed_at) AS min_closed_utc,
       MAX(closed_at) AS max_closed_utc,
       MAX(opened_at) AS max_opened_utc
FROM paper_position
GROUP BY status
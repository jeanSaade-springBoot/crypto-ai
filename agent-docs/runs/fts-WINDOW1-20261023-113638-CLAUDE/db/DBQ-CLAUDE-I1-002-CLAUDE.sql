SELECT * FROM paper_position WHERE id IN (182,172,162,158,143,137) ORDER BY id DESC;
SELECT id, symbol, status, realized_pnl, opened_at, closed_at,
       (closed_at <= '2026-10-23 08:36:38') AS closed_before_ksa_label_utc,
       (closed_at <= '2026-10-25 10:08:57') AS closed_before_utc_label
FROM paper_position
WHERE status = 'CLOSED' AND closed_at IS NOT NULL AND realized_pnl < 0
ORDER BY closed_at DESC LIMIT 12;
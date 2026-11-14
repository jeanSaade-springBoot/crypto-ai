SELECT 'paper_position' AS src, status AS k, COUNT(*) AS n, MAX(opened_at) AS max_opened, MAX(closed_at) AS max_closed FROM paper_position GROUP BY status
UNION ALL
SELECT 'trade_signal', interval_code, COUNT(*), MAX(generated_at), NULL FROM trade_signal WHERE generated_at >= '2026-10-12 00:00:00' GROUP BY interval_code
SELECT p.id AS paper_id, 'ENTRY' AS role, s.*
FROM paper_position p
JOIN trade_signal s ON s.id = p.signal_id
WHERE p.id IN (182,172,162,158,143,137)
UNION ALL
SELECT p.id, 'EXIT', s.*
FROM paper_position p
JOIN trade_signal s ON s.id = p.exit_signal_id
WHERE p.id IN (182,172,162,158,143,137)
ORDER BY 1 DESC, 2
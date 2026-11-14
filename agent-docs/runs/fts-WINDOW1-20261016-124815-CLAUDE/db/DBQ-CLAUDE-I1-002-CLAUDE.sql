SELECT pp.*, TIMESTAMPDIFF(SECOND, pp.opened_at, pp.closed_at) AS hold_seconds
FROM crypto_ai.paper_position pp
WHERE pp.id IN (182,172,162,158,143,137)
ORDER BY pp.id DESC;
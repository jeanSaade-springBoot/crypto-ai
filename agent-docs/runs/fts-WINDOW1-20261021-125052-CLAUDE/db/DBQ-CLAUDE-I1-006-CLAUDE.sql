SELECT p.id AS paper_id, m.*
FROM paper_position p
JOIN wallet_managed_position m
  ON m.symbol = p.symbol
 AND m.opened_at BETWEEN p.opened_at - INTERVAL 1 MINUTE AND p.opened_at + INTERVAL 1 MINUTE
WHERE p.id IN (182,172,162,158,143,137)
ORDER BY p.id DESC
LIMIT 20
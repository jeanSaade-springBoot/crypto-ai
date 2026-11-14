SELECT p.id AS frozen_id, q.id AS prior_id, q.symbol, q.status, q.close_reason,
       q.realized_pnl, q.opened_at, q.closed_at,
       TIMESTAMPDIFF(MINUTE, q.closed_at, p.opened_at) AS minutes_before_reentry
FROM paper_position p
JOIN paper_position q
  ON q.symbol = p.symbol
 AND q.closed_at <= p.opened_at
 AND q.closed_at >= p.opened_at - INTERVAL 6 HOUR
WHERE p.id IN (182, 172, 162, 158, 143, 137)
ORDER BY p.id DESC, q.closed_at DESC
LIMIT 60;
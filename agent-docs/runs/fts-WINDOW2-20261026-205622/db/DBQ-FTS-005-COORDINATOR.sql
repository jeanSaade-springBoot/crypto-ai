SELECT id, symbol, status, realized_pnl, opened_at, closed_at, close_reason, signal_id
FROM paper_position
WHERE status='CLOSED'
  AND closed_at IS NOT NULL
  AND realized_pnl IS NOT NULL
  AND realized_pnl < 0
ORDER BY closed_at DESC
LIMIT 6
SELECT id, symbol, status, entry_price, exit_price, stop_loss, realized_pnl, close_reason, opened_at, closed_at
FROM crypto_ai.paper_position
WHERE status = 'STOPPED' AND closed_at >= '2026-10-11 21:00:12'
ORDER BY closed_at DESC
LIMIT 30;
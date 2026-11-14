SELECT id, symbol, side, status, quantity, entry_price, stop_loss, take_profit, exit_price, realized_pnl, close_reason, signal_id, exit_signal_id, opened_at, closed_at, TIMESTAMPDIFF(SECOND, opened_at, closed_at) AS held_sec
FROM crypto_ai.paper_position
WHERE id IN (182,172,162,158,143,137)
ORDER BY id DESC
LIMIT 6;
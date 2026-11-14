SELECT id, symbol, side, status, quantity, entry_price, stop_loss, take_profit, exit_price, realized_pnl, close_reason, signal_id, exit_signal_id, opened_at, closed_at, LEFT(entry_reason, 3000) AS entry_reason, LEFT(exit_reason, 3000) AS exit_reason
FROM paper_position
WHERE id IN (182,172,162,158,143,137)
ORDER BY id
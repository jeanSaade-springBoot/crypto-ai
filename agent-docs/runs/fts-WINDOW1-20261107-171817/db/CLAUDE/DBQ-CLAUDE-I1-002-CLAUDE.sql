SELECT id, symbol, side, status, quantity, entry_price, stop_loss, take_profit,
       exit_price, realized_pnl, signal_id, exit_signal_id, close_reason,
       opened_at, closed_at,
       TIMESTAMPDIFF(SECOND, opened_at, closed_at) AS hold_seconds,
       (exit_price - entry_price) / entry_price * 100 AS move_pct,
       (entry_price - stop_loss) / entry_price * 100 AS sl_dist_pct,
       (take_profit - entry_price) / entry_price * 100 AS tp_dist_pct,
       LEFT(entry_reason, 1500) AS entry_reason_head,
       LEFT(exit_reason, 1500) AS exit_reason_head
FROM paper_position
WHERE id IN (182,172,162,158,143,137)
ORDER BY closed_at DESC;
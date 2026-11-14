SELECT id, symbol, side, status, quantity, entry_price, stop_loss, take_profit, exit_price,
       realized_pnl, close_reason, signal_id, exit_signal_id, opened_at, closed_at,
       (exit_price - entry_price) * quantity AS recomputed_pnl,
       (exit_price - entry_price) / entry_price * 100 AS move_pct,
       (take_profit - entry_price) / NULLIF(entry_price - stop_loss, 0) AS planned_rr,
       LEFT(entry_reason, 1500) AS entry_reason_head,
       LEFT(exit_reason, 1500) AS exit_reason_head
FROM paper_position
WHERE id IN (182, 172, 162, 158, 143, 137)
ORDER BY id DESC;
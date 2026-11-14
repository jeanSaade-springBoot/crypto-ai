SELECT id, symbol, status, quantity, entry_price, stop_loss, take_profit, exit_price, realized_pnl,
       close_reason, signal_id, exit_signal_id,
       UNIX_TIMESTAMP(opened_at) AS opened_epoch, UNIX_TIMESTAMP(closed_at) AS closed_epoch,
       LEFT(exit_reason, 600) AS exit_reason_head
FROM paper_position WHERE id = 142;
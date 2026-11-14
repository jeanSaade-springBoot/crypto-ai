SELECT wallet_position_id, id, event_type, old_value_usdt, new_value_usdt, market_price_usdt, occurred_at, LEFT(reason,300) reason
FROM position_management_event
WHERE wallet_position_id IN (211,201,191,187,172,171,166)
ORDER BY wallet_position_id DESC, occurred_at, id LIMIT 200;
SELECT id, entry_signal_id, status, opened_at, updated_at, average_entry_price_usdt, highest_price_usdt, profit_lock_state
FROM wallet_managed_position WHERE id = 171;
SELECT id, status, close_reason, entry_price, exit_price, realized_pnl, signal_id, opened_at, closed_at
FROM paper_position WHERE symbol='EGLDUSDT' AND closed_at BETWEEN '2026-10-11 22:30:00' AND '2026-10-11 22:40:10';
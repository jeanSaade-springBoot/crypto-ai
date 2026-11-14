SELECT id, symbol, entry_price, exit_price, realized_pnl, close_reason, signal_id, opened_at, closed_at
FROM crypto_ai.paper_position
WHERE symbol = 'EGLDUSDT' AND opened_at BETWEEN '2026-10-11 19:00:00' AND '2026-10-12 00:00:00'
ORDER BY opened_at
LIMIT 30;
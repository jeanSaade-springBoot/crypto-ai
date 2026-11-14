SELECT id, symbol, status, entry_price, stop_loss, take_profit, exit_price, realized_pnl, close_reason,
       signal_id, exit_signal_id, opened_at, closed_at
FROM crypto_ai.paper_position
WHERE symbol IN ('ACEUSDT','SHIBUSDT','DOGEUSDT','XRPUSDT','EGLDUSDT')
  AND opened_at BETWEEN '2026-10-11 18:00:00' AND '2026-10-12 12:00:00'
ORDER BY symbol, opened_at
LIMIT 200;
SELECT id, symbol, status, entry_price, exit_price, realized_pnl, close_reason, opened_at, closed_at
FROM paper_position
WHERE symbol IN ('ACEUSDT','SHIBUSDT','DOGEUSDT','XRPUSDT','EGLDUSDT')
  AND opened_at BETWEEN '2026-10-11 18:00:00' AND '2026-10-12 12:00:00'
ORDER BY symbol, opened_at
LIMIT 60
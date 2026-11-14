SELECT pp.close_reason, COUNT(*) AS trades, SUM(pp.realized_pnl < 0) AS losses,
       ROUND(SUM(pp.realized_pnl), 6) AS pnl_usdt,
       ROUND(AVG(pp.realized_pnl / (pp.quantity * pp.entry_price) * 100), 4) AS avg_pnl_pct,
       ROUND(AVG(TIMESTAMPDIFF(SECOND, pp.opened_at, pp.closed_at))) AS avg_hold_s
FROM crypto_ai.paper_position pp
WHERE pp.status IN ('CLOSED','STOPPED') AND pp.opened_at >= '2026-10-05 00:00:00'
GROUP BY pp.close_reason ORDER BY trades DESC LIMIT 50;
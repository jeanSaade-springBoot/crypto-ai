SELECT wt.execution_reason AS entry_route, pp.close_reason, COUNT(*) AS trades,
       SUM(pp.realized_pnl < 0) AS losses, ROUND(SUM(pp.realized_pnl), 6) AS pnl_usdt,
       ROUND(AVG(TIMESTAMPDIFF(SECOND, pp.opened_at, pp.closed_at))) AS avg_hold_s
FROM crypto_ai.paper_position pp
JOIN crypto_ai.wallet_trade wt ON wt.signal_id = pp.signal_id AND wt.side = 'BUY' AND wt.status = 'EXECUTED'
WHERE pp.status IN ('CLOSED','STOPPED') AND pp.closed_at >= '2026-10-05 00:00:00'
GROUP BY wt.execution_reason, pp.close_reason
ORDER BY entry_route, trades DESC
LIMIT 200;
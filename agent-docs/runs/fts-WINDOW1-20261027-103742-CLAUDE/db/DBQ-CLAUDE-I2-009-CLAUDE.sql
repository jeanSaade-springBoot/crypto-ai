SELECT pp.status, pp.close_reason,
       COUNT(*) AS n,
       SUM(pp.realized_pnl < 0) AS losers,
       ROUND(SUM(pp.realized_pnl), 6) AS total_pnl,
       SUM(ts.risk_reward_ratio < 1) AS entry_rr_below_1,
       ROUND(AVG(ts.risk_reward_ratio), 4) AS avg_entry_rr,
       SUM(ABS((ts.latest_price - ts.stop_loss) / ts.latest_price - 0.005) < 0.00001) AS sl_floor_bound,
       ROUND(AVG((ts.take_profit - ts.latest_price) / ts.latest_price * 100), 4) AS avg_tp_pct
FROM paper_position pp
LEFT JOIN trade_signal ts ON ts.id = pp.signal_id
WHERE pp.status IN ('CLOSED','STOPPED')
GROUP BY pp.status, pp.close_reason
ORDER BY pp.status, n DESC
LIMIT 50
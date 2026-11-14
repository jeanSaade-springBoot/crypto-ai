SELECT CASE WHEN s.risk_reward_ratio < 0.5 THEN 'a_<0.5'
            WHEN s.risk_reward_ratio < 1.0 THEN 'b_0.5-1.0'
            WHEN s.risk_reward_ratio < 1.5 THEN 'c_1.0-1.5'
            ELSE 'd_>=1.5' END AS rr_bucket,
       p.status, COUNT(*) AS n, SUM(p.realized_pnl < 0) AS losses,
       SUM(p.realized_pnl >= 0) AS non_losses, SUM(p.realized_pnl) AS sum_pnl,
       AVG((p.exit_price - p.entry_price) / p.entry_price * 100) AS avg_move_pct
FROM paper_position p JOIN trade_signal s ON s.id = p.signal_id
WHERE p.status IN ('CLOSED','STOPPED') AND p.closed_at IS NOT NULL
GROUP BY rr_bucket, p.status
ORDER BY rr_bucket, p.status;

SELECT status, close_reason, COUNT(*) AS n, SUM(realized_pnl < 0) AS losses,
       SUM(realized_pnl) AS sum_pnl, MIN(closed_at) AS first_close, MAX(closed_at) AS last_close
FROM paper_position
WHERE closed_at IS NOT NULL
GROUP BY status, close_reason
ORDER BY n DESC;
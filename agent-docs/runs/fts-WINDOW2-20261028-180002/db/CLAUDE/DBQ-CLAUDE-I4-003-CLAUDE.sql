SELECT CASE WHEN s.risk_reward_ratio IS NULL THEN 'NULL'
            WHEN s.risk_reward_ratio < 0.5 THEN 'a <0.5'
            WHEN s.risk_reward_ratio < 1.0 THEN 'b 0.5-1.0'
            WHEN s.risk_reward_ratio < 1.5 THEN 'c 1.0-1.5'
            ELSE 'd >=1.5' END AS rr_bucket,
       COUNT(*) AS n, SUM(p.realized_pnl > 0) AS wins, SUM(p.realized_pnl < 0) AS losses,
       ROUND(SUM(p.realized_pnl), 6) AS pnl_sum, ROUND(AVG(p.realized_pnl), 6) AS pnl_avg
FROM paper_position p
LEFT JOIN trade_signal s ON s.id = p.signal_id
WHERE p.status IN ('CLOSED','STOPPED') AND p.closed_at IS NOT NULL
GROUP BY rr_bucket
ORDER BY rr_bucket
LIMIT 10;
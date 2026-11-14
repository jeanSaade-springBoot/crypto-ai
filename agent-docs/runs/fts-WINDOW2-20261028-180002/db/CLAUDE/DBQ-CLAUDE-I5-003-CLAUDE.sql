SELECT DATE_FORMAT(p.closed_at, '%Y-%m') AS ym,
       CASE WHEN s.risk_reward_ratio IS NULL THEN 'NULL'
            WHEN s.risk_reward_ratio < 1.0 THEN 'a <1.0'
            WHEN s.risk_reward_ratio < 1.5 THEN 'b 1.0-1.5'
            ELSE 'c >=1.5' END AS rr_bucket,
       p.close_reason, COUNT(*) AS n, SUM(p.realized_pnl > 0) AS wins,
       ROUND(SUM(p.realized_pnl), 6) AS pnl_sum, ROUND(MIN(p.realized_pnl), 6) AS worst
FROM paper_position p
LEFT JOIN trade_signal s ON s.id = p.signal_id
WHERE p.status IN ('CLOSED','STOPPED') AND p.closed_at IS NOT NULL
GROUP BY ym, rr_bucket, p.close_reason
ORDER BY ym, rr_bucket, pnl_sum
LIMIT 120;
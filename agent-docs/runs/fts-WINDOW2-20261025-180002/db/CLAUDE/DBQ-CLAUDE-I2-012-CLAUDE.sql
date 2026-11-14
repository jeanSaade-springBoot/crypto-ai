SELECT CASE WHEN s.risk_reward_ratio IS NULL THEN 'NULL' WHEN s.risk_reward_ratio < 0.75 THEN '<0.75' WHEN s.risk_reward_ratio < 1 THEN '0.75-1' WHEN s.risk_reward_ratio < 1.5 THEN '1-1.5' ELSE '>=1.5' END AS rr_bucket,
       COUNT(*) AS n, SUM(p.realized_pnl > 0) AS wins, SUM(p.realized_pnl < 0) AS losses, ROUND(SUM(p.realized_pnl), 6) AS pnl_sum, MIN(p.opened_at) AS first_open, MAX(p.opened_at) AS last_open
FROM crypto_ai.paper_position p JOIN crypto_ai.trade_signal s ON s.id = p.signal_id
WHERE p.status IN ('CLOSED','STOPPED') AND p.closed_at IS NOT NULL AND p.closed_at <= '2026-10-25 15:00:04'
GROUP BY rr_bucket
ORDER BY rr_bucket
LIMIT 10;
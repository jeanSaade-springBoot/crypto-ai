SELECT CASE WHEN s1.risk_reward_ratio IS NULL THEN 'z_null'
            WHEN s1.risk_reward_ratio < 0.5 THEN 'a_<0.5'
            WHEN s1.risk_reward_ratio < 0.75 THEN 'b_0.5-0.75'
            WHEN s1.risk_reward_ratio < 1 THEN 'c_0.75-1'
            ELSE 'd_>=1' END AS rr_bucket,
       CASE WHEN wmp.entry_quality_score IS NULL THEN 'z_null'
            WHEN wmp.entry_quality_score < 55 THEN 'EQ50-54'
            WHEN wmp.entry_quality_score < 70 THEN 'EQ55-69'
            ELSE 'EQ70+' END AS eq_bucket,
       COUNT(*) AS trades, SUM(pp.realized_pnl < 0) AS losses,
       ROUND(SUM(pp.realized_pnl), 6) AS pnl_usdt,
       ROUND(AVG((pp.entry_price - pp.stop_loss) / pp.entry_price * 100), 3) AS avg_risk_pct,
       ROUND(AVG(s1.atr_percent), 3) AS avg_1m_atr_pct
FROM crypto_ai.paper_position pp
JOIN crypto_ai.trade_signal s1 ON s1.id = pp.signal_id
LEFT JOIN crypto_ai.wallet_managed_position wmp ON wmp.entry_signal_id = pp.signal_id
WHERE pp.status IN ('CLOSED','STOPPED') AND pp.opened_at >= '2026-10-05 00:00:00'
GROUP BY rr_bucket, eq_bucket
ORDER BY rr_bucket, eq_bucket;
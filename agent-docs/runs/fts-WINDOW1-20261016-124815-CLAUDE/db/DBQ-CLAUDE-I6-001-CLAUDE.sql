SELECT CASE WHEN s1.atr_percent < 0.08 THEN 'a_<0.08' WHEN s1.atr_percent < 0.15 THEN 'b_0.08-0.15'
            WHEN s1.atr_percent < 0.30 THEN 'c_0.15-0.30' ELSE 'd_>=0.30' END AS atr_bucket,
       COUNT(*) AS trades, SUM(pp.realized_pnl < 0) AS losses,
       ROUND(SUM(pp.realized_pnl), 6) AS gross_pnl,
       ROUND(SUM(0.001 * pp.quantity * (pp.entry_price + COALESCE(pp.exit_price, pp.entry_price))), 6) AS est_fee_0_1pct,
       ROUND(AVG(pp.realized_pnl / (pp.quantity * pp.entry_price) * 100), 4) AS avg_pnl_pct,
       SUM(pp.close_reason IN ('STOP_LOSS','TAKE_PROFIT')) AS sl_tp_exits
FROM crypto_ai.paper_position pp
JOIN crypto_ai.trade_signal s1 ON s1.id = pp.signal_id
WHERE pp.status IN ('CLOSED','STOPPED') AND pp.opened_at >= '2026-10-05 00:00:00'
GROUP BY atr_bucket ORDER BY atr_bucket;
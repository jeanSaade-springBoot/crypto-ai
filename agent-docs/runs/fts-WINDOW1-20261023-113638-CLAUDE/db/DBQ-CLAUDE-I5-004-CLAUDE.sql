SELECT CASE WHEN (ts.take_profit-ts.latest_price)/ts.latest_price*100 <= 0.15 THEN 'TP<=0.15'
            WHEN (ts.take_profit-ts.latest_price)/ts.latest_price*100 <= 0.20 THEN 'TP<=0.20'
            WHEN (ts.take_profit-ts.latest_price)/ts.latest_price*100 <= 0.30 THEN 'TP<=0.30' ELSE 'TP>0.30' END tp_bucket,
       COUNT(*) n, SUM(pp.realized_pnl>0) wins, ROUND(SUM(pp.realized_pnl),6) gross_pnl,
       ROUND(SUM(pp.quantity*pp.entry_price*0.002),6) est_fee_0_20pct
FROM paper_position pp JOIN trade_signal ts ON ts.id=pp.signal_id
WHERE pp.closed_at IS NOT NULL GROUP BY tp_bucket ORDER BY tp_bucket LIMIT 10;
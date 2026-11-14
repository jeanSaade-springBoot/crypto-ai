SELECT CASE WHEN (ts.take_profit-ts.latest_price)/ts.latest_price*100 <= 0.30 THEN 'TP<=0.30' ELSE 'TP>0.30' END tp_bucket,
       pp.status, pp.exit_reason, COUNT(*) n, SUM(pp.realized_pnl>0) wins, ROUND(SUM(pp.realized_pnl),6) pnl,
       ROUND(AVG(TIMESTAMPDIFF(SECOND, pp.opened_at, pp.closed_at)),0) avg_hold_s
FROM paper_position pp JOIN trade_signal ts ON ts.id=pp.signal_id
WHERE pp.closed_at IS NOT NULL GROUP BY tp_bucket, pp.status, pp.exit_reason ORDER BY tp_bucket, n DESC LIMIT 40;
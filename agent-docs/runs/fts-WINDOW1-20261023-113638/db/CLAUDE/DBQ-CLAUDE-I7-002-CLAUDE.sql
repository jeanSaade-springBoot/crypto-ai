SELECT CASE WHEN (ts.take_profit-ts.latest_price)/ts.latest_price*100 <= 0.30 THEN 'TP<=0.30' ELSE 'TP>0.30' END tp_bucket,
       pp.close_reason, COUNT(*) n, SUM(pp.realized_pnl>0) wins, ROUND(SUM(pp.realized_pnl),6) pnl,
       ROUND(AVG((pp.exit_price-pp.entry_price)/NULLIF(pp.entry_price-pp.stop_loss,0)),3) avg_r,
       ROUND(AVG((pp.take_profit-pp.entry_price)/NULLIF(pp.entry_price-pp.stop_loss,0)),3) planned_r
FROM paper_position pp JOIN trade_signal ts ON ts.id=pp.signal_id
WHERE pp.closed_at IS NOT NULL GROUP BY tp_bucket, pp.close_reason ORDER BY tp_bucket, n DESC LIMIT 40;
SELECT pp.id, pp.symbol, pp.close_reason, LEFT(pp.exit_reason,60) exit_head, pp.realized_pnl, pp.closed_at
FROM paper_position pp WHERE pp.closed_at IS NOT NULL AND pp.exit_reason LIKE '%Entry allowed%' ORDER BY pp.id LIMIT 50;
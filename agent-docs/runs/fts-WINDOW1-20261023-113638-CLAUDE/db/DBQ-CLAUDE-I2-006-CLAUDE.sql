SELECT status, close_reason, COUNT(*) n, SUM(realized_pnl>0) wins, ROUND(SUM(realized_pnl),6) pnl,
       ROUND(AVG((exit_price-entry_price)/entry_price*100),4) avg_move_pct
FROM paper_position WHERE closed_at IS NOT NULL GROUP BY status, close_reason ORDER BY n DESC;
SELECT CASE WHEN ts.risk_reward_ratio < 1 THEN 'RR<1' ELSE 'RR>=1' END rr_bucket,
       CASE WHEN ABS((ts.latest_price-ts.stop_loss)/ts.latest_price*100 - 0.5) < 0.001 THEN 'FLOOR_0.50' ELSE 'ATR_BASED' END stop_basis,
       COUNT(*) n, SUM(pp.realized_pnl>0) wins, ROUND(SUM(pp.realized_pnl),6) pnl,
       ROUND(AVG(ts.atr_percent),4) avg_atr_pct,
       ROUND(AVG((ts.take_profit-ts.latest_price)/ts.latest_price*100),4) avg_tp_pct,
       SUM(pp.status='STOPPED') stopped
FROM paper_position pp JOIN trade_signal ts ON ts.id = pp.signal_id
WHERE pp.closed_at IS NOT NULL GROUP BY rr_bucket, stop_basis;
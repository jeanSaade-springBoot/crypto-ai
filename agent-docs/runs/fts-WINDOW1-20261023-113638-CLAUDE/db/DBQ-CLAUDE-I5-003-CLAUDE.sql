SELECT eo.execution_source, eo.decision_code, COUNT(*) n, SUM(pp.realized_pnl>0) wins, ROUND(SUM(pp.realized_pnl),6) pnl
FROM paper_position pp JOIN execution_opportunity eo ON eo.latest_signal_id=pp.signal_id AND eo.status='EXECUTED'
WHERE pp.closed_at IS NOT NULL GROUP BY eo.execution_source, eo.decision_code ORDER BY n DESC LIMIT 30;
SELECT ts.atr_overextended, ts.decision, ts.market_regime, ts.selected_strategy, COUNT(*) n, SUM(pp.realized_pnl>0) wins, ROUND(SUM(pp.realized_pnl),6) pnl,
       ROUND(AVG((ts.take_profit-ts.latest_price)/ts.latest_price*100),4) avg_tp_pct
FROM paper_position pp JOIN trade_signal ts ON ts.id=pp.signal_id
WHERE pp.closed_at IS NOT NULL GROUP BY ts.atr_overextended, ts.decision, ts.market_regime, ts.selected_strategy ORDER BY n DESC LIMIT 40;
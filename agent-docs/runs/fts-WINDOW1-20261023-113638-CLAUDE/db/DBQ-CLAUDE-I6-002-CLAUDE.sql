SELECT CASE WHEN MINUTE(s.candle_open_time)%5<>4 THEN 'NON_BOUNDARY' WHEN f.generated_at > s.generated_at THEN 'BOUNDARY_5M_LATE' ELSE 'BOUNDARY_5M_VISIBLE' END kind,
       eo.execution_source, COUNT(*) n, SUM(pp.realized_pnl>0) wins, ROUND(SUM(pp.realized_pnl),6) pnl
FROM paper_position pp JOIN trade_signal s ON s.id=pp.signal_id AND s.interval_code='1m'
LEFT JOIN trade_signal f ON f.symbol=s.symbol AND f.interval_code='5m' AND f.candle_open_time = s.candle_open_time - INTERVAL 4 MINUTE
LEFT JOIN execution_opportunity eo ON eo.latest_signal_id=pp.signal_id AND eo.status='EXECUTED'
WHERE pp.closed_at IS NOT NULL GROUP BY kind, eo.execution_source ORDER BY kind, n DESC LIMIT 40;
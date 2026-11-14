SELECT CASE WHEN MINUTE(s.candle_open_time)%5<>4 THEN 'NON_BOUNDARY' ELSE 'BOUNDARY' END kind,
       COALESCE(eo.execution_source,'NULL') src_group,
       COUNT(*) n, SUM(pp.realized_pnl>0) wins, ROUND(AVG(pp.realized_pnl),6) mean_pnl,
       ROUND(STDDEV_SAMP(pp.realized_pnl),6) sd_pnl,
       ROUND(AVG(CASE WHEN pp.realized_pnl>0 THEN pp.realized_pnl END),6) avg_win,
       ROUND(AVG(CASE WHEN pp.realized_pnl<=0 THEN pp.realized_pnl END),6) avg_loss
FROM paper_position pp JOIN trade_signal s ON s.id=pp.signal_id AND s.interval_code='1m'
LEFT JOIN execution_opportunity eo ON eo.latest_signal_id=pp.signal_id AND eo.status='EXECUTED'
WHERE pp.closed_at IS NOT NULL
GROUP BY kind, (COALESCE(eo.execution_source,'NULL')='SETUP_CONFIRMATION_WAKEUP')
ORDER BY kind LIMIT 10;
SELECT COUNT(*) n,
       ROUND(AVG((stop_loss-exit_price)/stop_loss*100),4) avg_slip_pct,
       ROUND(MAX((stop_loss-exit_price)/stop_loss*100),4) max_slip_pct,
       SUM(exit_price < stop_loss) filled_below_stop
FROM paper_position WHERE status='STOPPED' AND stop_loss IS NOT NULL AND exit_price IS NOT NULL;
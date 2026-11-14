SELECT p.id AS pid, i.iv, s.id AS sig_id, s.decision, s.original_decision,
       UNIX_TIMESTAMP(s.candle_open_time) AS candle_epoch, UNIX_TIMESTAMP(s.generated_at) AS gen_epoch,
       TIMESTAMPDIFF(SECOND, s.generated_at, p.closed_at) AS age_at_close_s,
       s.latest_price, s.data_quality_valid, s.signal_mode
FROM paper_position p
CROSS JOIN (SELECT '1m' AS iv UNION ALL SELECT '5m' UNION ALL SELECT '1h') i
LEFT JOIN trade_signal s ON s.id = (
  SELECT s2.id FROM trade_signal s2
  WHERE s2.symbol = p.symbol AND s2.interval_code = i.iv AND s2.generated_at <= p.closed_at
  ORDER BY s2.generated_at DESC LIMIT 1)
WHERE p.id IN (182,172,162,158,143,137)
ORDER BY p.id DESC, i.iv;
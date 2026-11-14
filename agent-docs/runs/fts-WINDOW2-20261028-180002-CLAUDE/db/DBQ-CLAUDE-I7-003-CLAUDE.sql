SELECT p.id, p.symbol, p.close_reason, p.realized_pnl,
       s1.id AS s1_id, s1.decision AS s1_decision, UNIX_TIMESTAMP(s1.generated_at) AS s1_gen,
       s5.id AS s5_id, s5.decision AS s5_decision, UNIX_TIMESTAMP(s5.generated_at) AS s5_gen
FROM paper_position p
JOIN trade_signal s1 ON s1.id = p.signal_id AND s1.interval_code = '1m'
JOIN trade_signal s5 ON s5.symbol = s1.symbol AND s5.interval_code = '5m'
     AND s5.candle_open_time = s1.candle_open_time - INTERVAL 240 SECOND
WHERE p.closed_at >= FROM_UNIXTIME(1790812800)
  AND s5.generated_at > s1.generated_at
ORDER BY p.id DESC
LIMIT 200;
SELECT id, symbol, interval_code, decision, UNIX_TIMESTAMP(candle_open_time) AS candle_open_epoch,
       UNIX_TIMESTAMP(generated_at) AS gen_epoch
FROM trade_signal
WHERE symbol='DOGEUSDT'
  AND generated_at BETWEEN FROM_UNIXTIME(1791765600) AND FROM_UNIXTIME(1791765700)
ORDER BY generated_at
LIMIT 10;
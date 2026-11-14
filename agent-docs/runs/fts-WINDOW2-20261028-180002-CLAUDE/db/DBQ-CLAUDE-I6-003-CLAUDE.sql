SELECT id, symbol, interval_code, decision, latest_price, atr_at_signal, stop_loss, take_profit,
       risk_reward_ratio, UNIX_TIMESTAMP(generated_at) AS gen_epoch
FROM trade_signal
WHERE symbol='EGLDUSDT' AND interval_code='5m'
  AND generated_at BETWEEN FROM_UNIXTIME(1791746700) AND FROM_UNIXTIME(1791747602)
ORDER BY generated_at
LIMIT 10;
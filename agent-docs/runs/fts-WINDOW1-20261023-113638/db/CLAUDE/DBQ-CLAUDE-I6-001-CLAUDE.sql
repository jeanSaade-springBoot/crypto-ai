SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='crypto_ai' AND TABLE_NAME='candle' ORDER BY ORDINAL_POSITION;
SELECT CASE WHEN TIMESTAMPDIFF(MICROSECOND, c.open_time, e.observed_at) < 3000000 THEN 'FIRST_3S' ELSE 'LATER' END pos,
       COUNT(*) n, SUM(e.price > c.high_price OR e.price < c.low_price) outside_range,
       SUM((e.price > c.high_price OR e.price < c.low_price) AND e.price = p.close_price) outside_eq_prev_close
FROM market_price_event e
JOIN candle c ON c.symbol=e.symbol AND c.interval_code='1m' AND c.closed=1 AND e.observed_at >= c.open_time AND e.observed_at <= c.close_time
LEFT JOIN candle p ON p.symbol=c.symbol AND p.interval_code='1m' AND p.open_time = c.open_time - INTERVAL 1 MINUTE
WHERE e.delivery_status='APPLIED' AND e.observed_at >= '2026-10-12 00:00:00' AND e.observed_at < '2026-10-12 12:00:00'
GROUP BY pos LIMIT 10;
SELECT wt.id, wt.symbol, wt.price_usdt, c.low_price, c.high_price FROM wallet_trade wt
JOIN candle c ON c.symbol=wt.symbol AND c.interval_code='1m' AND wt.execution_price_observed_at BETWEEN c.open_time AND c.close_time
WHERE wt.side='BUY' AND wt.executed_at >= '2026-10-06 00:00:00' AND (wt.price_usdt > c.high_price OR wt.price_usdt < c.low_price) ORDER BY wt.id LIMIT 100;
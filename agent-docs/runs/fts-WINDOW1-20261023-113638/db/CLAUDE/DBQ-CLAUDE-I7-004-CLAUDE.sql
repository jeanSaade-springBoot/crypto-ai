SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='crypto_ai' AND TABLE_NAME='market_price_event' ORDER BY ORDINAL_POSITION;
SELECT pp.id, pp.symbol, pp.close_reason, pp.exit_price, c.low_price, c.high_price, p.close_price prev_close, pp.realized_pnl, pp.closed_at
FROM paper_position pp
JOIN candle c ON c.symbol=pp.symbol AND c.interval_code='1m' AND c.closed=1 AND pp.closed_at >= c.open_time AND pp.closed_at <= c.close_time
LEFT JOIN candle p ON p.symbol=c.symbol AND p.interval_code='1m' AND p.open_time = c.open_time - INTERVAL 1 MINUTE
WHERE pp.closed_at IS NOT NULL AND pp.close_reason IN ('STOP_LOSS','PROFIT_LOCK','TAKE_PROFIT')
  AND (pp.exit_price > c.high_price OR pp.exit_price < c.low_price)
ORDER BY pp.id LIMIT 100;
SELECT 'p137' AS w, id, symbol, UNIX_TIMESTAMP(observed_at) AS obs_epoch, price, delivery_status, outcome_reason
FROM market_price_event WHERE symbol='EGLDUSDT' AND observed_at BETWEEN FROM_UNIXTIME(1791741600) AND FROM_UNIXTIME(1791741612.2)
UNION ALL
SELECT 'p158', id, symbol, UNIX_TIMESTAMP(observed_at), price, delivery_status, outcome_reason
FROM market_price_event WHERE symbol='XRPUSDT' AND observed_at BETWEEN FROM_UNIXTIME(1791765000) AND FROM_UNIXTIME(1791765006.7)
UNION ALL
SELECT 'p172', id, symbol, UNIX_TIMESTAMP(observed_at), price, delivery_status, outcome_reason
FROM market_price_event WHERE symbol='SHIBUSDT' AND observed_at BETWEEN FROM_UNIXTIME(1791778440) AND FROM_UNIXTIME(1791778545.5)
UNION ALL
SELECT 'p182', id, symbol, UNIX_TIMESTAMP(observed_at), price, delivery_status, outcome_reason
FROM market_price_event WHERE symbol='ACEUSDT' AND observed_at BETWEEN FROM_UNIXTIME(1791788640) AND FROM_UNIXTIME(1791788720.5)
ORDER BY w, obs_epoch
LIMIT 200;
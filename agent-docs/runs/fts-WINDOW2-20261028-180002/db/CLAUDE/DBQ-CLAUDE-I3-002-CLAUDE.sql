SELECT symbol, COUNT(*) AS n, UNIX_TIMESTAMP(MIN(observed_at)) AS first_epoch, UNIX_TIMESTAMP(MAX(observed_at)) AS last_epoch,
       SUM(observed_at BETWEEN FROM_UNIXTIME(1791747785.1) AND FROM_UNIXTIME(1791747799.0)) AS n_in_gap1,
       SUM(observed_at BETWEEN FROM_UNIXTIME(1791747807.1) AND FROM_UNIXTIME(1791747815.0)) AS n_in_gap2,
       SUM(delivery_status <> 'APPLIED') AS n_not_applied
FROM market_price_event
WHERE observed_at BETWEEN FROM_UNIXTIME(1791747770) AND FROM_UNIXTIME(1791747825)
GROUP BY symbol
ORDER BY symbol
LIMIT 200;
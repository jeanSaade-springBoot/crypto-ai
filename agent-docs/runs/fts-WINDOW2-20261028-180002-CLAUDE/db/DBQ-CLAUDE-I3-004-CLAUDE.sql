SELECT id, UNIX_TIMESTAMP(observed_at) AS obs_epoch, price, delivery_status
FROM market_price_event
WHERE symbol='EGLDUSDT' AND observed_at BETWEEN FROM_UNIXTIME(1791747604) AND FROM_UNIXTIME(1791747680)
ORDER BY observed_at LIMIT 60;
SELECT * FROM wallet_managed_position WHERE id = 172;
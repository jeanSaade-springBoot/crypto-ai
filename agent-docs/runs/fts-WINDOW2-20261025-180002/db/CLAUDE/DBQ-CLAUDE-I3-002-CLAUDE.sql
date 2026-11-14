SELECT symbol, delivery_status, COUNT(*) AS n, MIN(observed_at) AS first_obs, MAX(observed_at) AS last_obs
FROM crypto_ai.market_price_event
WHERE observed_at BETWEEN '2026-10-11 22:43:19' AND '2026-10-11 22:43:36'
GROUP BY symbol, delivery_status
ORDER BY symbol
LIMIT 100;
SELECT pp.id AS paper_position_id, e.delivery_status, e.source, COUNT(*) AS events,
       MIN(e.observed_at) AS first_obs, MAX(e.observed_at) AS last_obs,
       MIN(e.price) AS min_price, MAX(e.price) AS max_price,
       MAX(TIMESTAMPDIFF(MICROSECOND, e.observed_at, e.created_at))/1000000 AS max_ingest_lag_s
FROM crypto_ai.paper_position pp
JOIN crypto_ai.market_price_event e
  ON e.symbol = pp.symbol AND e.observed_at BETWEEN pp.opened_at AND pp.closed_at
WHERE pp.id IN (172,162,158,143,137,131)
GROUP BY pp.id, e.delivery_status, e.source
ORDER BY pp.id, e.delivery_status;
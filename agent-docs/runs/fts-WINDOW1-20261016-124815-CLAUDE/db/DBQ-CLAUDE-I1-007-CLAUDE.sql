SELECT pp.id AS position_id, pp.symbol, mpe.delivery_status, COUNT(*) AS events,
       MIN(mpe.observed_at) AS first_obs, MAX(mpe.observed_at) AS last_obs,
       MIN(mpe.price) AS min_price, MAX(mpe.price) AS max_price
FROM crypto_ai.paper_position pp
JOIN crypto_ai.market_price_event mpe
  ON mpe.symbol = pp.symbol
 AND mpe.observed_at BETWEEN pp.opened_at - INTERVAL 30 SECOND AND pp.closed_at + INTERVAL 30 SECOND
WHERE pp.id IN (182,172,162,158,143,137)
GROUP BY pp.id, pp.symbol, mpe.delivery_status
ORDER BY pp.id DESC, mpe.delivery_status;
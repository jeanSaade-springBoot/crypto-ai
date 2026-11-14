SELECT x.*, (SELECT MIN(m2.observed_at) FROM crypto_ai.market_price_event m2
             WHERE m2.symbol = x.symbol AND m2.price = x.max_price
               AND m2.observed_at BETWEEN x.opened_at AND x.closed_at) AS first_at_max,
       (SELECT MIN(m3.observed_at) FROM crypto_ai.market_price_event m3
             WHERE m3.symbol = x.symbol AND m3.price = x.min_price
               AND m3.observed_at BETWEEN x.opened_at AND x.closed_at) AS first_at_min
FROM (
  SELECT pp.id, pp.symbol, pp.entry_price, pp.stop_loss, pp.take_profit, pp.opened_at, pp.closed_at,
         COUNT(*) AS events, COUNT(DISTINCT mpe.price) AS distinct_prices,
         SUM(mpe.delivery_status <> 'APPLIED') AS non_applied,
         MIN(mpe.price) AS min_price, MAX(mpe.price) AS max_price,
         MIN(mpe.observed_at) AS first_obs, MAX(mpe.observed_at) AS last_obs
  FROM crypto_ai.paper_position pp
  JOIN crypto_ai.market_price_event mpe
    ON mpe.symbol = pp.symbol AND mpe.observed_at BETWEEN pp.opened_at AND pp.closed_at
  WHERE pp.id IN (182,172,162,158,143,137)
  GROUP BY pp.id, pp.symbol, pp.entry_price, pp.stop_loss, pp.take_profit, pp.opened_at, pp.closed_at
) x
ORDER BY x.id DESC;
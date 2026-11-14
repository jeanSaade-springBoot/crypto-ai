SELECT p.id AS position_id, p.symbol, COUNT(*) AS events,
       MIN(e.price) AS min_price, MAX(e.price) AS max_price,
       MIN(e.observed_at) AS first_obs, MAX(e.observed_at) AS last_obs,
       (MAX(e.price) - p.entry_price) / p.entry_price * 100 AS mfe_pct,
       (MIN(e.price) - p.entry_price) / p.entry_price * 100 AS mae_pct
FROM paper_position p
JOIN market_price_event e
  ON e.symbol = p.symbol
 AND e.observed_at BETWEEN p.opened_at AND p.closed_at
WHERE p.id IN (182,172,162,158,143,137)
GROUP BY p.id, p.symbol, p.entry_price
ORDER BY p.id DESC;
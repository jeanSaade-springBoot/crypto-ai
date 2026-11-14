SELECT pp.id AS pp_id, pp.symbol, m.delivery_status, COUNT(*) AS n, MIN(m.price) AS min_px, MAX(m.price) AS max_px,
       MIN(m.observed_at) AS first_obs, MAX(m.observed_at) AS last_obs,
       MIN(CASE WHEN m.price <= pp.stop_loss THEN m.observed_at END) AS first_le_final_sl,
       MIN(CASE WHEN m.price >= pp.take_profit THEN m.observed_at END) AS first_ge_final_tp
FROM paper_position pp
JOIN market_price_event m ON m.symbol = pp.symbol
 AND m.observed_at BETWEEN pp.opened_at AND pp.closed_at + INTERVAL 5 SECOND
WHERE pp.id IN (182,172,162,158,143,137)
GROUP BY pp.id, pp.symbol, m.delivery_status ORDER BY pp.id DESC, m.delivery_status;
SELECT pp.id AS pp_id, CASE WHEN m.observed_at <= pp.opened_at + INTERVAL 2 SECOND
                             AND m.observed_at >= pp.opened_at - INTERVAL 20 SECOND THEN 'ENTRY' ELSE 'EXIT' END AS edge,
       m.id, m.observed_at, m.price, m.source, m.delivery_status, m.created_at
FROM paper_position pp
JOIN market_price_event m ON m.symbol = pp.symbol
 AND (m.observed_at BETWEEN pp.opened_at - INTERVAL 20 SECOND AND pp.opened_at + INTERVAL 2 SECOND
   OR m.observed_at BETWEEN pp.closed_at - INTERVAL 20 SECOND AND pp.closed_at + INTERVAL 2 SECOND)
WHERE pp.id IN (182,172,162,158,143,137)
ORDER BY pp.id DESC, m.observed_at, m.id LIMIT 300;
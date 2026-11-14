SELECT pp.id AS paper_position_id, pp.stop_loss, pp.closed_at,
       (SELECT CONCAT(e.id,'|',e.observed_at,'|',e.price,'|',e.delivery_status)
          FROM crypto_ai.market_price_event e
         WHERE e.symbol = pp.symbol
           AND e.observed_at BETWEEN pp.opened_at AND pp.closed_at
           AND e.delivery_status IN ('LEGACY','APPLIED')
           AND e.price <= pp.stop_loss
         ORDER BY e.observed_at, e.id LIMIT 1) AS first_sl_breach
FROM crypto_ai.paper_position pp
WHERE pp.id IN (172,162,158,143,137,131)
ORDER BY pp.id;
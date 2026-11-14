SELECT pp.id, pp.symbol, pp.entry_price, pp.opened_at,
  (SELECT MIN(e.price) FROM crypto_ai.market_price_event e WHERE e.symbol=pp.symbol AND e.observed_at BETWEEN pp.opened_at - INTERVAL 120 SECOND AND pp.opened_at) AS pre120_min,
  (SELECT MAX(e.price) FROM crypto_ai.market_price_event e WHERE e.symbol=pp.symbol AND e.observed_at BETWEEN pp.opened_at - INTERVAL 120 SECOND AND pp.opened_at) AS pre120_max,
  (SELECT MAX(e.price) FROM crypto_ai.market_price_event e WHERE e.symbol=pp.symbol AND e.observed_at BETWEEN pp.opened_at AND pp.opened_at + INTERVAL 300 SECOND) AS post300_max,
  (SELECT MIN(e.price) FROM crypto_ai.market_price_event e WHERE e.symbol=pp.symbol AND e.observed_at BETWEEN pp.opened_at AND pp.opened_at + INTERVAL 300 SECOND) AS post300_min,
  (SELECT COUNT(*) FROM crypto_ai.market_price_event e WHERE e.symbol=pp.symbol AND e.observed_at BETWEEN pp.opened_at - INTERVAL 120 SECOND AND pp.opened_at + INTERVAL 300 SECOND) AS ticks
FROM crypto_ai.paper_position pp
WHERE pp.id IN (182,172,162,158,143,137)
ORDER BY pp.id;
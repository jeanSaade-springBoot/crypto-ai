DESCRIBE crypto_ai.market_price_event;
DESCRIBE crypto_ai.candle;
SELECT id, symbol, observed_at, price, source, delivery_status, ordering_slot, outcome_reason, created_at FROM crypto_ai.market_price_event WHERE symbol = 'EGLDUSDT' AND observed_at BETWEEN '2026-10-11 22:39:00' AND '2026-10-11 22:44:00' ORDER BY observed_at, id LIMIT 400
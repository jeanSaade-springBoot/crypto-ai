SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='crypto_ai' AND TABLE_NAME='wallet_trade' ORDER BY ORDINAL_POSITION;
SELECT * FROM wallet_trade WHERE id IN (451,426,403,392,358,344) ORDER BY id LIMIT 6;
SELECT symbol, id, observed_at, price, delivery_status, outcome_reason, created_at FROM market_price_event
WHERE (symbol='ACEUSDT'  AND observed_at BETWEEN '2026-10-12 09:56:50' AND '2026-10-12 09:57:07')
   OR (symbol='SHIBUSDT' AND observed_at BETWEEN '2026-10-12 06:35:50' AND '2026-10-12 06:36:06')
   OR (symbol='DOGEUSDT' AND observed_at BETWEEN '2026-10-12 04:01:52' AND '2026-10-12 04:02:07')
   OR (symbol='XRPUSDT'  AND observed_at BETWEEN '2026-10-12 02:34:52' AND '2026-10-12 02:35:06')
   OR (symbol='EGLDUSDT' AND observed_at BETWEEN '2026-10-11 22:39:52' AND '2026-10-11 22:40:06')
   OR (symbol='EGLDUSDT' AND observed_at BETWEEN '2026-10-11 20:54:52' AND '2026-10-11 20:55:06')
ORDER BY symbol, observed_at LIMIT 150;
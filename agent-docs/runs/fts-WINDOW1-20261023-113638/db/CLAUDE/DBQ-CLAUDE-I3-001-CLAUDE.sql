SELECT symbol, id, observed_at, price, delivery_status, outcome_reason, created_at
FROM market_price_event
WHERE (symbol='ACEUSDT'  AND observed_at BETWEEN '2026-10-12 10:04:55' AND '2026-10-12 10:05:20')
   OR (symbol='SHIBUSDT' AND observed_at BETWEEN '2026-10-12 07:14:58' AND '2026-10-12 07:15:45')
   OR (symbol='EGLDUSDT' AND observed_at BETWEEN '2026-10-11 20:59:58' AND '2026-10-11 21:00:13')
   OR (symbol='XRPUSDT'  AND observed_at BETWEEN '2026-10-12 03:29:58' AND '2026-10-12 03:30:07')
ORDER BY symbol, observed_at LIMIT 200;
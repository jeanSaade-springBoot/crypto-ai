SELECT 'TICK' kind, symbol, id, observed_at, created_at, price, delivery_status, outcome_reason
FROM crypto_ai.market_price_event
WHERE (symbol='ACEUSDT'  AND observed_at BETWEEN '2026-10-12 10:04:50' AND '2026-10-12 10:05:21')
   OR (symbol='SHIBUSDT' AND observed_at BETWEEN '2026-10-12 07:14:50' AND '2026-10-12 07:15:46')
   OR (symbol='XRPUSDT'  AND observed_at BETWEEN '2026-10-12 03:30:00' AND '2026-10-12 03:30:07')
   OR (symbol='EGLDUSDT' AND observed_at BETWEEN '2026-10-11 20:59:58' AND '2026-10-11 21:00:13')
UNION ALL
SELECT 'SIG_1H', symbol, id, generated_at, created_at, latest_price, decision, original_decision
FROM crypto_ai.trade_signal
WHERE symbol='EGLDUSDT' AND interval_code='1h' AND generated_at BETWEEN '2026-10-11 19:00:00' AND '2026-10-11 21:00:05'
ORDER BY 2, 4
LIMIT 300;
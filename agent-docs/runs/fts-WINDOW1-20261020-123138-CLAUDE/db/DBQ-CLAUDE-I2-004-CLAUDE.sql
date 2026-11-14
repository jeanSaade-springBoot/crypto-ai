SELECT interval_code, MAX(open_time) AS last_open_ksa_display, MAX(updated_at) AS last_update FROM crypto_ai.candle GROUP BY interval_code;
SELECT MAX(observed_at) AS last_price_event, COUNT(*) AS events_last_24h FROM crypto_ai.market_price_event WHERE observed_at >= NOW() - INTERVAL 1 DAY;
SELECT status, COUNT(*) AS n, MAX(updated_at) AS last_updated FROM crypto_ai.signal_processing_work WHERE updated_at >= '2026-10-16 00:00:00' GROUP BY status;
SELECT id, symbol, status, realized_pnl, close_reason, opened_at, closed_at FROM crypto_ai.paper_position WHERE id > 182 ORDER BY id LIMIT 15;
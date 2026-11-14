SHOW INDEX FROM crypto_ai.candle;
SELECT COUNT(*) AS asset_rows, SUM(quantity > 0) AS nonzero_assets FROM crypto_ai.wallet_asset;
EXPLAIN SELECT * FROM crypto_ai.candle WHERE symbol = 'EGLDUSDT' AND interval_code = '1m' AND closed = 1 ORDER BY close_time DESC LIMIT 1;
SELECT interval_code, COUNT(*) AS n FROM crypto_ai.trade_signal WHERE generated_at BETWEEN '2026-10-11 00:00:00' AND '2026-10-13 00:00:00' GROUP BY interval_code;
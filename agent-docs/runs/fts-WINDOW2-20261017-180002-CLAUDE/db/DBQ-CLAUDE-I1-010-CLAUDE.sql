SELECT MAX(executed_at) AS last_fill, COUNT(*) AS fills FROM crypto_ai.wallet_trade;
SELECT interval_code, MAX(generated_at) AS last_signal, SUM(generated_at >= '2026-10-15 03:15:00') AS signals_after_last_fill FROM crypto_ai.trade_signal WHERE interval_code IN ('1m','5m','1h') GROUP BY interval_code;
SELECT MAX(observed_at) AS last_price_event FROM crypto_ai.market_price_event;
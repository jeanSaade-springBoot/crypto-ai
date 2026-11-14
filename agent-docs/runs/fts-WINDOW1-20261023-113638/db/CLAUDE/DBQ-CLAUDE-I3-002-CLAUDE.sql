SELECT id, symbol, interval_code, candle_open_time, generated_at, created_at, decision
FROM trade_signal WHERE id IN (176927,176934,172587,172588,172201,166819,166821,168251,168277,156850) ORDER BY id;
SELECT id, interval_code, candle_open_time, generated_at, created_at, decision
FROM trade_signal WHERE symbol='EGLDUSDT' AND interval_code IN ('5m','1h')
 AND generated_at BETWEEN '2026-10-11 19:55:00' AND '2026-10-11 21:00:09' ORDER BY interval_code, generated_at DESC LIMIT 20;
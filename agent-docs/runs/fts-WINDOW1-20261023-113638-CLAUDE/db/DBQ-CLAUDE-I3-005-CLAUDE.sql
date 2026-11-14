SELECT DATE(generated_at) d, COUNT(*) signals, SUM(decision IN ('BUY','STRONG_BUY')) buys, MAX(generated_at) last_gen
FROM trade_signal WHERE generated_at >= '2026-10-12 00:00:00' GROUP BY d ORDER BY d LIMIT 20;
SELECT DATE(open_time) d, COUNT(*) candles_1m, MAX(open_time) last_open
FROM candle WHERE interval_code='1m' AND open_time >= '2026-10-12 00:00:00' GROUP BY d ORDER BY d LIMIT 20;
SELECT status, decision_code, COUNT(*) n, MAX(last_evidence_at) last_seen
FROM execution_opportunity WHERE started_at >= '2026-10-15 00:00:00' GROUP BY status, decision_code ORDER BY n DESC LIMIT 30;
SELECT id, interval_code, generated_at, decision, original_decision, total_score, confidence_score, final_entry_allowed, latest_price
FROM trade_signal WHERE symbol='EGLDUSDT' AND interval_code='1m'
 AND generated_at BETWEEN '2026-10-11 22:10:00' AND '2026-10-11 22:40:02' ORDER BY generated_at DESC LIMIT 35;
SELECT id, interval_code, generated_at, decision, final_entry_allowed, atr_recommended_position_percent
FROM trade_signal WHERE symbol='EGLDUSDT' AND interval_code IN ('5m','1h')
 AND generated_at BETWEEN '2026-10-11 21:35:00' AND '2026-10-11 22:40:02' ORDER BY interval_code, generated_at DESC LIMIT 10;
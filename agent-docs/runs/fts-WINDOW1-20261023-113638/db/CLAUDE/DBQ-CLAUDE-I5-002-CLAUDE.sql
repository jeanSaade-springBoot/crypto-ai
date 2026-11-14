SELECT id, interval_code, candle_open_time, generated_at, created_at, decision, final_entry_allowed, atr_entry_type, atr_recommended_position_percent FROM trade_signal WHERE id IN (159407,159408,159285,156721,156723) ORDER BY id;
SELECT COUNT(*) n, SUM(f.generated_at > s.generated_at) five_after_one, SUM(f.generated_at <= s.generated_at) five_before_one
FROM trade_signal s JOIN trade_signal f ON f.symbol=s.symbol AND f.interval_code='5m' AND f.candle_open_time = s.candle_open_time - INTERVAL 4 MINUTE
WHERE s.interval_code='1m' AND MINUTE(s.candle_open_time) % 5 = 4
  AND s.generated_at >= '2026-10-11 00:00:00' AND s.generated_at < '2026-10-13 00:00:00';
SELECT pp.id, pp.symbol, pp.realized_pnl, s.generated_at s1m, f.id f5m_id, f.generated_at f5m_gen, f.decision f5m_decision
FROM paper_position pp JOIN trade_signal s ON s.id=pp.signal_id AND s.interval_code='1m' AND MINUTE(s.candle_open_time) % 5 = 4
LEFT JOIN trade_signal f ON f.symbol=s.symbol AND f.interval_code='5m' AND f.candle_open_time = s.candle_open_time - INTERVAL 4 MINUTE
WHERE pp.closed_at IS NOT NULL ORDER BY pp.id LIMIT 60;
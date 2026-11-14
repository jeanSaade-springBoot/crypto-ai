SELECT p.id AS paper_id, p.symbol, s1.id AS one_m_id, s1.decision AS one_m_decision, s1.atr_immediate_entry_allowed AS one_m_atr_immediate, s1.generated_at AS one_m_generated,
       s5.id AS five_m_same_boundary_id, s5.decision AS five_m_decision, s5.generated_at AS five_m_generated,
       TIMESTAMPDIFF(MICROSECOND, s1.generated_at, s5.generated_at) AS five_minus_one_us
FROM paper_position p
JOIN trade_signal s1 ON s1.id = p.signal_id
JOIN trade_signal s5
  ON s5.symbol = s1.symbol
 AND s5.interval_code = '5m'
 AND s5.candle_open_time + INTERVAL 5 MINUTE = s1.candle_open_time + INTERVAL 1 MINUTE
WHERE p.opened_at >= '2026-10-11 00:00:00'
ORDER BY p.id
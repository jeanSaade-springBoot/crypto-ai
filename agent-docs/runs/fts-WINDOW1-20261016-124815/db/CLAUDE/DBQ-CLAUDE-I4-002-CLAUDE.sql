SELECT s1.id AS anchor_id, s1.interval_code AS anchor_iv, s1.generated_at AS anchor_gen, ivs.iv,
       h.id AS ctx_id, h.generated_at AS ctx_gen, h.decision, h.original_decision, h.trend_score,
       h.market_regime, h.final_entry_allowed
FROM crypto_ai.trade_signal s1
JOIN (SELECT '15m' AS iv UNION ALL SELECT '1h') ivs
JOIN crypto_ai.trade_signal h ON h.id = (
     SELECT h2.id FROM crypto_ai.trade_signal h2
     WHERE h2.symbol = s1.symbol AND h2.interval_code = ivs.iv AND h2.generated_at <= s1.generated_at
     ORDER BY h2.generated_at DESC, h2.id DESC LIMIT 1)
WHERE s1.id IN (176655,171559,167616,165415,159285,156721,176710,171589,167668,165412,159408,156723)
ORDER BY s1.id, ivs.iv;
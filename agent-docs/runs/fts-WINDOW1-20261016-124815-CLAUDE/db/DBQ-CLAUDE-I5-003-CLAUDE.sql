SELECT id, symbol, generated_at, decision, original_decision, final_entry_allowed, primary_blocking_stage,
       latest_price, atr_at_signal, atr_percent, risk_reward_ratio, atr_entry_type, atr_immediate_entry_allowed,
       liquidity_status, market_regime, selected_strategy, entry_authority
FROM crypto_ai.trade_signal
WHERE interval_code = '1m' AND (
      (symbol='ACEUSDT'  AND generated_at BETWEEN '2026-10-12 09:26:00' AND '2026-10-12 09:58:00')
   OR (symbol='SHIBUSDT' AND generated_at BETWEEN '2026-10-12 06:05:00' AND '2026-10-12 06:37:00')
   OR (symbol='DOGEUSDT' AND generated_at BETWEEN '2026-10-12 03:31:00' AND '2026-10-12 04:03:00')
   OR (symbol='XRPUSDT'  AND generated_at BETWEEN '2026-10-12 02:04:00' AND '2026-10-12 02:36:00')
   OR (symbol='EGLDUSDT' AND generated_at BETWEEN '2026-10-11 22:09:00' AND '2026-10-11 22:41:00')
   OR (symbol='EGLDUSDT' AND generated_at BETWEEN '2026-10-11 20:24:00' AND '2026-10-11 20:56:00'))
ORDER BY symbol, generated_at
LIMIT 250;
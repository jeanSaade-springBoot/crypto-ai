SELECT id, symbol, interval_code, generated_at, decision, latest_price, stop_loss, take_profit, atr_at_signal, risk_reward_ratio, market_regime, liquidity_status, btc_context_status
FROM trade_signal
WHERE id IN (156723,159285,159407,159408,165412,165415,167646,167668,171559,171589,176692,176710)
ORDER BY id
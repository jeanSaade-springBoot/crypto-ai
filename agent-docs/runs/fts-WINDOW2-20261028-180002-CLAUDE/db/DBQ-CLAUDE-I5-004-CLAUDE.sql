SELECT id, symbol, decision, total_score, confidence_score, rsi_score, bollinger_score,
       market_regime, liquidity_status, btc_context_status, LEFT(explanation, 600) AS expl
FROM trade_signal
WHERE id IN (156723,159408,165412,167668,171589,176710)
ORDER BY id;
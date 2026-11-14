SELECT id, LEFT(CAST(decision_path AS CHAR), 3000) AS decision_path_head, LEFT(final_decision_explanation, 1500) AS final_expl, LEFT(entry_authority_explanation, 1000) AS authority_expl, LEFT(atr_explanation, 600) AS atr_expl, LEFT(CAST(sentiment_breakdown AS CHAR), 1000) AS sentiment_head
FROM crypto_ai.trade_signal
WHERE id IN (176710,171589,167668,165412,159408,156723)
ORDER BY id DESC
LIMIT 6;
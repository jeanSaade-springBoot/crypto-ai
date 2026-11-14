SELECT *
FROM crypto_ai.buy_htf_assessment_audit
WHERE reference_signal_id IN (176692,176710,171462,171484,171589,167646,167668,165389,165412,165415,159281,159408,156723)
ORDER BY reference_signal_id, evaluated_at
LIMIT 100;
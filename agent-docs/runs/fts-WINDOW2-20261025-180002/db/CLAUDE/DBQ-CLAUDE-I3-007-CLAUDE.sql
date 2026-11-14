SELECT symbol, interval_code, candle_open_time, outcome, reason_code, signal_id, origin, created_at
FROM crypto_ai.candle_analysis_outcome
WHERE signal_id IN (176710,171589,167668,165412,159408,156723,176927,172587,168277,166819,159487,156850)
ORDER BY candle_open_time
LIMIT 30;
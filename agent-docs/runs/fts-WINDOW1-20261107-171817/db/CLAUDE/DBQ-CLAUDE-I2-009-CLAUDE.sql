SELECT signal_id, symbol, interval_code, candle_open_time, origin, status, attempts,
       updated_at, completed_at, paper_position_id, failure_stage, LEFT(error_message, 300) AS err
FROM signal_processing_work
WHERE signal_id IN (176710,171589,167668,168277,165412,159408,156723);

SELECT id, symbol, interval_code, candle_open_time, outcome, reason_code, signal_id, origin, created_at
FROM candle_analysis_outcome
WHERE signal_id IN (176710,171589,167668,168277,165412,159408,156723);

SELECT status, COUNT(*) AS n, MAX(opened_at) AS last_open, MAX(closed_at) AS last_close
FROM paper_position
WHERE opened_at >= '2026-10-12 10:05:21'
GROUP BY status;
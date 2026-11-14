SELECT signal_id, symbol, interval_code, candle_open_time, origin, status, attempts, updated_at, completed_at, paper_position_id, failure_stage, LEFT(error_message,300) err
FROM signal_processing_work WHERE signal_id IN (165415,165412);
SELECT id, symbol, interval_code, candle_open_time, generated_at, created_at, decision, total_score, confidence_score, final_entry_allowed
FROM trade_signal WHERE id = 165415;
SELECT id, symbol, status, started_at, last_evidence_at, latest_signal_id, evidence_count, buy_count, watch_count, evidence_score,
       five_minute_decision, one_hour_decision, execution_source, recommended_position_percent, decision_code, executed_at
FROM execution_opportunity
WHERE latest_signal_id IN (176710,171589,167668,165412,159408,156723) ORDER BY id LIMIT 20;
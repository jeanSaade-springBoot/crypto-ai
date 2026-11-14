SELECT id, reference_signal_id, evaluation_path, evaluated_at, replay_mode, one_hour_signal_id, one_hour_raw_decision, one_hour_effective_decision, one_hour_data_quality_valid, five_minute_signal_id, five_minute_raw_decision, five_minute_effective_decision, five_minute_data_quality_valid, assessment_band, max_initial_percent, legacy_veto_result, final_result, LEFT(final_reason, 300) AS final_reason
FROM crypto_ai.buy_htf_assessment_audit
WHERE reference_signal_id IN (176710, 171589, 167668, 165412, 159408, 156723)
ORDER BY reference_signal_id, evaluated_at
LIMIT 60;
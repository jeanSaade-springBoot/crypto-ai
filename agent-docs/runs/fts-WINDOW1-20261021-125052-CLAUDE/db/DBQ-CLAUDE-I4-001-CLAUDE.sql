SELECT id, reference_signal_id, evaluation_path, evaluated_at, UNIX_TIMESTAMP(evaluated_at) AS eval_epoch, one_hour_signal_id, one_hour_raw_decision, one_hour_effective_decision, one_hour_age_seconds, one_hour_data_quality_valid, five_minute_signal_id, five_minute_raw_decision, five_minute_effective_decision, five_minute_age_seconds, five_minute_data_quality_valid, assessment_band, hard_confirmation_present, max_initial_percent, legacy_veto_result, final_result, LEFT(final_reason,400) AS reason, created_at
FROM buy_htf_assessment_audit
WHERE reference_signal_id IN (156723,159408,165412,165415,167646,167668,171589,176692,176710,176655)
ORDER BY reference_signal_id, id
LIMIT 100
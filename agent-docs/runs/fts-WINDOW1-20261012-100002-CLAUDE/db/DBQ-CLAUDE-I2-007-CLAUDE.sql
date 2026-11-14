SELECT id, reference_signal_id, evaluation_path, evaluated_at, replay_mode, one_hour_signal_id, one_hour_effective_decision, one_hour_age_seconds, one_hour_data_quality_valid,
       one_hour_usability_reason, five_minute_signal_id, five_minute_effective_decision, five_minute_age_seconds, five_minute_data_quality_valid, assessment_band,
       max_initial_percent, legacy_veto_result, final_result, LEFT(final_reason, 600) AS final_reason_600, hard_confirmation_present
FROM crypto_ai.buy_htf_assessment_audit
WHERE reference_signal_id IN (155629,155652,156071,156723,159408,165412,167668,171589) ORDER BY reference_signal_id, evaluated_at;
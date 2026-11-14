-- FIX-154: immutable quality evidence belongs to each generated signal.
-- NULL is intentional for signals generated before this migration.
ALTER TABLE trade_signal
    ADD COLUMN data_quality_valid BOOLEAN NULL;

-- Execution-time evidence is append-only: the same reference signal can be
-- assessed repeatedly through different or repeated authority paths.
CREATE TABLE buy_htf_assessment_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reference_signal_id BIGINT NOT NULL,
    evaluation_path VARCHAR(40) NOT NULL,
    evaluated_at TIMESTAMP(6) NOT NULL,
    replay_mode BOOLEAN NOT NULL DEFAULT FALSE,
    one_hour_signal_id BIGINT NULL,
    one_hour_raw_decision VARCHAR(30) NULL,
    one_hour_effective_decision VARCHAR(30) NULL,
    one_hour_candle_close_time TIMESTAMP(6) NULL,
    one_hour_age_seconds BIGINT NULL,
    one_hour_data_quality_valid BOOLEAN NULL,
    one_hour_usability_reason VARCHAR(96) NULL,
    five_minute_signal_id BIGINT NULL,
    five_minute_raw_decision VARCHAR(30) NULL,
    five_minute_effective_decision VARCHAR(30) NULL,
    five_minute_candle_close_time TIMESTAMP(6) NULL,
    five_minute_age_seconds BIGINT NULL,
    five_minute_data_quality_valid BOOLEAN NULL,
    five_minute_usability_reason VARCHAR(96) NULL,
    confidence_threshold INT NOT NULL,
    assessment_band VARCHAR(40) NOT NULL,
    max_initial_percent INT NULL,
    legacy_veto_result VARCHAR(64) NULL,
    final_result VARCHAR(96) NOT NULL,
    final_reason VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_fix154_audit_reference (reference_signal_id, evaluated_at),
    KEY idx_fix154_audit_path_time (evaluation_path, evaluated_at),
    KEY idx_fix154_audit_band_time (assessment_band, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

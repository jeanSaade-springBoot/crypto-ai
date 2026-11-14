-- FIX-154 forward-only completion. V107 was already applied on the trader
-- database; never edit it. Replay needs the mapped signal fields and the
-- immutable audit needs its hard-confirmation marker.
ALTER TABLE trade_signal_test
    ADD COLUMN btc_context_candle_close_time TIMESTAMP(6) NULL,
    ADD COLUMN btc_context_age_seconds BIGINT NULL,
    ADD COLUMN btc_context_freshness_threshold_seconds BIGINT NULL,
    ADD COLUMN signal_mode VARCHAR(24) NULL,
    ADD COLUMN data_quality_valid BOOLEAN NULL;

ALTER TABLE buy_htf_assessment_audit
    ADD COLUMN hard_confirmation_present BOOLEAN NOT NULL DEFAULT FALSE;

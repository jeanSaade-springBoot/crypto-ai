-- FIX-11J: Replay-only persisted performance diagnostics.
-- Nullable BIGINT nanosecond fields preserve old runs as "timing unavailable" and do not
-- modify any Production trading table, Replay decision data, thresholds, ordering or lineage.
ALTER TABLE analysis_test_run
    ADD COLUMN timing_load_historical_ns BIGINT NULL,
    ADD COLUMN timing_verify_event_resolution_ns BIGINT NULL,
    ADD COLUMN timing_build_replay_dataset_ns BIGINT NULL,
    ADD COLUMN timing_generate_fresh_signals_ns BIGINT NULL,
    ADD COLUMN timing_shadow_execution_ns BIGINT NULL,
    ADD COLUMN timing_parity_comparison_ns BIGINT NULL,
    ADD COLUMN timing_total_ns BIGINT NULL;

-- analysis_test_run_archive was created from the earlier run schema in V57, while V71/V73
-- later added live-run metadata. Keep the archive shape aligned with current analysis_test_run
-- because RegressionTestService intentionally archives with INSERT ... SELECT ?, r.*.
ALTER TABLE analysis_test_run_archive
    ADD COLUMN heartbeat_at TIMESTAMP(6) NULL AFTER current_step,
    ADD COLUMN replay_price_mode VARCHAR(40) NULL,
    ADD COLUMN replay_logic_mode VARCHAR(40) NULL,
    ADD COLUMN timing_load_historical_ns BIGINT NULL,
    ADD COLUMN timing_verify_event_resolution_ns BIGINT NULL,
    ADD COLUMN timing_build_replay_dataset_ns BIGINT NULL,
    ADD COLUMN timing_generate_fresh_signals_ns BIGINT NULL,
    ADD COLUMN timing_shadow_execution_ns BIGINT NULL,
    ADD COLUMN timing_parity_comparison_ns BIGINT NULL,
    ADD COLUMN timing_total_ns BIGINT NULL;

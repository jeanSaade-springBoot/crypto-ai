ALTER TABLE analysis_test_signal
    ADD COLUMN replay_generated TINYINT(1) NOT NULL DEFAULT 0 AFTER source_signal_id,
    ADD COLUMN generation_error VARCHAR(1000) NULL AFTER decision_authority_corrected;

ALTER TABLE analysis_test_run
    ADD COLUMN generated_signal_count INT NOT NULL DEFAULT 0 AFTER replay_signal_count,
    ADD COLUMN generated_buy_count INT NOT NULL DEFAULT 0 AFTER generated_signal_count,
    ADD COLUMN generated_watch_count INT NOT NULL DEFAULT 0 AFTER generated_buy_count,
    ADD COLUMN generated_sell_count INT NOT NULL DEFAULT 0 AFTER generated_watch_count,
    ADD COLUMN generated_strong_sell_count INT NOT NULL DEFAULT 0 AFTER generated_sell_count;

ALTER TABLE analysis_test_result
    ADD COLUMN generated_signals_1m INT NOT NULL DEFAULT 0 AFTER replayable_1m_events,
    ADD COLUMN generated_buys_1m INT NOT NULL DEFAULT 0 AFTER generated_signals_1m,
    ADD COLUMN generated_signals_5m INT NOT NULL DEFAULT 0 AFTER replayable_5m_events,
    ADD COLUMN generated_buys_5m INT NOT NULL DEFAULT 0 AFTER generated_signals_5m,
    ADD COLUMN generated_signals_1h INT NOT NULL DEFAULT 0 AFTER replayable_1h_events,
    ADD COLUMN generated_buys_1h INT NOT NULL DEFAULT 0 AFTER generated_signals_1h,
    ADD COLUMN generated_signal_errors INT NOT NULL DEFAULT 0 AFTER generated_buys_1h;

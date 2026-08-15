CREATE TABLE regression_test_archive_batch (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_test_run_id BIGINT NOT NULL,
    test_name VARCHAR(150) NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    start_time TIMESTAMP(6) NOT NULL,
    end_time TIMESTAMP(6) NOT NULL,
    source_status VARCHAR(30) NOT NULL,
    archive_reason VARCHAR(255) NULL,
    archived_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_regression_archive_source_run (source_test_run_id),
    INDEX idx_regression_archive_symbol_time (symbol, start_time, end_time)
);

CREATE TABLE analysis_test_run_archive LIKE analysis_test_run;
ALTER TABLE analysis_test_run_archive MODIFY id BIGINT NOT NULL, DROP PRIMARY KEY,
    ADD COLUMN archive_batch_id BIGINT NOT NULL FIRST,
    ADD PRIMARY KEY (archive_batch_id, id), ADD INDEX idx_analysis_test_run_archive_source (id);

CREATE TABLE analysis_test_signal_archive LIKE analysis_test_signal;
ALTER TABLE analysis_test_signal_archive MODIFY id BIGINT NOT NULL, DROP PRIMARY KEY,
    ADD COLUMN archive_batch_id BIGINT NOT NULL FIRST,
    ADD PRIMARY KEY (archive_batch_id, id), ADD INDEX idx_analysis_test_signal_archive_run (archive_batch_id, test_run_id, generated_at);

CREATE TABLE execution_opportunity_test_archive LIKE execution_opportunity_test;
ALTER TABLE execution_opportunity_test_archive MODIFY id BIGINT NOT NULL, DROP PRIMARY KEY,
    ADD COLUMN archive_batch_id BIGINT NOT NULL FIRST,
    ADD PRIMARY KEY (archive_batch_id, id), ADD INDEX idx_execution_opportunity_archive_run (archive_batch_id, test_run_id, generated_at);

CREATE TABLE analysis_test_result_archive LIKE analysis_test_result;
ALTER TABLE analysis_test_result_archive MODIFY id BIGINT NOT NULL, DROP PRIMARY KEY,
    DROP INDEX uk_analysis_test_result_run,
    ADD COLUMN archive_batch_id BIGINT NOT NULL FIRST,
    ADD PRIMARY KEY (archive_batch_id, id), ADD INDEX idx_analysis_test_result_archive_run (archive_batch_id, test_run_id);

CREATE TABLE wallet_execution_test_archive LIKE wallet_execution_test;
ALTER TABLE wallet_execution_test_archive MODIFY id BIGINT NOT NULL, DROP PRIMARY KEY,
    ADD COLUMN archive_batch_id BIGINT NOT NULL FIRST,
    ADD PRIMARY KEY (archive_batch_id, id), ADD INDEX idx_wallet_execution_archive_run (archive_batch_id, test_run_id, execution_time);

CREATE TABLE wallet_position_test_archive LIKE wallet_position_test;
ALTER TABLE wallet_position_test_archive MODIFY id BIGINT NOT NULL, DROP PRIMARY KEY,
    ADD COLUMN archive_batch_id BIGINT NOT NULL FIRST,
    ADD PRIMARY KEY (archive_batch_id, id), ADD INDEX idx_wallet_position_archive_run (archive_batch_id, test_run_id, entry_time);

CREATE TABLE position_management_test_archive LIKE position_management_test;
ALTER TABLE position_management_test_archive MODIFY id BIGINT NOT NULL, DROP PRIMARY KEY,
    ADD COLUMN archive_batch_id BIGINT NOT NULL FIRST,
    ADD PRIMARY KEY (archive_batch_id, id), ADD INDEX idx_position_management_archive_run (archive_batch_id, test_run_id, generated_at);

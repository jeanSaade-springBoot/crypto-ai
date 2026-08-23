-- FIX-065: persistent Investigation Queue for repeatable replay cases.
-- Stores only replay/test metadata. It never points at or mutates production wallet state.
CREATE TABLE IF NOT EXISTS regression_investigation_case (
    id BIGINT NOT NULL AUTO_INCREMENT,
    case_name VARCHAR(150) NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    start_time TIMESTAMP(6) NOT NULL,
    end_time TIMESTAMP(6) NOT NULL,
    wallet_id BIGINT NULL,
    expected_action VARCHAR(40) NULL,
    notes VARCHAR(1000) NULL,
    last_run_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_regression_investigation_symbol_time (symbol, start_time),
    KEY idx_regression_investigation_last_run (last_run_id),
    CONSTRAINT fk_regression_investigation_last_run
        FOREIGN KEY (last_run_id) REFERENCES analysis_test_run(id) ON DELETE SET NULL
);

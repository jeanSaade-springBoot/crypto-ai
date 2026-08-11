CREATE TABLE position_management_test (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    test_run_id BIGINT NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    generated_at TIMESTAMP(6) NOT NULL,
    action_code VARCHAR(60) NOT NULL,
    current_price DECIMAL(30,12) NULL,
    old_take_profit DECIMAL(30,12) NULL,
    new_take_profit DECIMAL(30,12) NULL,
    highest_price DECIMAL(30,12) NULL,
    profit_lock_active TINYINT(1) NOT NULL DEFAULT 0,
    profit_lock_price DECIMAL(30,12) NULL,
    explanation VARCHAR(2000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_position_management_test_run FOREIGN KEY (test_run_id) REFERENCES analysis_test_run(id) ON DELETE CASCADE,
    INDEX idx_position_management_test_run_time (test_run_id, generated_at)
);

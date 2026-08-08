CREATE TABLE price_move_monitor_settings (
    id BIGINT NOT NULL PRIMARY KEY,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    minimum_move_percent DECIMAL(12,6) NOT NULL DEFAULT 0.300000,
    window_minutes INT NOT NULL DEFAULT 30,
    retention_days INT NOT NULL DEFAULT 7,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

INSERT INTO price_move_monitor_settings (id, enabled, minimum_move_percent, window_minutes, retention_days)
VALUES (1, 1, 0.300000, 30, 7);

CREATE TABLE price_move_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(30) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    start_time TIMESTAMP(6) NOT NULL,
    end_time TIMESTAMP(6) NOT NULL,
    start_price DECIMAL(30,12) NOT NULL,
    end_price DECIMAL(30,12) NOT NULL,
    change_percent DECIMAL(20,8) NOT NULL,
    duration_seconds BIGINT NOT NULL,
    review_status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_price_move_event_symbol_time (symbol, end_time),
    INDEX idx_price_move_event_status_time (review_status, end_time)
);

ALTER TABLE price_move_event
    ADD COLUMN block_start_time TIMESTAMP(6) NULL AFTER direction,
    ADD COLUMN block_end_time TIMESTAMP(6) NULL AFTER block_start_time,
    ADD COLUMN detection_window VARCHAR(10) NULL AFTER duration_seconds,
    ADD COLUMN outcome_status VARCHAR(40) NOT NULL DEFAULT 'PENDING' AFTER importance_level,
    ADD COLUMN blame_required TINYINT(1) NOT NULL DEFAULT 0 AFTER outcome_status,
    ADD COLUMN blame_reviewed TINYINT(1) NOT NULL DEFAULT 0 AFTER blame_required,
    ADD COLUMN blame_code VARCHAR(100) NULL AFTER blame_reviewed,
    ADD COLUMN blame_explanation VARCHAR(2000) NULL AFTER blame_code,
    ADD COLUMN best_signal_id BIGINT NULL AFTER blame_explanation,
    ADD COLUMN best_signal_decision VARCHAR(30) NULL AFTER best_signal_id,
    ADD COLUMN best_signal_score INT NULL AFTER best_signal_decision,
    ADD COLUMN trade_id BIGINT NULL AFTER best_signal_score;

CREATE INDEX idx_price_move_event_blame ON price_move_event (blame_required, blame_reviewed, end_time);
CREATE INDEX idx_price_move_event_block ON price_move_event (symbol, block_start_time, direction);

ALTER TABLE price_move_event ADD UNIQUE KEY uk_price_move_event_block_direction (symbol, block_start_time, direction);

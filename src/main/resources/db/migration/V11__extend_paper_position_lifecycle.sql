ALTER TABLE paper_position
    ADD COLUMN exit_signal_id BIGINT NULL AFTER signal_id,
    ADD COLUMN close_reason VARCHAR(40) NULL AFTER realized_pnl,
    ADD COLUMN entry_reason VARCHAR(2000) NULL AFTER close_reason,
    ADD COLUMN exit_reason VARCHAR(2000) NULL AFTER entry_reason;

ALTER TABLE paper_position
    ADD CONSTRAINT fk_paper_position_exit_signal
        FOREIGN KEY (exit_signal_id) REFERENCES trade_signal(id);

CREATE INDEX idx_paper_position_exit_signal
    ON paper_position(exit_signal_id);

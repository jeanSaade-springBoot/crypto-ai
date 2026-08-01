ALTER TABLE trade_signal
    ADD COLUMN confidence_score INT NOT NULL DEFAULT 0 AFTER total_score,
    ADD COLUMN final_entry_allowed BOOLEAN NOT NULL DEFAULT TRUE AFTER confidence_score,
    ADD COLUMN decision_path JSON NULL AFTER final_entry_allowed,
    ADD COLUMN final_decision_explanation VARCHAR(2000) NULL AFTER decision_path;

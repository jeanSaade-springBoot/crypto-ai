-- Read-only-review metadata for blocked BUY research. It never participates in trading decisions.
CREATE TABLE blocked_buy_review (
    signal_id BIGINT NOT NULL PRIMARY KEY,
    suspicious TINYINT(1) NOT NULL DEFAULT 1,
    marked_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_blocked_buy_review_suspicious_marked (suspicious, marked_at)
);

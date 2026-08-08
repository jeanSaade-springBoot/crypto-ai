ALTER TABLE execution_opportunity
    ADD COLUMN bearish_count INT NOT NULL DEFAULT 0 AFTER neutral_count,
    ADD COLUMN opportunity_health INT NOT NULL DEFAULT 50 AFTER evidence_score,
    ADD COLUMN last_bearish_at TIMESTAMP(6) NULL AFTER opportunity_health;

ALTER TABLE execution_opportunity
    ADD COLUMN evidence_momentum INT NOT NULL DEFAULT 0 AFTER health_momentum;

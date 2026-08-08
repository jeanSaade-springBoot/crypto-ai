ALTER TABLE execution_opportunity
    ADD COLUMN health_momentum INT NOT NULL DEFAULT 0 AFTER opportunity_health;

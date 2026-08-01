ALTER TABLE trade_signal
    ADD COLUMN derivatives_status VARCHAR(40) NOT NULL DEFAULT 'UNAVAILABLE',
    ADD COLUMN derivatives_entry_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN funding_rate DECIMAL(20,12) NULL,
    ADD COLUMN funding_percentile DECIMAL(8,2) NULL,
    ADD COLUMN open_interest DECIMAL(30,8) NULL,
    ADD COLUMN open_interest_value DECIMAL(30,8) NULL,
    ADD COLUMN open_interest_change_percent DECIMAL(20,8) NULL,
    ADD COLUMN derivatives_price_change_percent DECIMAL(20,8) NULL,
    ADD COLUMN funding_sample_size INT NOT NULL DEFAULT 0,
    ADD COLUMN derivatives_period VARCHAR(10) NULL,
    ADD COLUMN derivatives_confidence_adjustment INT NOT NULL DEFAULT 0,
    ADD COLUMN derivatives_explanation VARCHAR(2000) NULL,
    ADD COLUMN derivatives_evaluated_at TIMESTAMP(6) NULL;

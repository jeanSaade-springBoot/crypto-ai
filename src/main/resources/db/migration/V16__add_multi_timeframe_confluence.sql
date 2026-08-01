ALTER TABLE trade_signal
    ADD COLUMN original_decision VARCHAR(30) NULL AFTER decision,
    ADD COLUMN confluence_status VARCHAR(30) NULL AFTER original_decision,
    ADD COLUMN confluence_entry_allowed BOOLEAN NOT NULL DEFAULT TRUE AFTER confluence_status,
    ADD COLUMN confluence_higher_interval VARCHAR(10) NULL AFTER confluence_entry_allowed,
    ADD COLUMN confluence_higher_decision VARCHAR(30) NULL AFTER confluence_higher_interval,
    ADD COLUMN confluence_higher_trend_score INT NULL AFTER confluence_higher_decision,
    ADD COLUMN confluence_explanation VARCHAR(1500) NULL AFTER confluence_higher_trend_score;

UPDATE trade_signal
SET original_decision = decision,
    confluence_status = 'UNAVAILABLE'
WHERE original_decision IS NULL;

ALTER TABLE trade_signal
    MODIFY COLUMN original_decision VARCHAR(30) NOT NULL,
    MODIFY COLUMN confluence_status VARCHAR(30) NOT NULL;

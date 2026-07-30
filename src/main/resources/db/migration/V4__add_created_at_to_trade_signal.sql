-- Add an immutable audit timestamp to every trade signal.
-- Existing rows are backfilled from generated_at so no historical data is lost.

ALTER TABLE trade_signal
    ADD COLUMN created_at TIMESTAMP(6) NULL AFTER generated_at;

UPDATE trade_signal
SET created_at = COALESCE(generated_at, CURRENT_TIMESTAMP(6))
WHERE created_at IS NULL;

ALTER TABLE trade_signal
    MODIFY COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

CREATE INDEX idx_trade_signal_created_at
    ON trade_signal(created_at);

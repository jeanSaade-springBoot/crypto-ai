ALTER TABLE trade_signal
    ADD COLUMN confluence_evaluated_at TIMESTAMP(6) NULL AFTER confluence_explanation,
    ADD COLUMN confluence_higher_signal_generated_at TIMESTAMP(6) NULL AFTER confluence_evaluated_at;

-- Existing rows predate immutable snapshot timestamps. Keep them null rather than
-- pretending that the historical context was evaluated later.

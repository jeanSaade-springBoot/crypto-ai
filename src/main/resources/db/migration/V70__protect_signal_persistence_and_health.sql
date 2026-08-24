-- FIX-072: the assembled TradeSignal explanation reached VARCHAR(2000) and caused
-- Production INSERT failures. Keep full diagnostics without allowing text length to drop a signal.
ALTER TABLE trade_signal
    MODIFY COLUMN explanation TEXT NULL;

-- Replay and its backend archive must accept the same production-shaped explanation payload.
ALTER TABLE trade_signal_test
    MODIFY COLUMN explanation TEXT NULL;

ALTER TABLE trade_signal_test_archive
    MODIFY COLUMN explanation TEXT NULL;

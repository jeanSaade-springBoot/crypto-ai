-- FIX-145: preserve every original ledger column, including full error text.
-- No automatic backfill, cancellation, completion or retry.
CREATE TABLE signal_processing_work_cancellation LIKE signal_processing_work;
ALTER TABLE signal_processing_work_cancellation
 ADD COLUMN cancelled_at TIMESTAMP(6) NOT NULL,
 ADD COLUMN operator_name VARCHAR(128) NOT NULL,
 ADD COLUMN review_reference VARCHAR(128) NOT NULL,
 ADD COLUMN decision VARCHAR(64) NOT NULL;

ALTER TABLE analysis_test_run
    ADD COLUMN failure_step VARCHAR(255) NULL AFTER error_message,
    ADD COLUMN failure_exception VARCHAR(500) NULL AFTER failure_step,
    ADD COLUMN failure_root_cause VARCHAR(1000) NULL AFTER failure_exception,
    ADD COLUMN failure_stack_trace LONGTEXT NULL AFTER failure_root_cause;

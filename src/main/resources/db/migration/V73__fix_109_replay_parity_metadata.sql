-- FIX-109: make Replay's parity contract visible/auditable per run.
ALTER TABLE analysis_test_run
    ADD COLUMN replay_price_mode VARCHAR(40) NULL,
    ADD COLUMN replay_logic_mode VARCHAR(40) NULL;

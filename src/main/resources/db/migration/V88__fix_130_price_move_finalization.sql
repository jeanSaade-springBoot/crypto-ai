-- FIX-130: reporting-only queue. Enqueue never locks the global worker gate.
CREATE TABLE price_move_finalization_work (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 symbol VARCHAR(30) NOT NULL,
 block_start TIMESTAMP(6) NOT NULL,
 snapshot_json LONGTEXT NOT NULL,
 status VARCHAR(40) NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 owner_token VARCHAR(36) NULL,
 next_attempt_at TIMESTAMP(6) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL,
 started_at TIMESTAMP(6) NULL,
 finished_at TIMESTAMP(6) NULL,
 last_error VARCHAR(1000) NULL,
 UNIQUE KEY uk_fix130_block(symbol,block_start),
 KEY idx_fix130_pending(status,next_attempt_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE price_move_finalization_gate (
 id INT NOT NULL PRIMARY KEY,
 active_job_id BIGINT NULL,
 owner_token VARCHAR(36) NULL
) ENGINE=InnoDB;
INSERT INTO price_move_finalization_gate(id) VALUES(1);

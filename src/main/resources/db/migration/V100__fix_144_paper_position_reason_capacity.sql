-- FIX-144: Preserve full paper-position audit explanations without changing trade decisions.
-- V11 introduced VARCHAR(2000); full explanations plus execution/progressive text exceed it.
-- New migration only: never edit the already-applied V11 checksum.
-- MySQL may rebuild this table and take metadata locks; run with the trader stopped.
-- Keep nullable fields and existing utf8mb4_unicode_ci collation. No work-state reconciliation.
ALTER TABLE paper_position
    MODIFY COLUMN entry_reason MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    MODIFY COLUMN exit_reason MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL;

-- FIX-132: short-lived, operator-reviewed proposals. No approvals are inferred.
CREATE TABLE shared_market_cutover_proposal (
 id VARCHAR(36) PRIMARY KEY, symbol VARCHAR(30) NOT NULL,
 operation VARCHAR(20) NOT NULL, requested_by VARCHAR(160) NOT NULL,
 base_state_hash VARCHAR(64) NOT NULL,
 cutover_sequence BIGINT NOT NULL, cutover_at TIMESTAMP(6) NOT NULL,
 expires_at TIMESTAMP(6) NOT NULL, consumed_at TIMESTAMP(6) NULL
) ENGINE=InnoDB;

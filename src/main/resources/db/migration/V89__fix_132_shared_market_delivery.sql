CREATE TABLE shared_market_consumer_state (
 symbol VARCHAR(30) PRIMARY KEY, cutover_sequence BIGINT NOT NULL, cutover_at TIMESTAMP(6) NOT NULL,
 discovered_sequence BIGINT NOT NULL, last_observed_at TIMESTAMP(6) NULL,
 last_price DECIMAL(30,12) NULL, status VARCHAR(40) NOT NULL DEFAULT 'READY',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;
CREATE TABLE shared_market_event_delivery (
 source_event_id BIGINT PRIMARY KEY, symbol VARCHAR(30) NOT NULL, symbol_sequence BIGINT NOT NULL,
 interval_code VARCHAR(10) NOT NULL, candle_open_time TIMESTAMP(6) NOT NULL,
 candle_close_time TIMESTAMP(6) NOT NULL, closed BOOLEAN NOT NULL, observed_at TIMESTAMP(6) NULL,
 received_at TIMESTAMP(6) NOT NULL, source_created_at TIMESTAMP(6) NOT NULL,
 price DECIMAL(30,12) NOT NULL, source VARCHAR(30) NOT NULL, classification VARCHAR(40) NOT NULL,
 phase INT NOT NULL DEFAULT 0, status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
 analysis_status VARCHAR(40) NOT NULL DEFAULT 'WAITING', observer_status VARCHAR(40) NOT NULL DEFAULT 'PENDING', owner_token VARCHAR(36) NULL,
 protection_started_at TIMESTAMP(6) NULL, protection_owner VARCHAR(36) NULL,
 protection_completed_at TIMESTAMP(6) NULL, analysis_started_at TIMESTAMP(6) NULL, analysis_completed_at TIMESTAMP(6) NULL,
 last_error VARCHAR(1000) NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_shared_symbol_seq(symbol,symbol_sequence), KEY idx_shared_work(symbol,phase,symbol_sequence),
 KEY idx_shared_analysis(analysis_status,symbol,interval_code,symbol_sequence)
) ENGINE=InnoDB;
ALTER TABLE market_price_event ADD COLUMN source_event_id BIGINT NULL,
 ADD COLUMN source_sequence BIGINT NULL, ADD COLUMN source_received_at TIMESTAMP(6) NULL,
 ADD COLUMN delivery_status VARCHAR(40) NOT NULL DEFAULT 'LEGACY',
 ADD UNIQUE KEY uk_market_price_source_event(source_event_id);
CREATE TABLE shared_candle_input_audit (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, context_key VARCHAR(100) NOT NULL,
 read_index INT NOT NULL, input_hash VARCHAR(64) NOT NULL, row_count INT NOT NULL,
 query_text VARCHAR(1000) NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY idx_shared_input_context(context_key,read_index)
) ENGINE=InnoDB;

CREATE TABLE shared_market_health (
 component VARCHAR(40) PRIMARY KEY, status VARCHAR(40) NOT NULL, detail VARCHAR(1000) NULL,
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;
CREATE TABLE shared_market_cutover_history (
 symbol VARCHAR(30) NOT NULL, cutover_at TIMESTAMP(6) NOT NULL, cutover_sequence BIGINT NOT NULL,
 PRIMARY KEY(symbol,cutover_at,cutover_sequence)
) ENGINE=InnoDB;

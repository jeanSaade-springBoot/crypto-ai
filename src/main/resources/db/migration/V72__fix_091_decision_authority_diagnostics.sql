-- FIX-091: approved pre-wallet trading-intelligence hardening.
-- Every column added here is mirrored into Replay/Test tables so Production and Replay
-- can be compared field-for-field without touching wallet behavior.

ALTER TABLE trade_signal
    ADD COLUMN raw_confidence_score INT NULL AFTER confidence_score,
    ADD COLUMN effective_confidence_score INT NULL AFTER raw_confidence_score,
    ADD COLUMN primary_blocking_stage VARCHAR(80) NULL AFTER effective_confidence_score,
    ADD COLUMN detected_regime VARCHAR(40) NULL AFTER market_regime,
    ADD COLUMN candidate_regime VARCHAR(40) NULL AFTER detected_regime,
    ADD COLUMN confirmed_regime VARCHAR(40) NULL AFTER candidate_regime,
    ADD COLUMN regime_candidate_count INT NOT NULL DEFAULT 0 AFTER confirmed_regime,
    ADD COLUMN entry_authority VARCHAR(40) NULL AFTER regime_candidate_count,
    ADD COLUMN entry_authority_max_position_percent INT NULL AFTER entry_authority,
    ADD COLUMN entry_authority_explanation VARCHAR(1500) NULL AFTER entry_authority_max_position_percent;

ALTER TABLE trade_signal_test
    ADD COLUMN raw_confidence_score INT NULL AFTER confidence_score,
    ADD COLUMN effective_confidence_score INT NULL AFTER raw_confidence_score,
    ADD COLUMN primary_blocking_stage VARCHAR(80) NULL AFTER effective_confidence_score,
    ADD COLUMN detected_regime VARCHAR(40) NULL AFTER market_regime,
    ADD COLUMN candidate_regime VARCHAR(40) NULL AFTER detected_regime,
    ADD COLUMN confirmed_regime VARCHAR(40) NULL AFTER candidate_regime,
    ADD COLUMN regime_candidate_count INT NOT NULL DEFAULT 0 AFTER confirmed_regime,
    ADD COLUMN entry_authority VARCHAR(40) NULL AFTER regime_candidate_count,
    ADD COLUMN entry_authority_max_position_percent INT NULL AFTER entry_authority,
    ADD COLUMN entry_authority_explanation VARCHAR(1500) NULL AFTER entry_authority_max_position_percent;

ALTER TABLE trade_signal_test_archive
    ADD COLUMN raw_confidence_score INT NULL AFTER confidence_score,
    ADD COLUMN effective_confidence_score INT NULL AFTER raw_confidence_score,
    ADD COLUMN primary_blocking_stage VARCHAR(80) NULL AFTER effective_confidence_score,
    ADD COLUMN detected_regime VARCHAR(40) NULL AFTER market_regime,
    ADD COLUMN candidate_regime VARCHAR(40) NULL AFTER detected_regime,
    ADD COLUMN confirmed_regime VARCHAR(40) NULL AFTER candidate_regime,
    ADD COLUMN regime_candidate_count INT NOT NULL DEFAULT 0 AFTER confirmed_regime,
    ADD COLUMN entry_authority VARCHAR(40) NULL AFTER regime_candidate_count,
    ADD COLUMN entry_authority_max_position_percent INT NULL AFTER entry_authority,
    ADD COLUMN entry_authority_explanation VARCHAR(1500) NULL AFTER entry_authority_max_position_percent;

ALTER TABLE analysis_test_signal
    ADD COLUMN raw_confidence_score INT NULL AFTER confidence_score,
    ADD COLUMN effective_confidence_score INT NULL AFTER raw_confidence_score,
    ADD COLUMN primary_blocking_stage VARCHAR(80) NULL AFTER effective_confidence_score,
    ADD COLUMN detected_regime VARCHAR(40) NULL AFTER primary_blocking_stage,
    ADD COLUMN candidate_regime VARCHAR(40) NULL AFTER detected_regime,
    ADD COLUMN confirmed_regime VARCHAR(40) NULL AFTER candidate_regime,
    ADD COLUMN regime_candidate_count INT NOT NULL DEFAULT 0 AFTER confirmed_regime,
    ADD COLUMN entry_authority VARCHAR(40) NULL AFTER regime_candidate_count,
    ADD COLUMN entry_authority_max_position_percent INT NULL AFTER entry_authority;

ALTER TABLE analysis_test_signal_archive
    ADD COLUMN raw_confidence_score INT NULL AFTER confidence_score,
    ADD COLUMN effective_confidence_score INT NULL AFTER raw_confidence_score,
    ADD COLUMN primary_blocking_stage VARCHAR(80) NULL AFTER effective_confidence_score,
    ADD COLUMN detected_regime VARCHAR(40) NULL AFTER primary_blocking_stage,
    ADD COLUMN candidate_regime VARCHAR(40) NULL AFTER detected_regime,
    ADD COLUMN confirmed_regime VARCHAR(40) NULL AFTER candidate_regime,
    ADD COLUMN regime_candidate_count INT NOT NULL DEFAULT 0 AFTER confirmed_regime,
    ADD COLUMN entry_authority VARCHAR(40) NULL AFTER regime_candidate_count,
    ADD COLUMN entry_authority_max_position_percent INT NULL AFTER entry_authority;

ALTER TABLE execution_opportunity
    ADD COLUMN peak_score INT NOT NULL DEFAULT 0 AFTER average_confidence,
    ADD COLUMN peak_confidence INT NOT NULL DEFAULT 0 AFTER peak_score,
    ADD COLUMN peak_decision VARCHAR(30) NULL AFTER peak_confidence,
    ADD COLUMN peak_regime VARCHAR(40) NULL AFTER peak_decision,
    ADD COLUMN peak_btc_status VARCHAR(30) NULL AFTER peak_regime,
    ADD COLUMN peak_liquidity_status VARCHAR(30) NULL AFTER peak_btc_status,
    ADD COLUMN peak_observed_at TIMESTAMP(6) NULL AFTER peak_liquidity_status;

ALTER TABLE execution_opportunity_test
    ADD COLUMN peak_score INT NOT NULL DEFAULT 0 AFTER opportunity_health,
    ADD COLUMN peak_confidence INT NOT NULL DEFAULT 0 AFTER peak_score,
    ADD COLUMN peak_decision VARCHAR(30) NULL AFTER peak_confidence,
    ADD COLUMN peak_regime VARCHAR(40) NULL AFTER peak_decision,
    ADD COLUMN peak_btc_status VARCHAR(30) NULL AFTER peak_regime,
    ADD COLUMN peak_liquidity_status VARCHAR(30) NULL AFTER peak_btc_status,
    ADD COLUMN peak_observed_at TIMESTAMP(6) NULL AFTER peak_liquidity_status;

ALTER TABLE execution_opportunity_test_archive
    ADD COLUMN peak_score INT NOT NULL DEFAULT 0 AFTER opportunity_health,
    ADD COLUMN peak_confidence INT NOT NULL DEFAULT 0 AFTER peak_score,
    ADD COLUMN peak_decision VARCHAR(30) NULL AFTER peak_confidence,
    ADD COLUMN peak_regime VARCHAR(40) NULL AFTER peak_decision,
    ADD COLUMN peak_btc_status VARCHAR(30) NULL AFTER peak_regime,
    ADD COLUMN peak_liquidity_status VARCHAR(30) NULL AFTER peak_btc_status,
    ADD COLUMN peak_observed_at TIMESTAMP(6) NULL AFTER peak_liquidity_status;

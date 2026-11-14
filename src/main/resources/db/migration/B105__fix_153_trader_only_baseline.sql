-- FIX-153 trader-only fresh installation at SQL schema version 105.
-- Existing Flyway histories ignore B migrations. V106 is applied normally afterward.
-- Source-qualified crypto_ai identifiers are made connection-local.
-- Java V103 collector admission indexes deliberately have no trader-only baseline effect.

-- Baseline section V1__initial_schema.sql
-- =========================================================
-- Crypto AI Trader - Initial Schema
-- MySQL 8
-- =========================================================

-- ---------------------------------------------------------
-- Candle
-- ---------------------------------------------------------
CREATE TABLE candle (
    id BIGINT NOT NULL AUTO_INCREMENT,

    symbol VARCHAR(30) NOT NULL,
    interval_code VARCHAR(10) NOT NULL,

    open_time TIMESTAMP(6) NOT NULL,
    close_time TIMESTAMP(6) NOT NULL,

    open_price DECIMAL(30, 12) NOT NULL,
    high_price DECIMAL(30, 12) NOT NULL,
    low_price DECIMAL(30, 12) NOT NULL,
    close_price DECIMAL(30, 12) NOT NULL,

    volume DECIMAL(38, 12) NOT NULL,
    quote_asset_volume DECIMAL(38, 12),

    number_of_trades BIGINT,

    taker_buy_base_volume DECIMAL(38, 12),
    taker_buy_quote_volume DECIMAL(38, 12),

    closed BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6),

    updated_at TIMESTAMP(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT uk_candle_symbol_interval_open_time
        UNIQUE (
            symbol,
            interval_code,
            open_time
        )
);

CREATE INDEX idx_candle_symbol_interval_time
    ON candle (
        symbol,
        interval_code,
        open_time
    );

CREATE INDEX idx_candle_closed
    ON candle (closed);


-- ---------------------------------------------------------
-- Sentiment Signal
-- ---------------------------------------------------------
CREATE TABLE sentiment_signal (
    id BIGINT NOT NULL AUTO_INCREMENT,

    symbol VARCHAR(30) NOT NULL,

    score DECIMAL(8, 6) NOT NULL,
    confidence DECIMAL(8, 6) NOT NULL,

    source VARCHAR(30) NOT NULL,
    summary VARCHAR(1000),

    observed_at TIMESTAMP(6) NOT NULL,

    PRIMARY KEY (id)
);

CREATE INDEX idx_sentiment_symbol_observed
    ON sentiment_signal (
        symbol,
        observed_at
    );

CREATE INDEX idx_sentiment_source
    ON sentiment_signal (source);


-- ---------------------------------------------------------
-- Market Fundamental
-- ---------------------------------------------------------
CREATE TABLE market_fundamental (
    id BIGINT NOT NULL AUTO_INCREMENT,

    symbol VARCHAR(30) NOT NULL,

    market_cap DECIMAL(38, 2),
    fully_diluted_valuation DECIMAL(38, 2),
    volume_24h DECIMAL(38, 2),

    circulating_supply DECIMAL(38, 8),
    total_supply DECIMAL(38, 8),

    observed_at TIMESTAMP(6) NOT NULL,

    PRIMARY KEY (id)
);

CREATE INDEX idx_fundamental_symbol_observed
    ON market_fundamental (
        symbol,
        observed_at
    );


-- ---------------------------------------------------------
-- Trade Signal
--
-- TEMPORARY DEFINITION:
-- Replace its columns after checking TradeSignal.java.
-- id is required because paper_position references it.
-- ---------------------------------------------------------
CREATE TABLE trade_signal (
    id BIGINT NOT NULL AUTO_INCREMENT,

    symbol VARCHAR(30) NOT NULL,

    interval_code VARCHAR(10) NOT NULL,

    decision VARCHAR(30) NOT NULL,

    total_score INT NOT NULL,
    trend_score INT NOT NULL,
    volume_score INT NOT NULL,
    momentum_score INT NOT NULL,
    sentiment_score INT NOT NULL,
    fundamental_score INT NOT NULL,

    latest_price DECIMAL(30,12) NOT NULL,
    stop_loss DECIMAL(30,12),
    take_profit DECIMAL(30,12),

    explanation VARCHAR(2000),

    generated_at TIMESTAMP(6) NOT NULL,

    PRIMARY KEY (id)
);

CREATE INDEX idx_trade_signal_symbol_interval
    ON trade_signal(symbol, interval_code);

CREATE INDEX idx_trade_signal_generated
    ON trade_signal(generated_at);

CREATE INDEX idx_trade_signal_decision
    ON trade_signal(decision);

-- ---------------------------------------------------------
-- Paper Position
-- ---------------------------------------------------------
CREATE TABLE paper_position (
    id BIGINT NOT NULL AUTO_INCREMENT,

    symbol VARCHAR(30) NOT NULL,

    side VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,

    quantity DECIMAL(30, 12) NOT NULL,

    entry_price DECIMAL(30, 12) NOT NULL,
    stop_loss DECIMAL(30, 12) NOT NULL,
    take_profit DECIMAL(30, 12) NOT NULL,

    exit_price DECIMAL(30, 12),
    realized_pnl DECIMAL(30, 12),

    signal_id BIGINT NOT NULL,

    opened_at TIMESTAMP(6) NOT NULL,
    closed_at TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT fk_paper_position_trade_signal
        FOREIGN KEY (signal_id)
        REFERENCES trade_signal (id)
);

CREATE INDEX idx_paper_position_symbol_status
    ON paper_position (
        symbol,
        status
    );

CREATE INDEX idx_paper_position_signal
    ON paper_position (signal_id);

CREATE INDEX idx_paper_position_opened_at
    ON paper_position (opened_at);

-- Baseline section V2__create_technical_indicator_table.sql
CREATE TABLE technical_indicator (
    id BIGINT NOT NULL AUTO_INCREMENT,

    symbol VARCHAR(30) NOT NULL,
    interval_code VARCHAR(10) NOT NULL,
    candle_open_time TIMESTAMP(6) NOT NULL,

    close_price DECIMAL(30, 12) NOT NULL,

    sma_20 DECIMAL(30, 12),
    ema_20 DECIMAL(30, 12),
    ema_50 DECIMAL(30, 12),

    rsi_14 DECIMAL(20, 8),

    bollinger_middle DECIMAL(30, 12),
    bollinger_upper DECIMAL(30, 12),
    bollinger_lower DECIMAL(30, 12),
    bollinger_bandwidth DECIMAL(20, 8),

    atr_14 DECIMAL(30, 12),

    volume_sma_20 DECIMAL(30, 12),

    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    UNIQUE KEY uk_indicator_candle (
        symbol,
        interval_code,
        candle_open_time
    ),

    INDEX idx_indicator_symbol_interval_time (
        symbol,
        interval_code,
        candle_open_time
    )
);

-- Baseline section V3__extend_technical_indicator.sql
ALTER TABLE technical_indicator
    ADD COLUMN ema_200 DECIMAL(30, 12),
    ADD COLUMN macd DECIMAL(30, 12),
    ADD COLUMN macd_signal DECIMAL(30, 12),
    ADD COLUMN macd_histogram DECIMAL(30, 12),
    ADD COLUMN relative_volume DECIMAL(20, 8);

-- Baseline section V4__add_created_at_to_trade_signal.sql
-- Add an immutable audit timestamp to every trade signal.
-- Existing rows are backfilled from generated_at so no historical data is lost.

ALTER TABLE trade_signal
    ADD COLUMN created_at TIMESTAMP(6) NULL AFTER generated_at;

UPDATE trade_signal
SET created_at = COALESCE(generated_at, CURRENT_TIMESTAMP(6))
WHERE created_at IS NULL;

ALTER TABLE trade_signal
    MODIFY COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

CREATE INDEX idx_trade_signal_created_at
    ON trade_signal(created_at);


-- Baseline section V5__create_sentiment_provider_table.sql
CREATE TABLE sentiment_provider (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider_code VARCHAR(60) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    weight DECIMAL(10,6) NOT NULL DEFAULT 0,
    collection_interval_seconds BIGINT NOT NULL DEFAULT 300,
    last_collection_at TIMESTAMP(6) NULL,
    last_success_at TIMESTAMP(6) NULL,
    last_status VARCHAR(30) NOT NULL DEFAULT 'NEVER_RUN',
    last_message VARCHAR(1000) NULL,
    api_key_env_var VARCHAR(100) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_sentiment_provider_code UNIQUE (provider_code)
);


-- Baseline section V6__create_whale_activity.sql
CREATE TABLE whale_activity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    blockchain VARCHAR(30) NOT NULL,
    transaction_hash VARCHAR(180) NOT NULL,
    wallet_address VARCHAR(220) NOT NULL,
    counterparty_address VARCHAR(220) NULL,
    wallet_label VARCHAR(180) NULL,
    counterparty_label VARCHAR(180) NULL,
    symbol VARCHAR(30) NOT NULL,
    asset VARCHAR(20) NOT NULL,
    movement_type VARCHAR(40) NOT NULL,
    amount DECIMAL(38,12) NULL,
    usd_value DECIMAL(24,2) NOT NULL,
    transaction_score DECIMAL(10,8) NOT NULL,
    transaction_confidence DECIMAL(10,8) NOT NULL,
    price_at_signal DECIMAL(30,12) NULL,
    evaluation_horizon VARCHAR(30) NOT NULL,
    evaluation_due_at TIMESTAMP(6) NOT NULL,
    price_at_evaluation DECIMAL(30,12) NULL,
    market_return DECIMAL(14,10) NULL,
    evaluation_result VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    prediction_quality DECIMAL(10,8) NULL,
    evaluated_at TIMESTAMP(6) NULL,
    whale_total_signals BIGINT NOT NULL DEFAULT 0,
    whale_correct_signals BIGINT NOT NULL DEFAULT 0,
    whale_incorrect_signals BIGINT NOT NULL DEFAULT 0,
    whale_inconclusive_signals BIGINT NOT NULL DEFAULT 0,
    whale_accuracy DECIMAL(10,8) NOT NULL DEFAULT 0,
    whale_average_quality DECIMAL(10,8) NOT NULL DEFAULT 0,
    whale_learned_weight DECIMAL(10,8) NOT NULL DEFAULT 0.15,
    observed_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_whale_activity_tx_wallet_horizon UNIQUE
        (blockchain, transaction_hash, wallet_address, evaluation_horizon),
    INDEX idx_whale_activity_due (evaluation_result, evaluation_due_at),
    INDEX idx_whale_activity_symbol_time (symbol, observed_at),
    INDEX idx_whale_activity_wallet_performance
        (wallet_address, symbol, evaluation_horizon, evaluated_at)
);

INSERT INTO sentiment_provider (
    provider_code,
    display_name,
    enabled,
    weight,
    collection_interval_seconds,
    last_status,
    api_key_env_var
)
VALUES (
    'WHALE_ALERT',
    'Whale Alert',
    FALSE,
    0.20,
    300,
    'NEVER_RUN',
    'WHALE_ALERT_API_KEY'
)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    weight = 0.20,
    collection_interval_seconds = VALUES(collection_interval_seconds),
    api_key_env_var = VALUES(api_key_env_var);


-- Baseline section V7__create_app_user_table.sql
CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role_name VARCHAR(50) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_app_user_username UNIQUE (username)
);

-- Initial local administrator.
-- Username: admin
-- Password: ChangeMe123!
-- Change or delete this row immediately after the first login.
-- {noop} is included only to make the first local login possible. New passwords
-- should be stored as {bcrypt}<bcrypt-hash>.
INSERT INTO app_user (username, password, role_name, enabled)
VALUES ('admin', '{noop}ChangeMe123!', 'ADMIN', TRUE);


-- Baseline section V8__add_trade_signal_score_breakdown.sql
ALTER TABLE trade_signal
    ADD COLUMN ema_cross_score INT NOT NULL DEFAULT 0,
    ADD COLUMN price_ema200_score INT NOT NULL DEFAULT 0,
    ADD COLUMN ema_alignment_score INT NOT NULL DEFAULT 0,
    ADD COLUMN sma20_score INT NOT NULL DEFAULT 0,
    ADD COLUMN rsi_score INT NOT NULL DEFAULT 0,
    ADD COLUMN macd_score INT NOT NULL DEFAULT 0,
    ADD COLUMN bollinger_score INT NOT NULL DEFAULT 0,
    ADD COLUMN relative_volume_score INT NOT NULL DEFAULT 0,
    ADD COLUMN volume_sma20_score INT NOT NULL DEFAULT 0,
    ADD COLUMN raw_score INT NOT NULL DEFAULT 0,
    ADD COLUMN maximum_available_score INT NOT NULL DEFAULT 0,
    ADD COLUMN sentiment_breakdown JSON NULL;


-- Baseline section V9__add_analysis_breakdown_to_trade_signal.sql
ALTER TABLE trade_signal
    ADD COLUMN analysis_breakdown JSON NULL AFTER sentiment_breakdown;


-- Baseline section V10__add_latest_volume_to_technical_indicator.sql
ALTER TABLE technical_indicator
    ADD COLUMN latest_volume DECIMAL(30,12) NULL AFTER atr_14;


-- Baseline section V11__extend_paper_position_lifecycle.sql
ALTER TABLE paper_position
    ADD COLUMN exit_signal_id BIGINT NULL AFTER signal_id,
    ADD COLUMN close_reason VARCHAR(40) NULL AFTER realized_pnl,
    ADD COLUMN entry_reason VARCHAR(2000) NULL AFTER close_reason,
    ADD COLUMN exit_reason VARCHAR(2000) NULL AFTER entry_reason;

ALTER TABLE paper_position
    ADD CONSTRAINT fk_paper_position_exit_signal
        FOREIGN KEY (exit_signal_id) REFERENCES trade_signal(id);

CREATE INDEX idx_paper_position_exit_signal
    ON paper_position(exit_signal_id);


-- Baseline section V12__extend_market_fundamental_metrics.sql
ALTER TABLE market_fundamental
    ADD COLUMN max_supply DECIMAL(38, 8) NULL AFTER total_supply,
    ADD COLUMN tier1_exchange_count INT NULL AFTER max_supply,
    ADD COLUMN exchange_count INT NULL AFTER tier1_exchange_count;


-- Baseline section V13__add_atr_risk_to_trade_signal.sql
ALTER TABLE trade_signal
    ADD COLUMN atr_at_signal DECIMAL(30,12) NULL,
    ADD COLUMN atr_percent DECIMAL(20,8) NULL,
    ADD COLUMN risk_reward_ratio DECIMAL(20,8) NULL,
    ADD COLUMN candle_range_atr_multiple DECIMAL(20,8) NULL,
    ADD COLUMN volatility_level VARCHAR(20) NULL,
    ADD COLUMN atr_overextended BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN atr_explanation VARCHAR(1000) NULL;


-- Baseline section V14__add_fundamental_ownership_allocation.sql
ALTER TABLE market_fundamental
    ADD COLUMN team_supply DECIMAL(38, 8) NULL,
    ADD COLUMN treasury_supply DECIMAL(38, 8) NULL,
    ADD COLUMN private_investor_supply DECIMAL(38, 8) NULL,
    ADD COLUMN locked_supply DECIMAL(38, 8) NULL;


-- Baseline section V15__add_grouped_trend_scores.sql
ALTER TABLE trade_signal
    ADD COLUMN trend_direction_score INT NOT NULL DEFAULT 0 AFTER sma20_score,
    ADD COLUMN trend_structure_score INT NOT NULL DEFAULT 0 AFTER trend_direction_score,
    ADD COLUMN trend_strength_score INT NOT NULL DEFAULT 0 AFTER trend_structure_score,
    ADD COLUMN trend_price_location_score INT NOT NULL DEFAULT 0 AFTER trend_strength_score;


-- Baseline section V16__add_multi_timeframe_confluence.sql
ALTER TABLE trade_signal
    ADD COLUMN original_decision VARCHAR(30) NULL AFTER decision,
    ADD COLUMN confluence_status VARCHAR(30) NULL AFTER original_decision,
    ADD COLUMN confluence_entry_allowed BOOLEAN NOT NULL DEFAULT TRUE AFTER confluence_status,
    ADD COLUMN confluence_higher_interval VARCHAR(10) NULL AFTER confluence_entry_allowed,
    ADD COLUMN confluence_higher_decision VARCHAR(30) NULL AFTER confluence_higher_interval,
    ADD COLUMN confluence_higher_trend_score INT NULL AFTER confluence_higher_decision,
    ADD COLUMN confluence_explanation VARCHAR(1500) NULL AFTER confluence_higher_trend_score;

UPDATE trade_signal
SET original_decision = decision,
    confluence_status = 'UNAVAILABLE'
WHERE original_decision IS NULL;

ALTER TABLE trade_signal
    MODIFY COLUMN original_decision VARCHAR(30) NOT NULL,
    MODIFY COLUMN confluence_status VARCHAR(30) NOT NULL;


-- Baseline section V17__add_confluence_snapshot_timestamps.sql
ALTER TABLE trade_signal
    ADD COLUMN confluence_evaluated_at TIMESTAMP(6) NULL AFTER confluence_explanation,
    ADD COLUMN confluence_higher_signal_generated_at TIMESTAMP(6) NULL AFTER confluence_evaluated_at;

-- Existing rows predate immutable snapshot timestamps. Keep them null rather than
-- pretending that the historical context was evaluated later.


-- Baseline section V18__add_btc_relationship_context.sql
ALTER TABLE trade_signal
    ADD COLUMN btc_relationship_type VARCHAR(30) NOT NULL DEFAULT 'UNAVAILABLE',
    ADD COLUMN btc_context_status VARCHAR(30) NOT NULL DEFAULT 'UNAVAILABLE',
    ADD COLUMN btc_context_entry_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN btc_context_interval VARCHAR(10) NULL,
    ADD COLUMN btc_context_decision VARCHAR(30) NULL,
    ADD COLUMN btc_context_trend_score INT NULL,
    ADD COLUMN btc_correlation DECIMAL(12,8) NULL,
    ADD COLUMN btc_beta DECIMAL(12,8) NULL,
    ADD COLUMN btc_relationship_sample_size INT NOT NULL DEFAULT 0,
    ADD COLUMN btc_influence_factor DECIMAL(8,6) NULL,
    ADD COLUMN btc_relationship_stable BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN btc_context_explanation VARCHAR(1500) NULL,
    ADD COLUMN btc_context_evaluated_at DATETIME(6) NULL,
    ADD COLUMN btc_signal_generated_at DATETIME(6) NULL;


-- Baseline section V19__add_order_book_liquidity_context.sql
ALTER TABLE trade_signal
    ADD COLUMN liquidity_status VARCHAR(30) NOT NULL DEFAULT 'UNAVAILABLE',
    ADD COLUMN liquidity_entry_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN order_book_imbalance DECIMAL(12,8) NULL,
    ADD COLUMN order_book_bid_depth DECIMAL(30,8) NULL,
    ADD COLUMN order_book_ask_depth DECIMAL(30,8) NULL,
    ADD COLUMN order_book_spread_percent DECIMAL(20,10) NULL,
    ADD COLUMN nearest_bid_wall_price DECIMAL(30,12) NULL,
    ADD COLUMN nearest_bid_wall_size DECIMAL(30,8) NULL,
    ADD COLUMN nearest_ask_wall_price DECIMAL(30,12) NULL,
    ADD COLUMN nearest_ask_wall_size DECIMAL(30,8) NULL,
    ADD COLUMN order_book_target_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN order_book_stop_exposed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN order_book_observations INT NOT NULL DEFAULT 0,
    ADD COLUMN liquidity_explanation VARCHAR(2000) NULL,
    ADD COLUMN liquidity_evaluated_at TIMESTAMP(6) NULL;


-- Baseline section V20__add_dynamic_market_strategy.sql
ALTER TABLE trade_signal
    ADD COLUMN market_regime VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN market_regime_confidence INT NOT NULL DEFAULT 0,
    ADD COLUMN selected_strategy VARCHAR(40) NOT NULL DEFAULT 'DEFENSIVE',
    ADD COLUMN strategy_version VARCHAR(30) NOT NULL DEFAULT '1.0',
    ADD COLUMN strategy_entry_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN strategy_explanation VARCHAR(1500) NULL,
    ADD COLUMN strategy_breakdown JSON NULL,
    ADD COLUMN strategy_trend_maximum INT NOT NULL DEFAULT 25,
    ADD COLUMN strategy_volume_maximum INT NOT NULL DEFAULT 20,
    ADD COLUMN strategy_momentum_maximum INT NOT NULL DEFAULT 15,
    ADD COLUMN strategy_sentiment_maximum INT NOT NULL DEFAULT 15,
    ADD COLUMN strategy_fundamental_maximum INT NOT NULL DEFAULT 10;


-- Baseline section V21__add_market_context_snapshot.sql
ALTER TABLE trade_signal
    ADD COLUMN market_context_snapshot JSON NULL AFTER strategy_breakdown;


-- Baseline section V22__add_final_decision_audit.sql
ALTER TABLE trade_signal
    ADD COLUMN confidence_score INT NOT NULL DEFAULT 0 AFTER total_score,
    ADD COLUMN final_entry_allowed BOOLEAN NOT NULL DEFAULT TRUE AFTER confidence_score,
    ADD COLUMN decision_path JSON NULL AFTER final_entry_allowed,
    ADD COLUMN final_decision_explanation VARCHAR(2000) NULL AFTER decision_path;


-- Baseline section V23__add_timeframe_aware_liquidity.sql
ALTER TABLE trade_signal
    ADD COLUMN order_book_window_seconds BIGINT NOT NULL DEFAULT 0 AFTER order_book_observations,
    ADD COLUMN order_book_wall_persistence_seconds BIGINT NOT NULL DEFAULT 0 AFTER order_book_window_seconds,
    ADD COLUMN order_book_influence_factor DECIMAL(8,6) NULL AFTER order_book_wall_persistence_seconds,
    ADD COLUMN order_book_veto_allowed BOOLEAN NOT NULL DEFAULT FALSE AFTER order_book_influence_factor;


-- Baseline section V24__add_derivatives_positioning_context.sql
ALTER TABLE trade_signal
    ADD COLUMN derivatives_status VARCHAR(40) NOT NULL DEFAULT 'UNAVAILABLE',
    ADD COLUMN derivatives_entry_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN funding_rate DECIMAL(20,12) NULL,
    ADD COLUMN funding_percentile DECIMAL(8,2) NULL,
    ADD COLUMN open_interest DECIMAL(30,8) NULL,
    ADD COLUMN open_interest_value DECIMAL(30,8) NULL,
    ADD COLUMN open_interest_change_percent DECIMAL(20,8) NULL,
    ADD COLUMN derivatives_price_change_percent DECIMAL(20,8) NULL,
    ADD COLUMN funding_sample_size INT NOT NULL DEFAULT 0,
    ADD COLUMN derivatives_period VARCHAR(10) NULL,
    ADD COLUMN derivatives_confidence_adjustment INT NOT NULL DEFAULT 0,
    ADD COLUMN derivatives_explanation VARCHAR(2000) NULL,
    ADD COLUMN derivatives_evaluated_at TIMESTAMP(6) NULL;


-- Baseline section V25__add_category_availability_snapshot.sql
ALTER TABLE trade_signal
    ADD COLUMN sentiment_available BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN fundamental_available BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN excluded_categories JSON NULL;


-- Baseline section V26__add_atr_entry_plan.sql
ALTER TABLE trade_signal
    ADD COLUMN atr_entry_type VARCHAR(40) NULL AFTER atr_overextended,
    ADD COLUMN atr_recommended_position_percent INT NOT NULL DEFAULT 100 AFTER atr_entry_type,
    ADD COLUMN atr_immediate_entry_allowed BOOLEAN NOT NULL DEFAULT TRUE AFTER atr_recommended_position_percent,
    ADD COLUMN atr_retracement_entry_price DECIMAL(30, 12) NULL AFTER atr_immediate_entry_allowed;


-- Baseline section V27__add_trade_signal_candle_idempotency.sql
ALTER TABLE trade_signal
    ADD COLUMN candle_open_time TIMESTAMP(6) NULL AFTER interval_code;

CREATE UNIQUE INDEX uk_trade_signal_symbol_interval_candle
    ON trade_signal (symbol, interval_code, candle_open_time);


-- Baseline section V28__create_wallet_portfolio.sql
CREATE TABLE wallet_asset (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(20) NOT NULL,
    quantity DECIMAL(30,12) NOT NULL DEFAULT 0,
    average_buy_price_usdt DECIMAL(30,12) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_asset_symbol (symbol)
);

CREATE TABLE wallet_settings (
    id BIGINT NOT NULL,
    base_trade_amount_usdt DECIMAL(30,12) NOT NULL DEFAULT 100,
    minimum_usdt_reserve DECIMAL(30,12) NOT NULL DEFAULT 0,
    automatic_execution_enabled TINYINT(1) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
);

CREATE TABLE wallet_cash_flow (
    id BIGINT NOT NULL AUTO_INCREMENT,
    flow_type VARCHAR(20) NOT NULL,
    amount_usdt DECIMAL(30,12) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    notes VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_wallet_cash_flow_time (occurred_at)
);

CREATE TABLE wallet_trade (
    id BIGINT NOT NULL AUTO_INCREMENT,
    signal_id BIGINT NULL,
    symbol VARCHAR(30) NOT NULL,
    side VARCHAR(10) NOT NULL,
    quantity DECIMAL(30,12) NOT NULL,
    price_usdt DECIMAL(30,12) NOT NULL,
    gross_amount_usdt DECIMAL(30,12) NOT NULL,
    fee_usdt DECIMAL(30,12) NOT NULL DEFAULT 0,
    net_amount_usdt DECIMAL(30,12) NOT NULL,
    cost_basis_usdt DECIMAL(30,12) NULL,
    realized_pnl_usdt DECIMAL(30,12) NULL,
    realized_pnl_percent DECIMAL(20,8) NULL,
    execution_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    status VARCHAR(20) NOT NULL DEFAULT 'EXECUTED',
    executed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    notes VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_wallet_trade_signal FOREIGN KEY (signal_id) REFERENCES trade_signal(id),
    KEY idx_wallet_trade_symbol_time (symbol, executed_at),
    KEY idx_wallet_trade_signal (signal_id)
);

CREATE TABLE wallet_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    portfolio_value_usdt DECIMAL(30,12) NOT NULL,
    net_invested_usdt DECIMAL(30,12) NOT NULL,
    total_pnl_usdt DECIMAL(30,12) NOT NULL,
    total_return_percent DECIMAL(20,8) NOT NULL,
    realized_pnl_usdt DECIMAL(30,12) NOT NULL,
    unrealized_pnl_usdt DECIMAL(30,12) NOT NULL,
    available_usdt DECIMAL(30,12) NOT NULL,
    captured_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_wallet_snapshot_time (captured_at)
);

INSERT INTO wallet_asset(symbol, quantity, average_buy_price_usdt, enabled)
VALUES ('USDT', 0, 1, 1)
ON DUPLICATE KEY UPDATE symbol = VALUES(symbol);

INSERT INTO wallet_settings(id, base_trade_amount_usdt, minimum_usdt_reserve, automatic_execution_enabled)
VALUES (1, 100, 0, 0)
ON DUPLICATE KEY UPDATE id = VALUES(id);


-- Baseline section V29__automatic_wallet_execution.sql
CREATE TABLE wallet_managed_position (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(30) NOT NULL,
    quantity DECIMAL(30,12) NOT NULL DEFAULT 0,
    average_entry_price_usdt DECIMAL(30,12) NOT NULL DEFAULT 0,
    total_cost_usdt DECIMAL(30,12) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    opened_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_wallet_managed_position_symbol_status (symbol, status)
);

ALTER TABLE wallet_trade
    ADD COLUMN execution_key VARCHAR(100) NULL AFTER signal_id,
    ADD UNIQUE KEY uk_wallet_trade_execution_key (execution_key);

UPDATE wallet_settings
SET automatic_execution_enabled = 0
WHERE id = 1;


-- Baseline section V30__add_daily_wallet_budget.sql
ALTER TABLE wallet_settings
    ADD COLUMN maximum_daily_new_positions INT NOT NULL DEFAULT 6 AFTER minimum_usdt_reserve;

CREATE TABLE wallet_daily_statistics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    trade_date DATE NOT NULL,
    maximum_new_positions INT NOT NULL,
    daily_trade_budget_usdt DECIMAL(30,12) NOT NULL DEFAULT 0,
    executed_buys INT NOT NULL DEFAULT 0,
    starting_usdt DECIMAL(30,12) NOT NULL DEFAULT 0,
    ending_usdt DECIMAL(30,12) NOT NULL DEFAULT 0,
    starting_portfolio_usdt DECIMAL(30,12) NOT NULL DEFAULT 0,
    ending_portfolio_usdt DECIMAL(30,12) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_daily_statistics_date (trade_date)
);

UPDATE wallet_settings
SET maximum_daily_new_positions = 6
WHERE id = 1;


-- Baseline section V31__create_position_analysis.sql
CREATE TABLE position_analysis (
    id BIGINT NOT NULL AUTO_INCREMENT,
    wallet_position_id BIGINT NOT NULL,
    trade_signal_id BIGINT NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    interval_code VARCHAR(10) NOT NULL,
    entry_price_usdt DECIMAL(30,12) NOT NULL,
    current_price_usdt DECIMAL(30,12) NOT NULL,
    unrealized_pnl_usdt DECIMAL(30,12) NOT NULL,
    unrealized_pnl_percent DECIMAL(20,8) NOT NULL,
    holding_minutes BIGINT NOT NULL,
    trend_deterioration_score INT NOT NULL,
    momentum_exhaustion_score INT NOT NULL,
    profit_protection_score INT NOT NULL,
    risk_event_score INT NOT NULL,
    opportunity_cost_score INT NOT NULL,
    exit_score INT NOT NULL,
    recommendation VARCHAR(20) NOT NULL,
    confidence INT NOT NULL,
    explanation VARCHAR(2000) NOT NULL,
    details_json JSON NULL,
    advisory_only BOOLEAN NOT NULL DEFAULT TRUE,
    analyzed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_position_analysis_wallet_position
        FOREIGN KEY (wallet_position_id) REFERENCES wallet_managed_position(id),
    CONSTRAINT fk_position_analysis_trade_signal
        FOREIGN KEY (trade_signal_id) REFERENCES trade_signal(id),
    CONSTRAINT uk_position_analysis_position_signal
        UNIQUE (wallet_position_id, trade_signal_id),
    INDEX idx_position_analysis_position_time (wallet_position_id, analyzed_at),
    INDEX idx_position_analysis_symbol_time (symbol, analyzed_at)
);


-- Baseline section V32__add_position_buy_thesis.sql
ALTER TABLE wallet_managed_position
    ADD COLUMN entry_signal_id BIGINT NULL AFTER id,
    ADD COLUMN entry_confidence INT NULL AFTER total_cost_usdt,
    ADD COLUMN entry_total_score INT NULL AFTER entry_confidence,
    ADD COLUMN entry_trend_score INT NULL AFTER entry_total_score,
    ADD COLUMN entry_structure_score INT NULL AFTER entry_trend_score,
    ADD COLUMN entry_momentum_score INT NULL AFTER entry_structure_score,
    ADD COLUMN entry_volume_score INT NULL AFTER entry_momentum_score,
    ADD COLUMN entry_sentiment_score INT NULL AFTER entry_volume_score,
    ADD COLUMN entry_fundamental_score INT NULL AFTER entry_sentiment_score,
    ADD COLUMN entry_decision VARCHAR(30) NULL AFTER entry_fundamental_score,
    ADD COLUMN entry_decision_path_json JSON NULL AFTER entry_decision,
    ADD COLUMN entry_analysis_snapshot_json JSON NULL AFTER entry_decision_path_json,
    ADD COLUMN stop_loss_usdt DECIMAL(30,12) NULL AFTER entry_analysis_snapshot_json,
    ADD COLUMN take_profit_usdt DECIMAL(30,12) NULL AFTER stop_loss_usdt,
    ADD CONSTRAINT fk_wallet_managed_position_entry_signal
        FOREIGN KEY (entry_signal_id) REFERENCES trade_signal(id),
    ADD INDEX idx_wallet_managed_position_entry_signal (entry_signal_id);


-- Baseline section V33__link_wallet_execution_to_position_decision.sql
ALTER TABLE wallet_trade
    ADD COLUMN position_analysis_id BIGINT NULL AFTER signal_id,
    ADD COLUMN execution_reason VARCHAR(40) NULL AFTER execution_type,
    ADD COLUMN execution_message VARCHAR(1000) NULL AFTER notes,
    ADD CONSTRAINT fk_wallet_trade_position_analysis
        FOREIGN KEY (position_analysis_id) REFERENCES position_analysis(id),
    ADD INDEX idx_wallet_trade_position_analysis (position_analysis_id),
    ADD INDEX idx_wallet_trade_execution_reason (execution_reason);

UPDATE wallet_trade
SET execution_reason = CASE
    WHEN side = 'BUY' THEN 'ENTRY_BUY'
    WHEN side = 'SELL' THEN 'SIGNAL_SELL'
    ELSE 'LEGACY_EXECUTION'
END
WHERE execution_reason IS NULL;


-- Baseline section V34__create_coin_configuration.sql
CREATE TABLE coin_configuration (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(20) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    system_default TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_coin_configuration_symbol (symbol)
);

INSERT INTO coin_configuration(symbol, enabled, system_default) VALUES
('BTCUSDT', 1, 1),
('ETHUSDT', 1, 1),
('BNBUSDT', 1, 1),
('XRPUSDT', 1, 1),
('SHIBUSDT', 1, 1),
('PEPEUSDT', 1, 1),
('XLMUSDT', 1, 1)
ON DUPLICATE KEY UPDATE system_default = VALUES(system_default);


-- Baseline section V35__make_daily_position_limit_admin_configurable.sql
UPDATE wallet_settings
SET maximum_daily_new_positions = 0
WHERE id = 1
  AND maximum_daily_new_positions = 6;

UPDATE wallet_daily_statistics
SET maximum_new_positions = 0
WHERE trade_date = CURRENT_DATE
  AND maximum_new_positions = 6;


-- Baseline section V36__remove_automatic_execution_toggle.sql
ALTER TABLE wallet_settings
    DROP COLUMN automatic_execution_enabled;


-- Baseline section V37__add_dashboard_trade_performance_settings.sql
ALTER TABLE wallet_settings
    ADD COLUMN performance_window_type VARCHAR(20) NOT NULL DEFAULT 'LAST_TRADES' AFTER maximum_daily_new_positions,
    ADD COLUMN performance_trade_count INT NOT NULL DEFAULT 20 AFTER performance_window_type,
    ADD COLUMN performance_period_days INT NOT NULL DEFAULT 1 AFTER performance_trade_count,
    ADD COLUMN performance_start_date DATE NULL AFTER performance_period_days,
    ADD COLUMN performance_end_date DATE NULL AFTER performance_start_date;

UPDATE wallet_settings
SET performance_window_type = 'LAST_TRADES',
    performance_trade_count = 20,
    performance_period_days = 1
WHERE id = 1;


-- Baseline section V38__add_dashboard_interval_configuration.sql
ALTER TABLE wallet_settings
    ADD COLUMN dashboard_intervals VARCHAR(100) NOT NULL DEFAULT '1m,5m,1h,4h,1d'
    AFTER performance_end_date;

UPDATE wallet_settings
SET dashboard_intervals = '1m,5m,1h,4h,1d'
WHERE dashboard_intervals IS NULL OR dashboard_intervals = '';


-- Baseline section V39__add_execution_validation_settings.sql
ALTER TABLE wallet_settings
    ADD COLUMN require_new_buy_transition TINYINT(1) NOT NULL DEFAULT 1 AFTER dashboard_intervals;


-- Baseline section V40__add_dynamic_profit_lock.sql
ALTER TABLE wallet_settings
    ADD COLUMN dynamic_profit_lock_enabled TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN profit_lock_activation_percent DECIMAL(8,4) NOT NULL DEFAULT 70.0000,
    ADD COLUMN profit_lock_initial_percent DECIMAL(8,4) NOT NULL DEFAULT 40.0000,
    ADD COLUMN profit_lock_trail_step_percent DECIMAL(8,4) NOT NULL DEFAULT 10.0000;

ALTER TABLE wallet_managed_position
    ADD COLUMN highest_price_usdt DECIMAL(30,12) NULL,
    ADD COLUMN profit_lock_active TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN profit_lock_price_usdt DECIMAL(30,12) NULL,
    ADD COLUMN profit_lock_progress_percent DECIMAL(12,6) NULL,
    ADD COLUMN profit_lock_activated_at TIMESTAMP(6) NULL;


-- Baseline section V41__add_execution_profile.sql
ALTER TABLE wallet_settings
    ADD COLUMN execution_profile VARCHAR(20) NOT NULL DEFAULT 'BALANCED' AFTER require_new_buy_transition;

UPDATE wallet_settings
SET execution_profile = 'BALANCED'
WHERE execution_profile IS NULL OR TRIM(execution_profile) = '';


-- Baseline section V42__create_execution_opportunity.sql
CREATE TABLE execution_opportunity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(30) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    last_evidence_at TIMESTAMP(6) NOT NULL,
    latest_signal_id BIGINT NULL,
    evidence_count INT NOT NULL DEFAULT 0,
    buy_count INT NOT NULL DEFAULT 0,
    watch_count INT NOT NULL DEFAULT 0,
    neutral_count INT NOT NULL DEFAULT 0,
    evidence_score INT NOT NULL DEFAULT 0,
    average_signal_score INT NOT NULL DEFAULT 0,
    average_confidence INT NOT NULL DEFAULT 0,
    five_minute_decision VARCHAR(30) NULL,
    one_hour_decision VARCHAR(30) NULL,
    execution_source VARCHAR(40) NULL,
    recommended_position_percent INT NOT NULL DEFAULT 0,
    decision_code VARCHAR(80) NULL,
    decision_explanation VARCHAR(2000) NULL,
    executed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_execution_opportunity_latest_signal
        FOREIGN KEY (latest_signal_id) REFERENCES trade_signal(id),
    INDEX idx_execution_opportunity_symbol_status (symbol, status, last_evidence_at),
    INDEX idx_execution_opportunity_latest_signal (latest_signal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Baseline section V43__add_opportunity_health_memory.sql
ALTER TABLE execution_opportunity
    ADD COLUMN bearish_count INT NOT NULL DEFAULT 0 AFTER neutral_count,
    ADD COLUMN opportunity_health INT NOT NULL DEFAULT 50 AFTER evidence_score,
    ADD COLUMN last_bearish_at TIMESTAMP(6) NULL AFTER opportunity_health;


-- Baseline section V44__add_opportunity_health_momentum.sql
ALTER TABLE execution_opportunity
    ADD COLUMN health_momentum INT NOT NULL DEFAULT 0 AFTER opportunity_health;


-- Baseline section V45__add_execution_opportunity_evidence_momentum.sql
ALTER TABLE execution_opportunity
    ADD COLUMN evidence_momentum INT NOT NULL DEFAULT 0 AFTER health_momentum;


-- Baseline section V46__add_progressive_position_building.sql
ALTER TABLE wallet_managed_position
    ADD COLUMN entry_stage VARCHAR(30) NOT NULL DEFAULT 'NONE',
    ADD COLUMN allocated_position_percent INT NOT NULL DEFAULT 0,
    ADD COLUMN entry_quality_score INT NOT NULL DEFAULT 0,
    ADD COLUMN last_scale_in_at TIMESTAMP(6) NULL;

-- Existing pre-migration OPEN positions were created before staged allocation existed.
-- Treat them as fully allocated so deployment cannot accidentally scale into them.
UPDATE wallet_managed_position
SET entry_stage = 'LEGACY_FULL',
    allocated_position_percent = 100
WHERE status = 'OPEN'
  AND quantity > 0;


-- Baseline section V47__create_debug_price_move_monitor.sql
CREATE TABLE price_move_monitor_settings (
    id BIGINT NOT NULL PRIMARY KEY,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    minimum_move_percent DECIMAL(12,6) NOT NULL DEFAULT 0.300000,
    window_minutes INT NOT NULL DEFAULT 30,
    retention_days INT NOT NULL DEFAULT 7,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

INSERT INTO price_move_monitor_settings (id, enabled, minimum_move_percent, window_minutes, retention_days)
VALUES (1, 1, 0.300000, 30, 7);

CREATE TABLE price_move_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(30) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    start_time TIMESTAMP(6) NOT NULL,
    end_time TIMESTAMP(6) NOT NULL,
    start_price DECIMAL(30,12) NOT NULL,
    end_price DECIMAL(30,12) NOT NULL,
    change_percent DECIMAL(20,8) NOT NULL,
    duration_seconds BIGINT NOT NULL,
    review_status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_price_move_event_symbol_time (symbol, end_time),
    INDEX idx_price_move_event_status_time (review_status, end_time)
);


-- Baseline section V48__upgrade_debug_price_move_monitor_to_tracker.sql
-- DEBUG ONLY: Upgrade the V47 threshold/tick monitor into a stateful market move tracker.
-- This table remains isolated from signal generation, execution and wallet logic.

ALTER TABLE price_move_monitor_settings
    ADD COLUMN minimum_duration_minutes INT NOT NULL DEFAULT 1 AFTER window_minutes,
    ADD COLUMN retracement_close_percent DECIMAL(12,6) NOT NULL DEFAULT 30.000000 AFTER minimum_duration_minutes,
    ADD COLUMN cooldown_minutes INT NOT NULL DEFAULT 10 AFTER retracement_close_percent;

UPDATE price_move_monitor_settings
SET minimum_duration_minutes = 1,
    retracement_close_percent = 30.000000,
    cooldown_minutes = 10
WHERE id = 1;

-- V47 rows were threshold-tick events and are intentionally cleared so the
-- new week starts with only one-row-per-completed-move tracker semantics.
DELETE FROM price_move_event;


-- Baseline section V49__filter_debug_tracker_to_medium_high_moves.sql
ALTER TABLE price_move_event
    ADD COLUMN importance_level VARCHAR(10) NULL AFTER duration_seconds;

UPDATE price_move_event
SET importance_level = CASE
    WHEN ABS(change_percent) >= 1.500000 THEN 'HIGH'
    WHEN ABS(change_percent) >= 0.750000 THEN 'MEDIUM'
    ELSE 'LOW'
END;

DELETE FROM price_move_event
WHERE duration_seconds <= 300
   OR ABS(change_percent) < 0.750000;

ALTER TABLE price_move_event
    MODIFY COLUMN importance_level VARCHAR(10) NOT NULL;

UPDATE price_move_monitor_settings
SET minimum_duration_minutes = GREATEST(minimum_duration_minutes, 6)
WHERE id = 1;


-- Baseline section V50__create_ai_regression_test_framework.sql
CREATE TABLE analysis_test_run (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    test_name VARCHAR(150) NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    start_time TIMESTAMP(6) NOT NULL,
    end_time TIMESTAMP(6) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    progress_percent INT NOT NULL DEFAULT 0,
    current_step VARCHAR(255) NULL,
    source_signal_count INT NOT NULL DEFAULT 0,
    replay_signal_count INT NOT NULL DEFAULT 0,
    neutralized_original_bearish_count INT NOT NULL DEFAULT 0,
    corrected_hard_reversal_count INT NOT NULL DEFAULT 0,
    historical_hard_reversal_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(2000) NULL,
    started_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_analysis_test_run_created (created_at),
    INDEX idx_analysis_test_run_symbol_time (symbol, start_time, end_time)
);

CREATE TABLE analysis_test_signal (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    test_run_id BIGINT NOT NULL,
    source_signal_id BIGINT NULL,
    symbol VARCHAR(30) NOT NULL,
    interval_code VARCHAR(10) NOT NULL,
    candle_open_time TIMESTAMP(6) NULL,
    generated_at TIMESTAMP(6) NOT NULL,
    latest_price DECIMAL(30,12) NULL,
    original_decision VARCHAR(30) NULL,
    final_decision VARCHAR(30) NULL,
    execution_effective_decision VARCHAR(30) NULL,
    total_score INT NULL,
    confidence_score INT NULL,
    trend_score INT NULL,
    volume_score INT NULL,
    momentum_score INT NULL,
    decision_authority_corrected TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_analysis_test_signal_run FOREIGN KEY (test_run_id) REFERENCES analysis_test_run(id) ON DELETE CASCADE,
    INDEX idx_analysis_test_signal_run_time (test_run_id, generated_at),
    INDEX idx_analysis_test_signal_run_interval (test_run_id, interval_code)
);

CREATE TABLE execution_opportunity_test (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    test_run_id BIGINT NOT NULL,
    source_signal_id BIGINT NULL,
    symbol VARCHAR(30) NOT NULL,
    generated_at TIMESTAMP(6) NOT NULL,
    current_final_decision VARCHAR(30) NULL,
    current_original_decision VARCHAR(30) NULL,
    five_minute_decision VARCHAR(30) NULL,
    one_hour_decision VARCHAR(30) NULL,
    old_hard_bearish_reversal TINYINT(1) NOT NULL DEFAULT 0,
    corrected_hard_bearish_reversal TINYINT(1) NOT NULL DEFAULT 0,
    decision_code VARCHAR(80) NULL,
    decision_explanation VARCHAR(2000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_execution_opportunity_test_run FOREIGN KEY (test_run_id) REFERENCES analysis_test_run(id) ON DELETE CASCADE,
    INDEX idx_execution_opportunity_test_run_time (test_run_id, generated_at)
);

CREATE TABLE analysis_test_result (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    test_run_id BIGINT NOT NULL,
    candles_1m INT NOT NULL DEFAULT 0,
    signals_1m_historical INT NOT NULL DEFAULT 0,
    replayable_1m_events INT NOT NULL DEFAULT 0,
    candles_5m INT NOT NULL DEFAULT 0,
    signals_5m_historical INT NOT NULL DEFAULT 0,
    replayable_5m_events INT NOT NULL DEFAULT 0,
    candles_1h INT NOT NULL DEFAULT 0,
    signals_1h_historical INT NOT NULL DEFAULT 0,
    replayable_1h_events INT NOT NULL DEFAULT 0,
    decision_authority_corrections INT NOT NULL DEFAULT 0,
    old_hard_bearish_reversals INT NOT NULL DEFAULT 0,
    corrected_hard_bearish_reversals INT NOT NULL DEFAULT 0,
    cadence_path_passed TINYINT(1) NOT NULL DEFAULT 0,
    decision_authority_passed TINYINT(1) NOT NULL DEFAULT 0,
    test_passed TINYINT(1) NOT NULL DEFAULT 0,
    notes VARCHAR(2000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_analysis_test_result_run FOREIGN KEY (test_run_id) REFERENCES analysis_test_run(id) ON DELETE CASCADE,
    UNIQUE KEY uk_analysis_test_result_run (test_run_id)
);


-- Baseline section V51__upgrade_regression_to_fresh_signal_replay.sql
ALTER TABLE analysis_test_signal
    ADD COLUMN replay_generated TINYINT(1) NOT NULL DEFAULT 0 AFTER source_signal_id,
    ADD COLUMN generation_error VARCHAR(1000) NULL AFTER decision_authority_corrected;

ALTER TABLE analysis_test_run
    ADD COLUMN generated_signal_count INT NOT NULL DEFAULT 0 AFTER replay_signal_count,
    ADD COLUMN generated_buy_count INT NOT NULL DEFAULT 0 AFTER generated_signal_count,
    ADD COLUMN generated_watch_count INT NOT NULL DEFAULT 0 AFTER generated_buy_count,
    ADD COLUMN generated_sell_count INT NOT NULL DEFAULT 0 AFTER generated_watch_count,
    ADD COLUMN generated_strong_sell_count INT NOT NULL DEFAULT 0 AFTER generated_sell_count;

ALTER TABLE analysis_test_result
    ADD COLUMN generated_signals_1m INT NOT NULL DEFAULT 0 AFTER replayable_1m_events,
    ADD COLUMN generated_buys_1m INT NOT NULL DEFAULT 0 AFTER generated_signals_1m,
    ADD COLUMN generated_signals_5m INT NOT NULL DEFAULT 0 AFTER replayable_5m_events,
    ADD COLUMN generated_buys_5m INT NOT NULL DEFAULT 0 AFTER generated_signals_5m,
    ADD COLUMN generated_signals_1h INT NOT NULL DEFAULT 0 AFTER replayable_1h_events,
    ADD COLUMN generated_buys_1h INT NOT NULL DEFAULT 0 AFTER generated_signals_1h,
    ADD COLUMN generated_signal_errors INT NOT NULL DEFAULT 0 AFTER generated_buys_1h;


-- Baseline section V52__add_full_shadow_production_replay.sql
ALTER TABLE analysis_test_run
    ADD COLUMN initial_test_capital DECIMAL(20,8) NOT NULL DEFAULT 10000.00000000 AFTER end_time,
    ADD COLUMN simulated_trade_count INT NOT NULL DEFAULT 0 AFTER generated_strong_sell_count,
    ADD COLUMN simulated_win_count INT NOT NULL DEFAULT 0 AFTER simulated_trade_count,
    ADD COLUMN simulated_loss_count INT NOT NULL DEFAULT 0 AFTER simulated_win_count,
    ADD COLUMN simulated_realized_pnl DECIMAL(20,8) NOT NULL DEFAULT 0 AFTER simulated_loss_count,
    ADD COLUMN simulated_final_wallet DECIMAL(20,8) NOT NULL DEFAULT 0 AFTER simulated_realized_pnl;
ALTER TABLE execution_opportunity_test
    ADD COLUMN replay_stage VARCHAR(30) NULL AFTER generated_at,
    ADD COLUMN evidence_count INT NOT NULL DEFAULT 0 AFTER replay_stage,
    ADD COLUMN buy_count INT NOT NULL DEFAULT 0 AFTER evidence_count,
    ADD COLUMN watch_count INT NOT NULL DEFAULT 0 AFTER buy_count,
    ADD COLUMN neutral_count INT NOT NULL DEFAULT 0 AFTER watch_count,
    ADD COLUMN bearish_count INT NOT NULL DEFAULT 0 AFTER neutral_count,
    ADD COLUMN evidence_score INT NOT NULL DEFAULT 0 AFTER bearish_count,
    ADD COLUMN opportunity_health INT NOT NULL DEFAULT 0 AFTER evidence_score,
    ADD COLUMN recommended_position_percent INT NOT NULL DEFAULT 0 AFTER opportunity_health;
CREATE TABLE wallet_execution_test (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, test_run_id BIGINT NOT NULL, symbol VARCHAR(30) NOT NULL,
 side VARCHAR(10) NOT NULL, execution_time TIMESTAMP(6) NOT NULL, execution_price DECIMAL(30,12) NOT NULL,
 quantity DECIMAL(30,12) NOT NULL, notional_usdt DECIMAL(20,8) NOT NULL, position_percent INT NOT NULL DEFAULT 0,
 signal_interval VARCHAR(10) NULL, signal_decision VARCHAR(30) NULL, execution_source VARCHAR(60) NULL,
 execution_code VARCHAR(100) NULL, execution_reason VARCHAR(2000) NULL, realized_pnl_usdt DECIMAL(20,8) NULL,
 realized_pnl_percent DECIMAL(20,8) NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_wallet_execution_test_run FOREIGN KEY (test_run_id) REFERENCES analysis_test_run(id) ON DELETE CASCADE,
 INDEX idx_wallet_execution_test_run_time (test_run_id, execution_time));
CREATE TABLE wallet_position_test (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, test_run_id BIGINT NOT NULL, symbol VARCHAR(30) NOT NULL,
 status VARCHAR(20) NOT NULL, entry_time TIMESTAMP(6) NOT NULL, entry_price DECIMAL(30,12) NOT NULL,
 quantity DECIMAL(30,12) NOT NULL, total_cost_usdt DECIMAL(20,8) NOT NULL, position_percent INT NOT NULL,
 stop_loss_usdt DECIMAL(30,12) NULL, take_profit_usdt DECIMAL(30,12) NULL, highest_price_usdt DECIMAL(30,12) NULL,
 profit_lock_active TINYINT(1) NOT NULL DEFAULT 0, profit_lock_price_usdt DECIMAL(30,12) NULL,
 exit_time TIMESTAMP(6) NULL, exit_price DECIMAL(30,12) NULL, exit_reason VARCHAR(100) NULL,
 exit_explanation VARCHAR(2000) NULL, realized_pnl_usdt DECIMAL(20,8) NULL, realized_pnl_percent DECIMAL(20,8) NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_wallet_position_test_run FOREIGN KEY (test_run_id) REFERENCES analysis_test_run(id) ON DELETE CASCADE,
 INDEX idx_wallet_position_test_run_time (test_run_id, entry_time));
ALTER TABLE analysis_test_result
    ADD COLUMN simulated_trades INT NOT NULL DEFAULT 0 AFTER generated_signal_errors,
    ADD COLUMN simulated_wins INT NOT NULL DEFAULT 0 AFTER simulated_trades,
    ADD COLUMN simulated_losses INT NOT NULL DEFAULT 0 AFTER simulated_wins,
    ADD COLUMN simulated_realized_pnl DECIMAL(20,8) NOT NULL DEFAULT 0 AFTER simulated_losses,
    ADD COLUMN simulated_final_wallet DECIMAL(20,8) NOT NULL DEFAULT 0 AFTER simulated_realized_pnl;


-- Baseline section V53__add_regression_failure_diagnostics.sql
ALTER TABLE analysis_test_run
    ADD COLUMN failure_step VARCHAR(255) NULL AFTER error_message,
    ADD COLUMN failure_exception VARCHAR(500) NULL AFTER failure_step,
    ADD COLUMN failure_root_cause VARCHAR(1000) NULL AFTER failure_exception,
    ADD COLUMN failure_stack_trace LONGTEXT NULL AFTER failure_root_cause;


-- Baseline section V54__persist_debug_monitor_symbols.sql
ALTER TABLE price_move_monitor_settings
    ADD COLUMN selected_symbols VARCHAR(1000) NOT NULL DEFAULT 'BNBUSDT' AFTER retention_days;

UPDATE price_move_monitor_settings
SET selected_symbols = 'BNBUSDT'
WHERE selected_symbols IS NULL OR TRIM(selected_symbols) = '';


-- Baseline section V55__add_position_management_replay_trace.sql
CREATE TABLE position_management_test (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    test_run_id BIGINT NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    generated_at TIMESTAMP(6) NOT NULL,
    action_code VARCHAR(60) NOT NULL,
    current_price DECIMAL(30,12) NULL,
    old_take_profit DECIMAL(30,12) NULL,
    new_take_profit DECIMAL(30,12) NULL,
    highest_price DECIMAL(30,12) NULL,
    profit_lock_active TINYINT(1) NOT NULL DEFAULT 0,
    profit_lock_price DECIMAL(30,12) NULL,
    explanation VARCHAR(2000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_position_management_test_run FOREIGN KEY (test_run_id) REFERENCES analysis_test_run(id) ON DELETE CASCADE,
    INDEX idx_position_management_test_run_time (test_run_id, generated_at)
);


-- Baseline section V56__add_proven_analyzed_trades.sql
CREATE TABLE proven_analyzed_trade (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_test_run_id BIGINT NULL,
    source_trade_id BIGINT NULL,
    symbol VARCHAR(30) NOT NULL,
    entry_time TIMESTAMP(6) NOT NULL,
    entry_price DECIMAL(30,12) NOT NULL,
    exit_time TIMESTAMP(6) NULL,
    exit_price DECIMAL(30,12) NULL,
    exit_reason VARCHAR(100) NULL,
    realized_pnl_usdt DECIMAL(20,8) NULL,
    realized_pnl_percent DECIMAL(20,8) NULL,
    position_percent INT NOT NULL DEFAULT 0,
    marked_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_proven_analyzed_trade_source (source_test_run_id, source_trade_id),
    INDEX idx_proven_analyzed_trade_symbol_time (symbol, entry_time)
);


-- Baseline section V57__archive_regression_test_runs.sql
CREATE TABLE regression_test_archive_batch (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_test_run_id BIGINT NOT NULL,
    test_name VARCHAR(150) NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    start_time TIMESTAMP(6) NOT NULL,
    end_time TIMESTAMP(6) NOT NULL,
    source_status VARCHAR(30) NOT NULL,
    archive_reason VARCHAR(255) NULL,
    archived_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_regression_archive_source_run (source_test_run_id),
    INDEX idx_regression_archive_symbol_time (symbol, start_time, end_time)
);

CREATE TABLE analysis_test_run_archive LIKE analysis_test_run;
ALTER TABLE analysis_test_run_archive MODIFY id BIGINT NOT NULL, DROP PRIMARY KEY,
    ADD COLUMN archive_batch_id BIGINT NOT NULL FIRST,
    ADD PRIMARY KEY (archive_batch_id, id), ADD INDEX idx_analysis_test_run_archive_source (id);

CREATE TABLE analysis_test_signal_archive LIKE analysis_test_signal;
ALTER TABLE analysis_test_signal_archive MODIFY id BIGINT NOT NULL, DROP PRIMARY KEY,
    ADD COLUMN archive_batch_id BIGINT NOT NULL FIRST,
    ADD PRIMARY KEY (archive_batch_id, id), ADD INDEX idx_analysis_test_signal_archive_run (archive_batch_id, test_run_id, generated_at);

CREATE TABLE execution_opportunity_test_archive LIKE execution_opportunity_test;
ALTER TABLE execution_opportunity_test_archive MODIFY id BIGINT NOT NULL, DROP PRIMARY KEY,
    ADD COLUMN archive_batch_id BIGINT NOT NULL FIRST,
    ADD PRIMARY KEY (archive_batch_id, id), ADD INDEX idx_execution_opportunity_archive_run (archive_batch_id, test_run_id, generated_at);

CREATE TABLE analysis_test_result_archive LIKE analysis_test_result;
ALTER TABLE analysis_test_result_archive MODIFY id BIGINT NOT NULL, DROP PRIMARY KEY,
    DROP INDEX uk_analysis_test_result_run,
    ADD COLUMN archive_batch_id BIGINT NOT NULL FIRST,
    ADD PRIMARY KEY (archive_batch_id, id), ADD INDEX idx_analysis_test_result_archive_run (archive_batch_id, test_run_id);

CREATE TABLE wallet_execution_test_archive LIKE wallet_execution_test;
ALTER TABLE wallet_execution_test_archive MODIFY id BIGINT NOT NULL, DROP PRIMARY KEY,
    ADD COLUMN archive_batch_id BIGINT NOT NULL FIRST,
    ADD PRIMARY KEY (archive_batch_id, id), ADD INDEX idx_wallet_execution_archive_run (archive_batch_id, test_run_id, execution_time);

CREATE TABLE wallet_position_test_archive LIKE wallet_position_test;
ALTER TABLE wallet_position_test_archive MODIFY id BIGINT NOT NULL, DROP PRIMARY KEY,
    ADD COLUMN archive_batch_id BIGINT NOT NULL FIRST,
    ADD PRIMARY KEY (archive_batch_id, id), ADD INDEX idx_wallet_position_archive_run (archive_batch_id, test_run_id, entry_time);

CREATE TABLE position_management_test_archive LIKE position_management_test;
ALTER TABLE position_management_test_archive MODIFY id BIGINT NOT NULL, DROP PRIMARY KEY,
    ADD COLUMN archive_batch_id BIGINT NOT NULL FIRST,
    ADD PRIMARY KEY (archive_batch_id, id), ADD INDEX idx_position_management_archive_run (archive_batch_id, test_run_id, generated_at);


-- Baseline section V58__optimize_dashboard_candle_reads.sql
-- Dashboard symbol/timeframe switches repeatedly request the latest closed candles.
-- Include `closed` in the composite index so MySQL can satisfy the filter and
-- descending open_time traversal from one index instead of combining indexes.
-- Read-performance only; no trading data or decision logic is changed.
CREATE INDEX idx_candle_dashboard_closed_time
    ON candle (symbol, interval_code, closed, open_time);


-- Baseline section V59__upgrade_market_catcher_8h_blame.sql
ALTER TABLE price_move_event
    ADD COLUMN block_start_time TIMESTAMP(6) NULL AFTER direction,
    ADD COLUMN block_end_time TIMESTAMP(6) NULL AFTER block_start_time,
    ADD COLUMN detection_window VARCHAR(10) NULL AFTER duration_seconds,
    ADD COLUMN outcome_status VARCHAR(40) NOT NULL DEFAULT 'PENDING' AFTER importance_level,
    ADD COLUMN blame_required TINYINT(1) NOT NULL DEFAULT 0 AFTER outcome_status,
    ADD COLUMN blame_reviewed TINYINT(1) NOT NULL DEFAULT 0 AFTER blame_required,
    ADD COLUMN blame_code VARCHAR(100) NULL AFTER blame_reviewed,
    ADD COLUMN blame_explanation VARCHAR(2000) NULL AFTER blame_code,
    ADD COLUMN best_signal_id BIGINT NULL AFTER blame_explanation,
    ADD COLUMN best_signal_decision VARCHAR(30) NULL AFTER best_signal_id,
    ADD COLUMN best_signal_score INT NULL AFTER best_signal_decision,
    ADD COLUMN trade_id BIGINT NULL AFTER best_signal_score;

CREATE INDEX idx_price_move_event_blame ON price_move_event (blame_required, blame_reviewed, end_time);
CREATE INDEX idx_price_move_event_block ON price_move_event (symbol, block_start_time, direction);

ALTER TABLE price_move_event ADD UNIQUE KEY uk_price_move_event_block_direction (symbol, block_start_time, direction);


-- Baseline section V60__archive_proven_trade_legs.sql
CREATE TABLE proven_trade_leg_archive (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    proven_trade_id BIGINT NOT NULL,
    source_test_run_id BIGINT NULL,
    source_trade_id BIGINT NULL,
    symbol VARCHAR(30) NOT NULL,
    side VARCHAR(10) NOT NULL,
    execution_time TIMESTAMP(6) NOT NULL,
    execution_price DECIMAL(30,12) NOT NULL,
    exit_reason VARCHAR(100) NULL,
    realized_pnl_usdt DECIMAL(20,8) NULL,
    realized_pnl_percent DECIMAL(20,8) NULL,
    archived_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_proven_trade_leg_archive (proven_trade_id, side),
    INDEX idx_proven_trade_leg_archive_symbol_time (symbol, execution_time),
    INDEX idx_proven_trade_leg_archive_source (source_test_run_id, source_trade_id)
);


-- Baseline section V61__create_production_exit_audit.sql
-- FIX-028: immutable production exit audit. This table is diagnostic only; it does not
-- participate in BUY/SELL decisions. It preserves the true terminal trigger separately
-- from the market-context signal that happened to be current when the exit executed.
CREATE TABLE production_exit_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    paper_position_id BIGINT NULL,
    wallet_position_id BIGINT NULL,
    symbol VARCHAR(30) NOT NULL,
    close_trigger VARCHAR(40) NOT NULL,
    source_signal_id BIGINT NULL,
    source_signal_decision VARCHAR(30) NULL,
    source_signal_original_decision VARCHAR(30) NULL,
    position_analysis_id BIGINT NULL,
    position_recommendation VARCHAR(20) NULL,
    entry_price_usdt DECIMAL(30,12) NULL,
    exit_price_usdt DECIMAL(30,12) NOT NULL,
    stop_loss_usdt DECIMAL(30,12) NULL,
    take_profit_usdt DECIMAL(30,12) NULL,
    close_explanation VARCHAR(2000) NULL,
    audited_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_production_exit_audit_paper_position (paper_position_id),
    KEY idx_production_exit_audit_symbol_time (symbol, audited_at),
    KEY idx_production_exit_audit_source_signal (source_signal_id),
    KEY idx_production_exit_audit_wallet_position (wallet_position_id)
);


-- Baseline section V62__create_user_crypto_account_configuration.sql
-- FIX-031: keep exchange/account configuration isolated per authenticated app_user.
-- Trading signals/strategy remain shared; this table only stores the user's execution-account boundary.
CREATE TABLE crypto_account_configuration (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    exchange_code VARCHAR(20) NOT NULL DEFAULT 'BINANCE',
    account_label VARCHAR(100) NOT NULL DEFAULT 'Primary account',
    execution_mode VARCHAR(20) NOT NULL DEFAULT 'PAPER',
    api_key_encrypted TEXT NULL,
    api_secret_encrypted TEXT NULL,
    api_key_hint VARCHAR(32) NULL,
    max_order_usdt DECIMAL(20,8) NOT NULL DEFAULT 10.00000000,
    max_total_exposure_usdt DECIMAL(20,8) NOT NULL DEFAULT 50.00000000,
    max_open_positions INT NOT NULL DEFAULT 3,
    max_daily_loss_usdt DECIMAL(20,8) NOT NULL DEFAULT 10.00000000,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_crypto_account_user_exchange UNIQUE (user_id, exchange_code),
    CONSTRAINT fk_crypto_account_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);


-- Baseline section V63__add_binance_live_micro_safety_controls.sql
-- FIX-032: user-scoped LIVE_MICRO safety configuration only.
-- These columns do not change signal generation, Paper Wallet execution or Replay behavior.
ALTER TABLE crypto_account_configuration
    ADD COLUMN safety_enabled TINYINT(1) NOT NULL DEFAULT 1 AFTER max_daily_loss_usdt,
    ADD COLUMN consecutive_loss_pause_count INT NOT NULL DEFAULT 3 AFTER safety_enabled,
    ADD COLUMN consecutive_loss_pause_minutes INT NOT NULL DEFAULT 120 AFTER consecutive_loss_pause_count,
    ADD COLUMN consecutive_loss_manual_stop_count INT NOT NULL DEFAULT 4 AFTER consecutive_loss_pause_minutes,
    ADD COLUMN rolling_loss_window_minutes INT NOT NULL DEFAULT 240 AFTER consecutive_loss_manual_stop_count,
    ADD COLUMN max_rolling_loss_usdt DECIMAL(20,8) NOT NULL DEFAULT 10.00000000 AFTER rolling_loss_window_minutes,
    ADD COLUMN same_symbol_loss_count INT NOT NULL DEFAULT 2 AFTER max_rolling_loss_usdt,
    ADD COLUMN same_symbol_quarantine_minutes INT NOT NULL DEFAULT 240 AFTER same_symbol_loss_count,
    ADD COLUMN max_slippage_percent DECIMAL(10,4) NOT NULL DEFAULT 0.3000 AFTER same_symbol_quarantine_minutes,
    ADD COLUMN binance_failure_pause_count INT NOT NULL DEFAULT 2 AFTER max_slippage_percent;

-- FIX-032 requested LIVE_MICRO default daily circuit breaker. Existing FIX-031 rows still
-- contain the original untouched 10 USDT placeholder, so promote only that original default.
UPDATE crypto_account_configuration
SET max_daily_loss_usdt = 20.00000000
WHERE max_daily_loss_usdt = 10.00000000;


-- Baseline section V64__persist_live_price_events_for_exact_replay.sql
-- FIX-052: persist the canonical 1m Binance live-price stream used by Production
-- position protection. Replay can then consume the same observed prices in the
-- same timestamp order instead of approximating TP/SL/profit-lock from candle closes.
CREATE TABLE IF NOT EXISTS market_price_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(32) NOT NULL,
    observed_at TIMESTAMP(6) NOT NULL,
    price DECIMAL(30,12) NOT NULL,
    source VARCHAR(64) NOT NULL DEFAULT 'BINANCE_KLINE_LIVE_CLOSE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_market_price_event_symbol_time (symbol, observed_at)
);


-- Baseline section V65__persist_position_management_events.sql
-- FIX-053: Persist management-path events needed by the active-position dashboard.
-- Production timestamps remain UTC. The browser is responsible for KSA/local display.
CREATE TABLE IF NOT EXISTS position_management_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    wallet_position_id BIGINT NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    old_value_usdt DECIMAL(30,12) NULL,
    new_value_usdt DECIMAL(30,12) NULL,
    market_price_usdt DECIMAL(30,12) NULL,
    reason VARCHAR(2000) NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_position_management_event_position_time (wallet_position_id, occurred_at),
    KEY idx_position_management_event_symbol_time (symbol, occurred_at),
    CONSTRAINT fk_position_management_event_position
        FOREIGN KEY (wallet_position_id) REFERENCES wallet_managed_position(id) ON DELETE CASCADE
);


-- Baseline section V66__execution_opportunity_entry_price_memory.sql
-- FIX-055: preserve the real price anchor of long-lived BUY opportunities.
-- MySQL/Binance timestamps remain UTC; this migration adds price evidence only.
ALTER TABLE execution_opportunity
    ADD COLUMN anchor_entry_price DECIMAL(30,12) NULL AFTER last_evidence_at,
    ADD COLUMN best_entry_price DECIMAL(30,12) NULL AFTER anchor_entry_price;


-- Baseline section V67__fresh_execution_price_authority.sql
-- FIX-056: keep decision-time price separate from execution-time fill price.
-- All timestamps remain UTC in MySQL; frontend/local conversion is presentation-only.
ALTER TABLE wallet_trade
    ADD COLUMN decision_price_usdt DECIMAL(30,12) NULL AFTER price_usdt,
    ADD COLUMN execution_price_observed_at TIMESTAMP(6) NULL AFTER decision_price_usdt;


-- Baseline section V68__create_regression_investigation_queue.sql
-- FIX-065: persistent Investigation Queue for repeatable replay cases.
-- Stores only replay/test metadata. It never points at or mutates production wallet state.
CREATE TABLE IF NOT EXISTS regression_investigation_case (
    id BIGINT NOT NULL AUTO_INCREMENT,
    case_name VARCHAR(150) NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    start_time TIMESTAMP(6) NOT NULL,
    end_time TIMESTAMP(6) NOT NULL,
    wallet_id BIGINT NULL,
    expected_action VARCHAR(40) NULL,
    notes VARCHAR(1000) NULL,
    last_run_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_regression_investigation_symbol_time (symbol, start_time),
    KEY idx_regression_investigation_last_run (last_run_id),
    CONSTRAINT fk_regression_investigation_last_run
        FOREIGN KEY (last_run_id) REFERENCES analysis_test_run(id) ON DELETE SET NULL
);


-- Baseline section V69__create_trade_signal_test_parity.sql
-- FIX-069: Replay keeps a full production-shaped TradeSignal snapshot for each generated signal.
-- The table intentionally clones production trade_signal first, then adds only replay metadata.
CREATE TABLE trade_signal_test LIKE trade_signal;
-- Production enforces one signal per symbol/interval/candle. Replay must allow the same
-- candle to be regenerated in multiple independent test runs, so run-scoping replaces
-- the production-only uniqueness boundary.
ALTER TABLE trade_signal_test DROP INDEX uk_trade_signal_symbol_interval_candle;
ALTER TABLE trade_signal_test
    ADD COLUMN test_run_id BIGINT NOT NULL AFTER id,
    ADD COLUMN source_signal_id BIGINT NULL AFTER test_run_id,
    ADD COLUMN replay_generated TINYINT(1) NOT NULL DEFAULT 1 AFTER source_signal_id,
    ADD COLUMN generation_error VARCHAR(1000) NULL AFTER replay_generated,
    ADD CONSTRAINT fk_trade_signal_test_run FOREIGN KEY (test_run_id) REFERENCES analysis_test_run(id) ON DELETE CASCADE,
    ADD INDEX idx_trade_signal_test_run_time (test_run_id, generated_at),
    ADD INDEX idx_trade_signal_test_run_interval (test_run_id, interval_code);

-- Safety archive remains backend-only even though the archive UI is removed.
-- Composite key permits test-table IDs to restart from 1 after Clear Data.
CREATE TABLE trade_signal_test_archive LIKE trade_signal_test;
ALTER TABLE trade_signal_test_archive
    DROP PRIMARY KEY,
    MODIFY COLUMN id BIGINT NOT NULL,
    ADD COLUMN archive_batch_id BIGINT NOT NULL FIRST,
    ADD PRIMARY KEY (archive_batch_id, id),
    ADD INDEX idx_trade_signal_test_archive_run_time (archive_batch_id, test_run_id, generated_at);


-- Baseline section V70__protect_signal_persistence_and_health.sql
-- FIX-072: the assembled TradeSignal explanation reached VARCHAR(2000) and caused
-- Production INSERT failures. Keep full diagnostics without allowing text length to drop a signal.
ALTER TABLE trade_signal
    MODIFY COLUMN explanation TEXT NULL;

-- Replay and its backend archive must accept the same production-shaped explanation payload.
ALTER TABLE trade_signal_test
    MODIFY COLUMN explanation TEXT NULL;

ALTER TABLE trade_signal_test_archive
    MODIFY COLUMN explanation TEXT NULL;


-- Baseline section V71__add_regression_recovery_heartbeat.sql
-- FIX-073: durable replay heartbeat distinguishes a live async worker from an orphaned
-- PENDING/RUNNING row after a JVM restart. It is isolated test metadata only.
ALTER TABLE analysis_test_run
    ADD COLUMN heartbeat_at TIMESTAMP(6) NULL AFTER current_step;


-- Baseline section V72__fix_091_decision_authority_diagnostics.sql
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


-- Baseline section V73__fix_109_replay_parity_metadata.sql
-- FIX-109: make Replay's parity contract visible/auditable per run.
ALTER TABLE analysis_test_run
    ADD COLUMN replay_price_mode VARCHAR(40) NULL,
    ADD COLUMN replay_logic_mode VARCHAR(40) NULL;


-- Baseline section V74__order_book_snapshot_persistence.sql
-- FIX-112C: persist the normalized Order Book evidence used by Production so
-- historical Replay can evaluate the same observations at the same as-of time.
CREATE TABLE order_book_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(30) NOT NULL,
    observed_at TIMESTAMP(6) NOT NULL,
    best_bid DECIMAL(30,12),
    best_ask DECIMAL(30,12),
    spread_bps DECIMAL(20,8),
    bid_depth DECIMAL(30,12),
    ask_depth DECIMAL(30,12),
    depth_imbalance DECIMAL(20,8),
    bid_wall_price DECIMAL(30,12),
    bid_wall_quantity DECIMAL(30,12),
    ask_wall_price DECIMAL(30,12),
    ask_wall_quantity DECIMAL(30,12),
    collection_latency_ms INT,
    source VARCHAR(30) NOT NULL DEFAULT 'BINANCE_LIVE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_ob_symbol_observed (symbol, observed_at)
);


-- Baseline section V75__catching_market_summary_lookup_index.sql
-- FIX-116: Catching Market is read-only diagnostics. The summary page filters by end_time before
-- grouping by symbol/direction/window; the original indexes lead with symbol or review_status and
-- cannot efficiently serve the unfiltered 1h/4h/24h page. This index changes no trading data/rules.
CREATE INDEX idx_price_move_event_summary_time
    ON price_move_event (end_time, importance_level, symbol, direction, detection_window, start_time, id);


-- Baseline section V76__system_health_trade_signal_covering_index.sql
-- FIX-116A: System Health groups the last seven days by strategy/regime and generated KSA day.
-- EXPLAIN ANALYZE on Production showed the existing generated_at index still required expensive
-- base-table lookups. Cover the only columns required by this read-only diagnostic aggregation.
-- This index does not change signal generation, execution, Replay, or any trading decision.
CREATE INDEX idx_trade_signal_health_strategy_regime
    ON trade_signal (generated_at, selected_strategy, market_regime);


-- Baseline section V77__persist_replay_stage_timings.sql
-- FIX-11J: Replay-only persisted performance diagnostics.
-- Nullable BIGINT nanosecond fields preserve old runs as "timing unavailable" and do not
-- modify any Production trading table, Replay decision data, thresholds, ordering or lineage.
ALTER TABLE analysis_test_run
    ADD COLUMN timing_load_historical_ns BIGINT NULL,
    ADD COLUMN timing_verify_event_resolution_ns BIGINT NULL,
    ADD COLUMN timing_build_replay_dataset_ns BIGINT NULL,
    ADD COLUMN timing_generate_fresh_signals_ns BIGINT NULL,
    ADD COLUMN timing_shadow_execution_ns BIGINT NULL,
    ADD COLUMN timing_parity_comparison_ns BIGINT NULL,
    ADD COLUMN timing_total_ns BIGINT NULL;

-- analysis_test_run_archive was created from the earlier run schema in V57, while V71/V73
-- later added live-run metadata. Keep the archive shape aligned with current analysis_test_run
-- because RegressionTestService intentionally archives with INSERT ... SELECT ?, r.*.
ALTER TABLE analysis_test_run_archive
    ADD COLUMN heartbeat_at TIMESTAMP(6) NULL AFTER current_step,
    ADD COLUMN replay_price_mode VARCHAR(40) NULL,
    ADD COLUMN replay_logic_mode VARCHAR(40) NULL,
    ADD COLUMN timing_load_historical_ns BIGINT NULL,
    ADD COLUMN timing_verify_event_resolution_ns BIGINT NULL,
    ADD COLUMN timing_build_replay_dataset_ns BIGINT NULL,
    ADD COLUMN timing_generate_fresh_signals_ns BIGINT NULL,
    ADD COLUMN timing_shadow_execution_ns BIGINT NULL,
    ADD COLUMN timing_parity_comparison_ns BIGINT NULL,
    ADD COLUMN timing_total_ns BIGINT NULL;


-- Baseline section V78__fix_11k_replay_defensive_risk_observations.sql
-- FIX-11K Phase A: replay-only observation persistence.
-- This table has no foreign key or trigger into Production wallet/signal tables and cannot execute trades.
CREATE TABLE defensive_risk_reduction_observation_test (
    id BIGINT NOT NULL AUTO_INCREMENT,
    test_run_id BIGINT NOT NULL,
    position_test_id BIGINT NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    observed_at TIMESTAMP(6) NOT NULL,
    source_signal_id BIGINT NULL,
    current_price DECIMAL(30,12) NOT NULL,
    entry_price DECIMAL(30,12) NOT NULL,
    highest_price_since_entry DECIMAL(30,12) NOT NULL,
    current_profit_percent DECIMAL(18,8) NOT NULL,
    peak_profit_percent DECIMAL(18,8) NOT NULL,
    giveback_from_peak_percent DECIMAL(18,8) NOT NULL,
    consecutive_final_1m_strong_sell INT NOT NULL,
    five_minute_signal_id BIGINT NULL,
    five_minute_original_decision VARCHAR(30) NULL,
    five_minute_final_decision VARCHAR(30) NULL,
    five_minute_confluence_status VARCHAR(30) NULL,
    one_hour_signal_id BIGINT NULL,
    one_hour_final_decision VARCHAR(30) NULL,
    observation_code VARCHAR(60) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_fix11k_observation_run_time (test_run_id, observed_at),
    KEY idx_fix11k_observation_symbol_time (symbol, observed_at)
);

CREATE TABLE defensive_risk_reduction_observation_test_archive LIKE defensive_risk_reduction_observation_test;
ALTER TABLE defensive_risk_reduction_observation_test_archive
    DROP PRIMARY KEY,
    MODIFY id BIGINT NOT NULL,
    ADD COLUMN archive_batch_id BIGINT NOT NULL FIRST,
    ADD PRIMARY KEY (archive_batch_id, id),
    ADD KEY idx_fix11k_archive_run_time (test_run_id, observed_at);


-- Baseline section V79__fix_11q_replay_one_candle_continuation_grace.sql
-- FIX-11Q: replay-only one-candle continuation grace counterfactual.
CREATE TABLE one_candle_continuation_grace_test (
    id BIGINT NOT NULL AUTO_INCREMENT,
    test_run_id BIGINT NOT NULL,
    position_test_id BIGINT NOT NULL,
    symbol VARCHAR(30) NOT NULL,
    grace_at TIMESTAMP(6) NOT NULL,
    grace_1m_signal_id BIGINT NULL,
    grace_candle_time TIMESTAMP(6) NOT NULL,
    price_at_grace DECIMAL(30,12) NOT NULL,
    entry_price DECIMAL(30,12) NOT NULL,
    take_profit_before_grace DECIMAL(30,12) NULL,
    take_profit_after_grace DECIMAL(30,12) NULL,
    current_1m_decision VARCHAR(30) NULL,
    five_minute_decision VARCHAR(30) NULL,
    one_hour_decision VARCHAR(30) NULL,
    trend_score INT NULL,
    trend_floor INT NULL,
    momentum_score INT NULL,
    momentum_floor INT NULL,
    baseline_action VARCHAR(60) NOT NULL,
    baseline_exit_price DECIMAL(30,12) NULL,
    baseline_pnl_usdt DECIMAL(30,12) NULL,
    baseline_pnl_percent DECIMAL(18,8) NULL,
    baseline_explanation TEXT NULL,
    next_1m_decision VARCHAR(30) NULL,
    next_5m_decision VARCHAR(30) NULL,
    next_1h_decision VARCHAR(30) NULL,
    variant_exit_time TIMESTAMP(6) NULL,
    variant_exit_price DECIMAL(30,12) NULL,
    variant_exit_reason VARCHAR(80) NULL,
    variant_pnl_usdt DECIMAL(30,12) NULL,
    variant_pnl_percent DECIMAL(18,8) NULL,
    pnl_delta_usdt DECIMAL(30,12) NULL,
    mfe_after_grace_percent DECIMAL(18,8) NULL,
    mae_after_grace_percent DECIMAL(18,8) NULL,
    grace_result VARCHAR(60) NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_fix11q_run_position (test_run_id, position_test_id),
    KEY idx_fix11q_run_grace (test_run_id, grace_at),
    KEY idx_fix11q_symbol_grace (symbol, grace_at)
);

CREATE TABLE one_candle_continuation_grace_test_archive LIKE one_candle_continuation_grace_test;
ALTER TABLE one_candle_continuation_grace_test_archive
    DROP PRIMARY KEY,
    DROP INDEX uk_fix11q_run_position,
    MODIFY id BIGINT NOT NULL,
    ADD COLUMN archive_batch_id BIGINT NOT NULL FIRST,
    ADD PRIMARY KEY (archive_batch_id, id),
    ADD KEY idx_fix11q_archive_run_grace (test_run_id, grace_at);


-- Baseline section V80__trade_review_to_proven_analysis.sql
-- FIX-11R: operator review/copy workflow only. No trading behavior changes.
CREATE TABLE trade_inspector_review (wallet_sell_trade_id BIGINT NOT NULL PRIMARY KEY, marked_for_review TINYINT(1) NOT NULL DEFAULT 1, marked_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), INDEX idx_trade_inspector_review_marked (marked_for_review, marked_at));
ALTER TABLE proven_analyzed_trade ADD COLUMN source_wallet_buy_trade_id BIGINT NULL AFTER source_trade_id, ADD COLUMN source_wallet_sell_trade_id BIGINT NULL AFTER source_wallet_buy_trade_id, ADD COLUMN analysis_start_time TIMESTAMP(6) NULL AFTER source_wallet_sell_trade_id, ADD COLUMN analysis_end_time TIMESTAMP(6) NULL AFTER analysis_start_time, ADD COLUMN analysis_status VARCHAR(20) NOT NULL DEFAULT 'READY' AFTER analysis_end_time, ADD UNIQUE KEY uk_proven_source_wallet_sell (source_wallet_sell_trade_id);
CREATE TABLE proven_trade_execution_point (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, proven_trade_id BIGINT NOT NULL, wallet_trade_id BIGINT NOT NULL, side VARCHAR(10) NOT NULL, execution_time TIMESTAMP(6) NOT NULL, execution_price DECIMAL(30,12) NOT NULL, quantity DECIMAL(30,12) NULL, execution_reason VARCHAR(100) NULL, sequence_no INT NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), UNIQUE KEY uk_proven_execution_wallet_trade (proven_trade_id,wallet_trade_id), INDEX idx_proven_execution_trade_time (proven_trade_id,execution_time));


-- Baseline section V81__near_tp_failure_protection.sql
-- FIX-11T: Production + Replay Near-TP Failure Protection state.
-- All state is position-scoped and persisted so restarts cannot duplicate bearish streaks
-- or forget a completed partial harvest. Missing/stale signal evidence never confirms a sell.
ALTER TABLE wallet_managed_position
    ADD COLUMN near_tp_state VARCHAR(40) NOT NULL DEFAULT 'INACTIVE' AFTER last_scale_in_at,
    ADD COLUMN near_tp_best_price DECIMAL(30,12) NULL AFTER near_tp_state,
    ADD COLUMN near_tp_bearish_streak INT NOT NULL DEFAULT 0 AFTER near_tp_best_price,
    ADD COLUMN near_tp_last_1m_signal_id BIGINT NULL AFTER near_tp_bearish_streak,
    ADD COLUMN near_tp_harvest_used TINYINT(1) NOT NULL DEFAULT 0 AFTER near_tp_last_1m_signal_id,
    ADD COLUMN near_tp_harvested_quantity DECIMAL(30,12) NULL AFTER near_tp_harvest_used;

-- Replay carries the exact same trading-rule state. The archive table must evolve in lockstep
-- because RegressionTestService archives rows with INSERT ... SELECT * semantics.
ALTER TABLE wallet_position_test
    ADD COLUMN near_tp_state VARCHAR(40) NOT NULL DEFAULT 'INACTIVE' AFTER profit_lock_price_usdt,
    ADD COLUMN near_tp_best_price DECIMAL(30,12) NULL AFTER near_tp_state,
    ADD COLUMN near_tp_bearish_streak INT NOT NULL DEFAULT 0 AFTER near_tp_best_price,
    ADD COLUMN near_tp_last_1m_signal_id BIGINT NULL AFTER near_tp_bearish_streak,
    ADD COLUMN near_tp_harvest_used TINYINT(1) NOT NULL DEFAULT 0 AFTER near_tp_last_1m_signal_id,
    ADD COLUMN near_tp_harvested_quantity DECIMAL(30,12) NULL AFTER near_tp_harvest_used;

ALTER TABLE wallet_position_test_archive
    ADD COLUMN near_tp_state VARCHAR(40) NOT NULL DEFAULT 'INACTIVE' AFTER profit_lock_price_usdt,
    ADD COLUMN near_tp_best_price DECIMAL(30,12) NULL AFTER near_tp_state,
    ADD COLUMN near_tp_bearish_streak INT NOT NULL DEFAULT 0 AFTER near_tp_best_price,
    ADD COLUMN near_tp_last_1m_signal_id BIGINT NULL AFTER near_tp_bearish_streak,
    ADD COLUMN near_tp_harvest_used TINYINT(1) NOT NULL DEFAULT 0 AFTER near_tp_last_1m_signal_id,
    ADD COLUMN near_tp_harvested_quantity DECIMAL(30,12) NULL AFTER near_tp_harvest_used;


-- Baseline section V82__fix_118_profit_lock_tp_extension_rebase.sql
-- FIX-118 behavioral phase: preserve earned Profit Lock history across TP extension
-- without letting protection calculated against the superseded TP remain executable.
ALTER TABLE wallet_managed_position
    ADD COLUMN profit_lock_state VARCHAR(40) NOT NULL DEFAULT 'INACTIVE' AFTER profit_lock_activated_at,
    ADD COLUMN profit_lock_rebase_started_at TIMESTAMP(6) NULL AFTER profit_lock_state;

-- Mandatory legacy backfill: every position that already has an earned lock remains ACTIVE.
UPDATE wallet_managed_position
SET profit_lock_state = 'ACTIVE'
WHERE profit_lock_active = 1;

-- Replay carries the same persisted lifecycle state. Archive remains schema-identical because
-- regression archival uses INSERT ... SELECT * semantics.
ALTER TABLE wallet_position_test
    ADD COLUMN profit_lock_state VARCHAR(40) NOT NULL DEFAULT 'INACTIVE' AFTER profit_lock_price_usdt,
    ADD COLUMN profit_lock_rebase_started_at TIMESTAMP(6) NULL AFTER profit_lock_state;
UPDATE wallet_position_test
SET profit_lock_state = 'ACTIVE'
WHERE profit_lock_active = 1;

ALTER TABLE wallet_position_test_archive
    ADD COLUMN profit_lock_state VARCHAR(40) NOT NULL DEFAULT 'INACTIVE' AFTER profit_lock_price_usdt,
    ADD COLUMN profit_lock_rebase_started_at TIMESTAMP(6) NULL AFTER profit_lock_state;
UPDATE wallet_position_test_archive
SET profit_lock_state = 'ACTIVE'
WHERE profit_lock_active = 1;


-- Baseline section V83__fix_122_stop_evidence_diagnostics.sql
-- FIX-122: append-only evaluation diagnostics include blocked entries and preserve
-- run identity when existing Replay tables are archived. No wallet foreign-key locks.
CREATE TABLE fix122_evaluation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    test_run_id BIGINT NULL,
    symbol VARCHAR(30) NOT NULL,
    signal_id BIGINT NULL,
    evaluated_at TIMESTAMP(6) NOT NULL,
    stage VARCHAR(40) NOT NULL,
    payload JSON NOT NULL,
    INDEX idx_fix122_run (test_run_id,id),
    INDEX idx_fix122_production (symbol,evaluated_at)
);
CREATE TABLE fix122_replay_revision (
    test_run_id BIGINT NOT NULL PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
-- Bounded latest terminal-stop lookup. This does not change signal history queries.
CREATE INDEX idx_wallet_trade_fix122_stop
    ON wallet_trade(symbol,status,side,execution_reason,executed_at,id);

-- Saved Proven evidence survives Replay purge and run-ID reuse.
ALTER TABLE proven_analyzed_trade ADD COLUMN fix122_diagnostics_json JSON NULL;


-- Baseline section V84__fix_124_protection_incidents.sql
-- FIX-124: Production incident history, independent of failed wallet transactions.
-- Exact candle lineage is retained; these rows are NOT Replay decision inputs.
CREATE TABLE fix124_protection_incident (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    symbol VARCHAR(30) NOT NULL,
    interval_code VARCHAR(10) NOT NULL,
    candle_open_time TIMESTAMP(6) NOT NULL,
    observed_at TIMESTAMP(6) NOT NULL,
    price DECIMAL(30,12) NOT NULL,
    attempts INT NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    error_message MEDIUMTEXT NULL,
    recorded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_fix124_symbol_observed (symbol, observed_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Baseline section V85__fix_126_wallet_execution_message.sql
-- FIX-126: signal #634982 BUY rolled back because its audit explanation exceeded
-- VARCHAR(1000). Preserve complete BUY/SELL explanations; do not truncate lineage.
ALTER TABLE wallet_trade
    MODIFY COLUMN execution_message MEDIUMTEXT
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL;


-- Baseline section V86__fix_127_signal_processing.sql
-- FIX-127: no historical backfill. Signal existence is not processing completion.
-- No foreign keys: ledger claims must not acquire signal/position parent locks.
CREATE TABLE signal_processing_work (
    signal_id BIGINT NOT NULL PRIMARY KEY,
    symbol VARCHAR(30) NOT NULL,
    interval_code VARCHAR(10) NOT NULL,
    candle_open_time TIMESTAMP(6) NOT NULL,
    origin VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    owner_token VARCHAR(36) NULL,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    paper_position_id BIGINT NULL,
    failure_stage VARCHAR(40) NULL,
    error_message MEDIUMTEXT NULL,
    KEY idx_fix127_due (status,next_attempt_at,signal_id),
    KEY idx_fix127_window (symbol,candle_open_time,signal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Baseline section V87__fix_129_stage_timing.sql
-- FIX-129: reporting-only measurements; no FK or lock on trading tables.
CREATE TABLE fix129_stage_timing (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 correlation_id VARCHAR(36) NOT NULL,
 symbol VARCHAR(30) NOT NULL,
 interval_code VARCHAR(10) NULL,
 candle_open_time TIMESTAMP(6) NULL,
 observed_at TIMESTAMP(6) NULL,
 block_start TIMESTAMP(6) NULL,
 stage VARCHAR(40) NOT NULL,
 started_at TIMESTAMP(6) NOT NULL,
 finished_at TIMESTAMP(6) NOT NULL,
 elapsed_ms BIGINT NOT NULL,
 outcome VARCHAR(20) NOT NULL,
 thread_name VARCHAR(100) NOT NULL,
 INDEX idx_fix129_symbol_started (symbol, started_at),
 INDEX idx_fix129_correlation (correlation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- Baseline section V88__fix_130_price_move_finalization.sql
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


-- Baseline section V89__fix_132_shared_market_delivery.sql
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


-- Baseline section V90__fix_132_delivery_exclusion_reason.sql
-- FIX-132: additive review refinement; retain V89 checksum for existing candidates.
ALTER TABLE shared_market_event_delivery ADD COLUMN eligibility_reason VARCHAR(80) NULL;


-- Baseline section V91__fix_132_explicit_cutover_ownership.sql
-- FIX-132: old READY records are ambiguous; never infer operator approval.
ALTER TABLE shared_market_consumer_state
 ADD COLUMN cutover_source VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN_LEGACY',
 ADD COLUMN approved_by VARCHAR(160) NULL,
 ADD COLUMN approved_at TIMESTAMP(6) NULL,
 ADD COLUMN approval_reference VARCHAR(255) NULL,
 ADD COLUMN approved_sequence BIGINT NULL,
 ADD COLUMN approved_cutover_at TIMESTAMP(6) NULL;
ALTER TABLE shared_market_consumer_state ALTER COLUMN status SET DEFAULT 'PENDING_CUTOVER';
UPDATE shared_market_consumer_state SET status='PENDING_CUTOVER' WHERE status='READY';
ALTER TABLE signal_processing_work ADD COLUMN source_event_id BIGINT NULL;
CREATE INDEX idx_fix132_work_event ON signal_processing_work(source_event_id);
CREATE TABLE shared_market_cutover_approval (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, symbol VARCHAR(30) NOT NULL,
 cutover_sequence BIGINT NOT NULL, cutover_at TIMESTAMP(6) NOT NULL,
 approved_by VARCHAR(160) NOT NULL, approved_at TIMESTAMP(6) NOT NULL,
 approval_reference VARCHAR(255) NOT NULL,
 UNIQUE KEY uk_fix132_approval(symbol,cutover_sequence,cutover_at,approval_reference)
) ENGINE=InnoDB;


-- Baseline section V92__fix_125_wallet_coordination.sql
-- FIX-125: persistent rows survive flat positions; locks belong to the transaction.
CREATE TABLE wallet_symbol_coordination(symbol VARCHAR(30) NOT NULL PRIMARY KEY) ENGINE=InnoDB;
INSERT IGNORE INTO wallet_symbol_coordination SELECT symbol FROM coin_configuration;
INSERT IGNORE INTO wallet_symbol_coordination SELECT DISTINCT symbol FROM wallet_managed_position;
INSERT IGNORE INTO wallet_symbol_coordination SELECT CONCAT(symbol,'USDT') FROM wallet_asset WHERE symbol<>'USDT';
-- FIX-125/134: replaces body-only serialization for wallet mutations, not analysis.
-- Also protects absent daily-statistics/cash/asset initialization across writers.
CREATE TABLE wallet_mutation_coordination(id INT NOT NULL PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO wallet_mutation_coordination VALUES(1);


-- Baseline section V93__fix_132_historical_close_deferral.sql
-- FIX-132: historical work must not reserve signal identity ahead of a fresh close.
ALTER TABLE shared_market_event_delivery ADD COLUMN analysis_not_before TIMESTAMP(6) NULL;


-- Baseline section V94__fix_132_reviewed_cutover_proposal.sql
-- FIX-132: short-lived, operator-reviewed proposals. No approvals are inferred.
CREATE TABLE shared_market_cutover_proposal (
 id VARCHAR(36) PRIMARY KEY, symbol VARCHAR(30) NOT NULL,
 operation VARCHAR(20) NOT NULL, requested_by VARCHAR(160) NOT NULL,
 base_state_hash VARCHAR(64) NOT NULL,
 cutover_sequence BIGINT NOT NULL, cutover_at TIMESTAMP(6) NOT NULL,
 expires_at TIMESTAMP(6) NOT NULL, consumed_at TIMESTAMP(6) NULL
) ENGINE=InnoDB;


-- Baseline section V95__fix_138_analysis_dispatch_ranges.sql
-- FIX-138: fixed lane + status + close-time range for live/historical discovery.
-- Distinct names from the operator-added FIX-137 indexes; do not rebuild/drop them.
CREATE INDEX idx_fix138_analysis_close
 ON shared_market_event_delivery(symbol,interval_code,phase,analysis_status,candle_close_time,symbol_sequence);
-- The independent monitor visits only timed-out RUNNING rows, never all deliveries.
CREATE INDEX idx_fix138_analysis_timeout
 ON shared_market_event_delivery(analysis_status,analysis_started_at,source_event_id);


-- Baseline section V96__fix_140_history_retirement_audit.sql
-- FIX-140: terminal retirement is distinct from successful analysis completion.
-- No UPDATE/DELETE of existing deliveries, candles, signals, or wallet work is performed by migration.
ALTER TABLE shared_market_event_delivery
 ADD COLUMN analysis_retired_at TIMESTAMP(6) NULL,
 ADD COLUMN analysis_retirement_operation VARCHAR(36) NULL,
 ADD COLUMN analysis_retirement_reason VARCHAR(255) NULL;


-- Baseline section V97__fix_141_btc_context_freshness.sql
-- FIX-141: persist BTC context freshness evidence alongside the existing btc_context_* columns.
-- Added only where the existing model cannot represent the evidence (see FIX-141 design, section 5).
ALTER TABLE trade_signal
    ADD COLUMN btc_context_candle_close_time TIMESTAMP(6) NULL AFTER btc_signal_generated_at,
    ADD COLUMN btc_context_age_seconds BIGINT NULL AFTER btc_context_candle_close_time,
    ADD COLUMN btc_context_freshness_threshold_seconds BIGINT NULL AFTER btc_context_age_seconds;


-- Baseline section V98__fix_141_reconciliation_scan_index.sql
-- FIX-141 Phase 2 review correction: SharedAnalysisDispatcher#reconcileAnalysisOnlyReviews and
-- #reconcileOwnerTimeoutReviews scan shared_market_event_delivery GLOBALLY (not per-symbol like
-- claim()), filtering by analysis_status and paging via a source_event_id cursor in
-- "ORDER BY source_event_id" order. Neither existing FIX-138 index supports that access path:
-- idx_fix138_analysis_close is led by symbol (no symbol filter in these global scans), and
-- idx_fix138_analysis_timeout is led by analysis_status but then analysis_started_at, so rows
-- are grouped by analysis_started_at before source_event_id - not sorted by source_event_id
-- across the whole analysis_status slice, which "ORDER BY source_event_id" would otherwise need
-- to satisfy with a filesort over the entire REVIEW_REQUIRED backlog on every scheduled pass.
--
-- This index leads with analysis_status (the one true equality filter both scans share) and
-- sorts by source_event_id, so the two reconciliation queries can read analysis_status=
-- 'REVIEW_REQUIRED' rows already in source_event_id order starting from the cursor, instead of
-- sorting the whole matching set with a filesort.
--
-- What this index does NOT cover: analysis_completed_at, phase and analysis_started_at are not
-- part of it, so those predicates are not answered from the index alone - each candidate row
-- the index yields still needs a row lookup to evaluate them. LIMIT 64 (or 16) bounds how many
-- ROWS ARE RETURNED after those predicates are applied, not how many rows are READ to find them:
-- if REVIEW_REQUIRED rows are numerous and the remaining predicates are selective, this can still
-- examine far more than 64/16 rows per pass. Whether that is acceptable, and whether MySQL
-- actually avoids a filesort with this index in place, has NOT been verified against a deployed
-- MySQL instance - EXPLAIN against real data and realistic REVIEW_REQUIRED volume must happen
-- before enabling scheduled reconciliation in production.
CREATE INDEX idx_fix141_reconcile_scan
 ON shared_market_event_delivery(analysis_status, source_event_id);


-- Baseline section V99__fix_142_delivery_ownership.sql
-- FIX-142: durable phase-stream fencing; never infer legacy ownership from age.
ALTER TABLE shared_market_consumer_state
 ADD COLUMN active_delivery_event_id BIGINT NULL,
 ADD COLUMN active_delivery_owner_token VARCHAR(80) NULL,
 ADD COLUMN active_delivery_process_owner VARCHAR(36) NULL,
 ADD COLUMN active_delivery_state VARCHAR(30) NOT NULL DEFAULT 'NONE',
 ADD COLUMN active_delivery_started_at TIMESTAMP(6) NULL,
 ADD COLUMN active_delivery_updated_at TIMESTAMP(6) NULL;
ALTER TABLE shared_market_event_delivery ADD COLUMN protection_outcome VARCHAR(20) NULL;
UPDATE shared_market_event_delivery SET protection_outcome='LEGACY_UNKNOWN'
 WHERE protection_completed_at IS NOT NULL AND protection_outcome IS NULL;
CREATE TABLE shared_delivery_resolution_audit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 source_event_id BIGINT NOT NULL, symbol VARCHAR(30) NOT NULL,
 operation VARCHAR(40) NOT NULL, old_owner_token VARCHAR(80) NULL,
 new_owner_token VARCHAR(80) NULL, reviewer VARCHAR(160) NOT NULL,
 evidence VARCHAR(2000) NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;
CREATE INDEX idx_fix142_price_expiry ON shared_market_event_delivery
 (symbol,interval_code,protection_completed_at,protection_started_at,observed_at);
CREATE INDEX idx_fix142_price_sequence ON shared_market_event_delivery
 (symbol,interval_code,protection_completed_at,symbol_sequence);
CREATE INDEX idx_fix142_lane_phase_close ON shared_market_event_delivery
 (symbol,interval_code,phase,candle_close_time,symbol_sequence);

-- FIX-142: price-window selection must not traverse all earlier untouched rows.
CREATE INDEX idx_fix142_price_window ON shared_market_event_delivery
 (symbol,interval_code,observed_at,symbol_sequence);


-- Baseline section V100__fix_144_paper_position_reason_capacity.sql
-- FIX-144: Preserve full paper-position audit explanations without changing trade decisions.
-- V11 introduced VARCHAR(2000); full explanations plus execution/progressive text exceed it.
-- New migration only: never edit the already-applied V11 checksum.
-- MySQL may rebuild this table and take metadata locks; run with the trader stopped.
-- Keep nullable fields and existing utf8mb4_unicode_ci collation. No work-state reconciliation.
ALTER TABLE paper_position
    MODIFY COLUMN entry_reason MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    MODIFY COLUMN exit_reason MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL;


-- Baseline section V101__fix_145_operator_work_cancellation.sql
-- FIX-145: preserve every original ledger column, including full error text.
-- No automatic backfill, cancellation, completion or retry.
CREATE TABLE signal_processing_work_cancellation LIKE signal_processing_work;
ALTER TABLE signal_processing_work_cancellation
 ADD COLUMN cancelled_at TIMESTAMP(6) NOT NULL,
 ADD COLUMN operator_name VARCHAR(128) NOT NULL,
 ADD COLUMN review_reference VARCHAR(128) NOT NULL,
 ADD COLUMN decision VARCHAR(64) NOT NULL;


-- Baseline section V102__fix_147_cutover_approver_account.sql
-- FIX-147: provision the separately authorized isolated approval-console account.
-- Reuse the enabled local administrator's existing encoded password without
-- displaying it or changing the administrator's role or credentials.
-- Preserve an existing destination account exactly, including disabled state.
-- No cutover state, proposal, approval audit or trading work is changed.
INSERT INTO app_user (username, password, role_name, enabled)
SELECT 'cutover_approver', a.password, 'CUTOVER_APPROVER', TRUE
FROM app_user a
WHERE a.username = 'admin'
  AND a.enabled = TRUE
  AND a.role_name = 'ADMIN'
  AND LENGTH(TRIM(a.password)) > 0
  AND NOT EXISTS (
      SELECT 1 FROM app_user existing_user
      WHERE existing_user.username = 'cutover_approver'
  );


-- Baseline section V104__fix_150_protection_entry_evidence.sql
-- FIX-150: claim ownership is not evidence that protection business code ran.
-- Existing attempts remain explicitly unknown; never infer their effects in backfill.
ALTER TABLE shared_market_event_delivery
 ADD COLUMN protection_apply_state VARCHAR(20) NOT NULL DEFAULT 'LEGACY_UNKNOWN';
-- Bounded health history uses reviewer equality followed by newest audit IDs.
CREATE INDEX idx_fix150_recovery_audit ON shared_delivery_resolution_audit(reviewer,id);


-- Baseline section V105__fix_151_delivery_selection_indexes.sql
-- FIX-151: trader-local eligibility access, not the collector/source schema.
-- The flag has no time dependency and is maintained by MySQL on every phase or
-- status change. Existing evidence and all review/ownership state stay unchanged.
-- A virtual column avoids storing the flag in every clustered table row.
ALTER TABLE shared_market_event_delivery
 ADD COLUMN phase_walk_pending TINYINT GENERATED ALWAYS AS
   (CASE WHEN phase<4 AND status<>'REVIEW_REQUIRED' THEN 1 ELSE 0 END) VIRTUAL;

-- FIX-151: skip completed/review rows without losing global sequence ordering.
CREATE INDEX idx_fix151_pending_sequence
 ON shared_market_event_delivery(symbol,phase_walk_pending,symbol_sequence);

-- FIX-151: one covering close-window access path for both exposure phase groups.
-- Closed equality and the close-time range come before phase/status filters so
-- old pending history does not have to be examined to count current windows.
CREATE INDEX idx_fix151_exposure_close
 ON shared_market_event_delivery(symbol,interval_code,closed,candle_close_time,phase,analysis_status);


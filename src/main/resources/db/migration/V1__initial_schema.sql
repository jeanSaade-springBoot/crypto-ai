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
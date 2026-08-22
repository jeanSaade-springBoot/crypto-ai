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

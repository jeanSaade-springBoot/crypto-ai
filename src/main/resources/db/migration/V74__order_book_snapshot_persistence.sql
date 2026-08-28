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

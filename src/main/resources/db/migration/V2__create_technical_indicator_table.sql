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
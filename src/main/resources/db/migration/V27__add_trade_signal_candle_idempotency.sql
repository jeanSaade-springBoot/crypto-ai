ALTER TABLE trade_signal
    ADD COLUMN candle_open_time TIMESTAMP(6) NULL AFTER interval_code;

CREATE UNIQUE INDEX uk_trade_signal_symbol_interval_candle
    ON trade_signal (symbol, interval_code, candle_open_time);

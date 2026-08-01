ALTER TABLE trade_signal
    ADD COLUMN sentiment_available BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN fundamental_available BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN excluded_categories JSON NULL;

ALTER TABLE trade_signal
    ADD COLUMN order_book_window_seconds BIGINT NOT NULL DEFAULT 0 AFTER order_book_observations,
    ADD COLUMN order_book_wall_persistence_seconds BIGINT NOT NULL DEFAULT 0 AFTER order_book_window_seconds,
    ADD COLUMN order_book_influence_factor DECIMAL(8,6) NULL AFTER order_book_wall_persistence_seconds,
    ADD COLUMN order_book_veto_allowed BOOLEAN NOT NULL DEFAULT FALSE AFTER order_book_influence_factor;

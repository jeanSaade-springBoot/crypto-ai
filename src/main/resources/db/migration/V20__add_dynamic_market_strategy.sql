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

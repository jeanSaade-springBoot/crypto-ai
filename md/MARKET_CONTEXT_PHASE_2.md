# Dynamic Market Strategy — Phase 2

This phase introduces `MarketContextService` before strategy selection.

Strategy selection now considers:

- Coin/timeframe technical regime
- Latest closed higher-timeframe context
- Dynamic BTC relationship and current BTC context
- Current order-book liquidity
- Enabled sentiment-provider contribution coverage
- Required technical-data availability

The context is collected with neutral directional inputs so it can select a
strategy before a BUY or SELL decision exists. After scoring, the existing ATR,
multi-timeframe, BTC, and liquidity safety layers run again using the real
isolated decision.

Every signal stores `market_context_snapshot` as immutable JSON through Flyway
migration `V21__add_market_context_snapshot.sql`.

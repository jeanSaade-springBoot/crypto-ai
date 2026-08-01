# Order-book liquidity update

This build preserves the previous project features and adds a live Binance depth safety layer.

## Preserved
- Java 21 / Spring Boot / MySQL / Flyway / Spring Security
- Event-driven closed-candle analysis
- All technical indicators and normalized /85 scoring
- ATR risk veto and trade plan
- Grouped Trend scoring (8/7/6/4)
- Sentiment-provider health alerting
- Immutable multi-timeframe confluence snapshots
- Dynamic BTC correlation/beta context snapshots
- XRPUSDT configured only in YAML
- Dashboard pinning, readable score breakdown, MACD/volume confirmations
- Explicit Lombok Maven annotation processing

## Added
- Binance `/api/v3/depth` snapshots for every configured symbol
- Configurable rolling in-memory depth history
- Bid/ask depth and imbalance within a configurable price range
- Persistent bid/ask wall detection across multiple observations
- Ask-wall detection before ATR take-profit
- Stop exposure warning when persistent bid support is absent
- Conservative BUY downgrade to WATCH for strong ask pressure/blocked target
- Immutable liquidity fields stored in `trade_signal`
- Dashboard Order Book & Liquidity section
- Flyway `V19__add_order_book_liquidity_context.sql`

The liquidity layer does not add points to the raw score.

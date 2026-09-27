# FIX-132 candle-read inventory

Baseline: pom(20260926-180106).zip (crypto-ai-trader). Its 494 baseline files match the previous reviewed source after CRLF normalization. This is a source inventory, not proof of live database dependencies.

## Repository consumers

Every class below uses CandleRepository. Routing only the primary indicator path is insufficient.

- `com.crypto.controller.DashboardApiController`
- `com.crypto.debug.monitor.service.PriceMoveMonitorService`
- `com.crypto.execution.service.ExecutionIntelligenceService`
- `com.crypto.execution.service.PressureReadinessService`
- `com.crypto.execution.service.RecoveryTransitionService`
- `com.crypto.indicator.service.TechnicalIndicatorService`
- `com.crypto.inspector.service.TradeInspectorService`
- `com.crypto.regression.service.RegressionTestWorker`
- `com.crypto.service.BinanceKlineService`
- `com.crypto.service.BtcMarketContextService`
- `com.crypto.service.CandleDataQualityService`
- `com.crypto.service.DerivativesPositioningService`
- `com.crypto.service.MarketDataService`
- `com.crypto.service.ScheduledAnalysisService`
- `com.crypto.service.TradeReplayService`
- `com.crypto.service.TrendStructureService`
- `com.crypto.wallet.service.WalletService`
- `com.crypto.whale.service.WhalePriceService`

## Queries outside the repository

| Class | Read path |
| --- | --- |
| RegressionTestService | Replay run charts, Proven trade segments, standalone Replay trade chart |
| TradeActivityService | Candle window for trade activity |
| SystemHealthDailyService | Candle coverage aggregation |
| SignalProcessingFreshness | Exact latest closed-candle recovery eligibility |
| CandleGapDiagnostics | Explicit crypto_ai.candle coverage query |

## Implemented routing status

All repository consumers above now use the JDBC CandleRepository facade. Every direct-read path listed above uses SharedMarketSource, including SystemHealthDailyService's query held in a SQL variable. LIVE selects crypto_ai_v2; OFF/OBSERVE retain the local source. Historical Flyway checksums were preserved.

Candle is no longer a JPA entity, so Hibernate validation does not require the renamed local candle table. Recovery joins are split into shared candle reads and local indicator/signal identity checks. Local upsert remains guarded and is unavailable in LIVE. Bootstrap/import/kline processing and WS start/reload are all guarded. Coin pair validation still makes a separate REST request; it is not a candle ingestion path.

MarketPriceEventService retains provenance and applied status. ExecutionPriceAuthority reports the actual source. The worker returns durable outcomes; a queued event is not treated as successful analysis. The new consumer has its own bounded submissions and does not use the legacy dispatcher queues for collector delivery.

## Baseline findings addressed by this delivery


- `domain.Candle`: JPA entity mapped to unqualified candle. With ddl-auto=validate, merely changing JDBC read queries will not permit the old table rename.
- `CandleRepository`: derived JPA queries, exact as-of JPQL, native INSERT into crypto_ai.candle, and recovery SQL joining technical_indicator/trade_signal. Shared SELECT-only credentials must not be given local write privileges to make this join work. Split the source read and local coverage check.
- `BinanceKlineService`: live writer and live price/close-event source; disable only when replacement delivery is validated.
- `MarketDataService`: REST importer/upsert. Callers: MarketDataBootstrapService bootstrap/run and MarketDataController import endpoint. DynamicCoinActivationService invokes bootstrapSymbol and reload; CoinConfigurationController also invokes reload. Disable all those writer/reconnect entry paths at cutover.
- `BinanceWebSocketManager`: lifecycle/health/reload source. Disabling only startup is insufficient if reload can reconnect.
- Historical Flyway migrations create/index candle. Preserve their checksums. Rename is a new controlled deployment operation.

## Baseline historical price and delivery findings

- `MarketPriceEventService` stores intraminute prices; its find methods currently discard source and return only timestamp/price.
- `ExecutionPriceAuthorityService` reports literal BINANCE_KLINE_LIVE_CLOSE. Changing only INSERT provenance is insufficient.
- `ShadowProductionReplayService` and `OneCandleContinuationGraceReplayObserver` consume historical price evidence; neither should ever claim a live collector event.
- `CandleClosedAnalysisWorker` catches failures and returns void. A submitted event is not proof of completed analysis.
- `CandleAnalysisDispatcher` has unbounded per-lane ArrayDeque queues. Bounded backing threads do not bound these queues.

## Acceptance matrix

| Area | Required check before cutover |
| --- | --- |
| Replay OLD/NEW | Identical closed-candle windows and historical price evidence, same lineage/order; no live lease changes |
| Dashboard/Trade Inspector/Proven | All candle chart reads come from shared source; local signals/trades remain local |
| Wallet valuation/context/evidence | Explicit source read, no local candle fallback |
| Live protection | Every eligible canonical 1m observation audited; no analysis backlog blocking price workers |
| Recovery/health | Exact missing-analysis checks and source coverage; diagnostic recovery pause remains explicit |
| Existing collector consumers | Their event claims/outcomes and table schemas untouched; measured lag/commit overhead acceptance |

## Database checks not possible from source alone

Inspect live views, triggers, foreign keys and external scripts referencing crypto_ai.candle. Confirm source symbol/interval coverage and SELECT-only grants. Collector defaults include 1m/5m/15m/1h/4h; do not assume 1d exists. Do not rename until startup validation and every reader/writer is migrated.

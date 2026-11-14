# Crypto AI Trader — Architecture (Claude)

Maintained by Claude. Based on a read-only review of the working tree on
2026-10-09 (branch `master`, including uncommitted FIX-153 changes). Line
numbers are approximate. `codex-docs/` is maintained separately by Codex.

Status labels: **Current** (in the working tree), **Pending** (design only),
**Legacy/shared** (retained shared-market path).

## Release status

- FIX-153 (trader-owned local candles, `shared-market.mode=OFF`): implemented
  in source, **not approved for production**. Java 21 build/tests, disposable
  MySQL bootstrap, crash/race, Windows two-process, soak and Replay-parity
  gates are open. This workstation has Java 17 and cannot compile the project.
- FIX-153 work is **uncommitted**: ~67 modified tracked files plus untracked
  `com/crypto/local/`, `com/crypto/infrastructure/diagnostics/`, migrations
  `V99`–`V106` (except the Java `V103`) and `B105`.
- FIX-154 (higher-timeframe BUY validation): pending, not implemented.

## Stack

Spring Boot 4.1.0, Java 21, MySQL 8 (`docker-compose.yml`: `mysql:8.4`,
host port 3307), Flyway (`V1`–`V106`, plus baseline `B105`), Spring Security
form login, Jenkins + `scripts/deploy/Deploy-Trader.ps1`. Paper trading only;
no live-exchange orders.

## Databases

| Schema | Owner | Trader use |
|---|---|---|
| `crypto_ai` | Trader | Only runtime DB in OFF mode. |
| `crypto_ai_v2` | Collector | Read in LIVE/OBSERVE via `SharedMarketSource` read-only pool. Must not be touched in OFF mode. |

`V103__fix_149_fresh_admission_indexes` (Java migration) adds indexes to
`crypto_ai_v2.market_data_stream_event`; a fresh trader-only install fails
without that schema. Release blocker; do not edit applied migrations.

## Ingestion modes (`shared-market.mode`, default `OFF`)

- **OFF (FIX-153)** — trader connects to Binance and writes `crypto_ai.candle`.
  `LocalTraderInstance` requires an absolute `local-candle.instance-lock-path`
  (env `LOCAL_TRADER_INSTANCE_LOCK`) and holds an OS file lock: one trader
  process per host. Empty value fails OFF-mode startup.
- **LIVE / OBSERVE (legacy/shared)** — `SharedMarketConsumer` polls collector
  stream tables into local `shared_market_event_delivery`;
  `SharedAnalysisDispatcher` drives analysis. Direct ingestion entry points
  (`BinanceWebSocketManager`, `BinanceKlineService.processKline`,
  `MarketDataService.importCandles`, `MarketDataBootstrapService`) are
  disabled in LIVE. The two paths must never run together.

## Primary flow (OFF mode)

```text
Binance WS (combined stream, enabled coins x 1m/5m/1h)
 -> BinanceWebSocketManager (owner thread "fix131-ws-owner", generation gate)
 -> BinanceWebSocketHandler.handleTextMessage (socket thread, synchronous)
 -> BinanceKlineService.processKline        (@Transactional NEVER, drain check)
 -> KlineTransactionCoordinator.process     (each phase REQUIRES_NEW, READ_COMMITTED, 30s)
     1 INPUT        CandleRepository.upsertRuntime + MarketPriceEventService.recordPending (1m)
     2 PROTECTION   LivePositionProtectionService.onPrice (1m; one retry on initial-lock deadlock)
     3 OBSERVER     PriceMoveMonitorService.onPrice (errors swallowed)
     4 CLOSE        publish CandleClosedEvent only if this receipt won the 0->1 close token
 -> CandleClosedEventListener (AFTER_COMMIT, not @Async)
 -> CandleAnalysisDispatcher (per symbol|interval lane)
 -> candleAnalysisExecutor (core 8 / max 16 / queue 200, AbortPolicy)
 -> CandleClosedAnalysisWorker.processShared
 -> TechnicalIndicatorService -> AnalysisService -> TradeSignal + signal_processing_work
 -> SignalProcessingCoordinator -> PaperTradingService -> WalletAutoExecutionService
```

All candle writes and live position protection run on the single WebSocket
socket thread, one message at a time. Analysis never runs on it.

## Candle persistence

- `CandleRepository.upsertRuntime/write`: `INSERT ... ON DUPLICATE KEY UPDATE`
  on `uk_candle_symbol_interval_open_time`. Once `closed=1` every column keeps
  its old value (monotonic). RUNTIME writes set `close_transition_token` only
  on the 0->1 transition; the read-back token equal to this receipt's UUID is
  the sole authority to dispatch a close. BACKFILL writes never set a token.
- `domain/Candle` is now a read DTO.

## Recovery and gaps

- `LocalClosedCandleDetector` (every 2s, OFF only): re-admits tokened, non-
  BACKFILL closed candles with no `trade_signal` and no
  `signal_processing_work` (up to 6 retries). Repairs commit-to-dispatch gaps,
  **not** missing close facts.
- `CandleGapDiagnostics` (every 10s, 72h horizon): logs `DB_CANDLE_GAP` only,
  `repair=false`.
- REST backfill (`MarketDataService.importCandles`, BACKFILL provenance,
  skips still-open candle, server time fetched before klines, no HTTP inside
  the transaction) runs only at startup (`MarketDataBootstrapService`) or coin
  activation.
- **Gap:** a WebSocket reconnect at a candle-close boundary can leave rows at
  `closed=0` (Binance does not replay the final closed kline). Nothing repairs
  this automatically; with `maximum-missing-candles: 0` it blocks analysis for
  that symbol/interval. Deployment gate.

## WebSocket lifecycle

`BinanceWebSocketManager` ticks every 500 ms on its owner thread; connect and
close run on separate workers. Each attempt gets a new
`BinanceWebSocketHandler` + `GenerationGate` (PENDING -> ADMITTING -> REVOKED);
messages outside ADMITTING / the primary session are dropped. Handshake
deadline 15s. Failure backoff = `websocket.health-check-seconds` (15s);
`websocket.reconnect-delay-seconds` is only displayed by
`BinanceStatusController` and does not drive reconnects. 30s silence only logs
`NO_MESSAGES`. Coin changes call `reload()` (bounded mailbox, 30s wait).
Symbols come from `CoinConfigurationService.enabledSymbols()` (DB), not yml.

## Analysis

- `CandleAnalysisDispatcher`: one active task per lane. 1m keeps only the
  newest pending candle (`PENDING_REPLACED`); higher intervals FIFO capped at
  4 (`LANE_OVERFLOW`). Coordinates live and detector admissions.
- `CandleAnalysisExecutionCoordinator`: 1024 striped locks by
  symbol+interval+openTime; detector uses `tryLock`.
- `CandleClosedAnalysisWorker` stages: retry resume -> coordination lock ->
  `CandleDataQualityService` (>=210 candles, 0 gaps, valid OHLCV) ->
  indicators -> existing-signal check -> freshness (`SharedEventPolicy`;
  1m 90s, 5m 120s, 1h 300s, 4h 600s, 1d 1800s after close) -> LIVE analysis +
  registration, or `HISTORICAL_ONLY` analysis for stale input -> signal
  processing.
- Signal decision details: see `TRADING_RULES.md`.

## Execution

- `SignalProcessingStore`: `signal_processing_work` row created in the same
  transaction as the `trade_signal`. Claim (REQUIRES_NEW, 3s, FOR UPDATE)
  sets RUNNING + owner token. States: PENDING, RUNNING, COMPLETED, EXPIRED,
  RETRYABLE_FAILURE, SYMBOL_LOCK_RETRY (1/3/10s backoff), LOCK_RETRY_EXHAUSTED,
  REVIEW_REQUIRED (never auto-replayed). RUNNING > 5 min is quarantined.
- `SignalProcessingCoordinator.process` business transaction: symbol lock
  (`wallet_symbol_coordination`) -> ownership recheck -> position lock ->
  lineage recheck -> `SignalProcessingFreshness` (after locks, DB clock with
  exchange correction) -> `PaperTradingService.processSignalBody` ->
  `store.complete`.
- `SignalProcessingRecovery` (every 30s) resumes due WORKER/RECOVERY rows.
- `WalletAutoExecutionService`: lock order symbol -> position ->
  `wallet_mutation_coordination` -> daily stats. Idempotent by unique
  `wallet_trade.execution_key`. `WalletMutationRepositoryGuard` rejects wallet
  writes without the mutation lock.

## Price evidence (`market_price_event`)

1m direct observations: `recordPending` (INPUT tx; rejects >2s future, >15s
stale vs exchange-corrected JVM clock; duplicate timestamp -> NON_CANONICAL)
-> `finish` in PROTECTION tx -> APPLIED or NON_CANONICAL.
`resolvePending` (every 5s, OFF) marks PENDING > 120s and not in flight as
PROTECTION_NOT_APPLIED (never replayed). Replay and execution price authority
use only LEGACY/APPLIED rows. `ExchangeClockSynchronizer` measures Binance
time vs DB and JVM (dedicated 5s client, no HTTP under locks).

## Other components

| Area | Key classes | Notes |
|---|---|---|
| Order book | `OrderBookLiquidityService`, `OrderBookSnapshotService` | REST depth every 5s; best-effort async persistence, drops on overflow. |
| Replay | `RegressionTestWorker`, `ShadowProductionReplayService`, `ReplayParityValidator` | Same production policy beans; simulated wallet; exact parity only with stored price events (`EXACT_PRICE_REPLAY`), else `SIGNAL_PRICE_FALLBACK`. Writes only `*_test` / archive tables. |
| Inspector / activity | `TradeInspectorService`, `TradeActivityService` | Read-only except review marks / copy-to-proven. |
| Health | `SystemHealthDailyService`, local-candle health | OFF-mode must not query retired shared tables. |
| Monitor | `PriceMoveMonitorService`, `PriceMoveFinalizationWorker` | "Catching Market" retrospective blame. |
| Audit | `ProductionExitAuditService` | Records real close trigger. |
| Drain | `LocalDrainService` | ADMIN-only; stops admission, exits JVM on quiesce, resumes on timeout. |
| Security | `SecurityConfig` | Form login, `app_user`; `/api/local/drain`, `/api/fix142/**` ADMIN. |
| Whale | `whale/*` | Optional schedulers when `whale.enabled`. |
| Approval console | `com.fix132.approval` | Separate app outside `com.crypto` scan; shared-market cutover only. |

## Deployment

Jenkins: JDK 21 `mvn clean package` -> `Deploy-Trader.ps1` (mutex, single
FIX-153 jar, SHA256, refuses unknown port-8080 owner, requires graceful
`-DrainCommand`, never force-kills, verifies child PID and OFF guard). No
automatic rollback. No deployment or production change without explicit user
approval.

# Crypto AI Trader — Architecture

## FIX-156 Replay boundary correction — 2026-10-16

Replay has a dedicated `com.crypto.regression.replay` boundary foundation. It
holds the frozen policy-profile contract, Replay-only sizing/minimum-order/SELL
policy primitives, and an operation-aware gateway for the narrowly permitted
`fix122_evaluation` diagnostic operations. This foundation remains incomplete:
the existing shadow Replay workflow has not yet been fully rewired away from
wallet settings, live Binance constraints, shared BUY validation, or legacy
test-table output. Production runtime and trading behavior remain unchanged.

V110 was confirmed already applied and was restored unchanged. Any new
Replay-owned schema is forward-only V111; it creates profile-detail, run
certificate, and price-coverage records without changing an existing table.

The active baseline no longer reads the legacy shared `fix122_replay_revision`
experiment switch, and its manual chart does not query `wallet_trade`. These
are boundary corrections only; the Replay-local BUY/lifecycle mirror and all
parity evidence remain pending.

`ReplaySizingPolicy`, `ReplayMinimumExecutionPolicy`, and the existing
`ReplaySellValidationPolicy` are Spring-managed Replay-only components because
they are injected by `ShadowProductionReplayService`; they have no wallet or
live-Binance dependency.

Status labels used in this document:

- **Current** — present in the repository today.
- **Pending** — approved design direction, not implemented or deployed.
- **Legacy/shared** — existing shared-market path retained as historical evidence
  while the trader moves to local-candle ownership.

## Current release status

**FIX-153 is implemented in source but is NOT APPROVED FOR PRODUCTION.**
It moves the trader toward collector-free, trader-owned local candle ingestion
in `shared-market.mode=OFF`. Java 21, disposable-MySQL, crash/race, Windows
two-process, soak, OFF-mode and Replay-parity proof gates remain open.

FIX-154 is a separate pending higher-timeframe design. It is not implemented
and is not part of FIX-153 deployment scope.

**FIX-156 is in progress and Replay-only.** A foundation exists for immutable
Replay policy profiles, Replay-owned evidence/output tables, and a fail-closed
Replay SQL allow-list. The existing Replay service has not yet been rewired
away from its live wallet/Binance/shared-service dependencies, so FIX-156 is
not complete or deployable.

Replay execution summaries are now published through the Replay-owned
`replay_run_output` table with an explicit `NOT_PARITY_CLAIMED` status. This is
diagnostic execution evidence only; it does not certify analysis or lifecycle
parity and does not alter Production behavior.

Each Replay completion now also writes a fail-closed run certificate with both
parity dimensions `NOT_MEASURED` until qualified Production comparison and
price-coverage evidence are available.

The active Shadow Replay lifecycle writer and active run views use Replay-owned
position, execution, and management tables introduced by forward-only V112.
Historical archive/proven-trade functions remain outside the execution path.

## System purpose

The application is a Spring Boot cryptocurrency trader that analyses Binance
market data, persists candles and technical indicators, creates trade signals,
and performs paper-wallet execution and position protection. Production and
Replay are intended to use equivalent persisted market evidence.

## Runtime and data ownership

| Area | Current ownership | Target direction |
|---|---|---|
| Trader database | `crypto_ai` | Remains the trader's only runtime database. |
| Collector database | `crypto_ai_v2` | Collector remains separate and is not read/written by the trader at runtime. |
| Candle ingestion | In OFF mode, trader-owned Binance ingestion writes local `crypto_ai.candle`. | Must be proven in a collector-free deployment rehearsal. |
| Candle processing | Bounded lanes, close-transition tokens and a local detector are implemented. | MySQL/race/crash proof remains required. |
| Trading | Paper wallet and managed positions. | Live-exchange orders are outside the local-candle automatic-resolution design. |

## Current primary flow

```text
Binance WebSocket kline
  -> BinanceKlineService
  -> KlineTransactionCoordinator
      -> input transaction: candle + canonical 1m market price event
      -> protection transaction: LivePositionProtectionService
      -> observer transaction
      -> close-dispatch transaction
  -> CandleClosedEvent (AFTER_COMMIT)
  -> CandleAnalysisDispatcher
  -> CandleClosedAnalysisWorker
  -> TechnicalIndicatorService
  -> AnalysisService / TradeSignal
  -> SignalProcessingCoordinator
  -> PaperTradingService / wallet and managed-position state
```

`KlineTransactionCoordinator` preserves input -> protection -> observer ->
close-dispatch ordering. Closed-candle analysis is asynchronous and must not
run on the Binance ingestion thread.

## Current core persistence

| Table/domain | Purpose |
|---|---|
| `candle` | Local OHLCV candle history keyed by symbol, interval, and open time. |
| `technical_indicator` | Persisted indicator snapshots derived from closed candles. |
| `trade_signal` | Persisted analysis decisions and their context. |
| `signal_processing_work` | Durable processing registration/status for a persisted signal. |
| `market_price_event` | Canonical 1m price evidence used by protection and Replay. |
| Paper wallet / position tables | Paper executions, open-position state, TP/SL/profit protection. |

## Legacy/shared-market path

**Current but pending retirement from the trader runtime.**

The shared path includes `SharedMarketSource`, `SharedMarketConsumer`,
`SharedAnalysisDispatcher`, cutover/readiness logic, and local historical
tables such as `shared_market_event_delivery`, `shared_market_consumer_state`,
and `shared_market_health`. These records remain evidence and must not be
dropped, reset, or used to manufacture an approval.

The collector event/cursor source is `crypto_ai_v2.market_data_stream_event`
and `market_data_stream_cursor`. In the target local-candle runtime, the
trader must not connect to, read, or write these collector tables.

`V103__fix_149_fresh_admission_indexes` is an existing Java Flyway migration
that requires the collector event table for a fresh installation. A trader-only
bootstrap policy is therefore **pending** and remains a release blocker. Do
not edit applied Flyway migrations or Flyway history.

## Implemented, unverified: local-candle architecture (FIX-153)

The implemented intended mode is `shared-market.mode=OFF` with trader-owned Binance kline
ingestion and local candle writes. The shared executors and direct-ingestion
path must never run together.

Implemented safeguards:

- Candle writes are monotonic: a late open update cannot reopen or overwrite a
  closed candle.
- A closed-candle transition is admitted exactly once using an atomic upsert
  result plus in-process admission coordination.
- A bounded local detector recovers a closed candle that was committed before
  the in-memory event/dispatcher could start analysis.
- Fresh candles may execute; stale candles are analysed historically only, or
  their already-owned work is expired without business execution.
- Analysis lanes are bounded. Under overload, 1m may retain only the latest
  pending candle; higher intervals use an explicit bounded FIFO policy.
- Historic shared `REVIEW_REQUIRED` evidence must not block OFF-mode work.
- OFF-mode health/admin endpoints must use local-candle health and must not
  query retired shared-market tables.
- Current REST backfill can finalise an interrupted open row but cannot mint a
  runtime close token or dispatch analysis. Runtime close tokens plus
  provenance keep backfill out of detector admission. The approved, pending
  automatic-reconciliation extension will also insert an exact wholly absent
  candle only inside the active quality window, with `BACKFILL` provenance and
  the same no-token/no-dispatch boundary.
- Observed local behavior (2026-10-09): a WebSocket reconnect at a candle-close
  boundary can leave the already-persisted 1m/5m rows open because Binance does
  not replay the final closed-kline event. The current detector recovers missing
  analysis admission, not missing close facts. An authoritative, bounded REST
  repair may therefore finalise the exact `closed=0` row or, under the approved
  gap-backfill scope, insert an exact absent identity. It must preserve existing
  provenance, use `BACKFILL` for an insertion, create no close-transition token,
  and never dispatch historical work. Automatic reconciliation of this reconnect
  case remains a FIX-153 release gate; the one-time local repair is evidence,
  not proof that the gap is closed.
- Approved pending reconciliation design: gap backfill is insert-only (no
  duplicate-key update); it can never alter a live row that appears during a
  REST request. Candidate selection is bounded to the active quality window;
  terminal audit records are not retried. Every candle/work route must produce
  a durable terminal outcome or remain explicitly retryable after a transient
  failure. Historical-only processing has no execution authority.
- The pending universal mutation barrier is an in-memory drain participant,
  not a database or persistent trading fence. Outside drain it must not delay,
  queue, or block fresh trading, signals, or protection. Open-position
  protection during application replacement remains an unresolved release gate.
- Local source now contains the first reconciliation implementation slice:
  V107 durable audit/outcome tables, update-only authoritative open-row repair,
  pure insert-only gap backfill, a dedicated recovery executor, a reconnect
  request hook, and drain participation. It is disabled by default and remains
  unvalidated on MySQL; it has no deployment approval.
- Direct prices are normalised to `DECIMAL(30,12)` before persistence,
  protection and completion. Equal timestamps have one canonical receipt.
- Every processing attempt is freshness-checked after normal locks using the
  database-to-exchange correction. JVM-origin timestamp decisions use the
  separately measured JVM-to-exchange correction. The measurement performs no
  HTTP under execution locks and uses a bounded five-second time-only client.
  General Binance REST calls use the configured request timeout, and scheduled
  clock sampling has a four-thread Spring scheduler pool.
  Bootstrap obtains time/klines before opening its local candle-write
  transaction, so a stalled REST request does not retain database resources.
  REST bootstrap gets Binance server time before klines.
- An ADMIN-only drain stops analysis admission, exits the JVM on successful
  quiesce, and resumes admission on timeout. Price protection pauses during
  the controlled drain window.

## Implemented, unverified: protection price-event state machine

For direct 1m price observations, the target evidence lifecycle is:

```text
PENDING
  -> APPLIED                 (protection effects and state commit together)
  -> NON_CANONICAL           (invalid, stale, out-of-order, or duplicate)
  -> PROTECTION_NOT_APPLIED  (confirmed rollback/crash resolution)
```

Replay and price authority consume only accepted `LEGACY`/`APPLIED` rows.
Replay must reject a parity claim when the requested range contains unresolved
direct `PENDING` evidence; this must not block live trading.

The automatic resolver is valid only for the paper-trading phase, where every
protection effect commits in the same transaction as its `APPLIED` update.
Before deployment, the code must prove that there are no asynchronous,
`REQUIRES_NEW`, in-memory-only, or external effects in this boundary. A future
live-exchange order needs a separate idempotent/exchange-reconciliation design.

The target runtime also requires exactly one active trader process. This is an
operational prerequisite, not a database lease or fence.

## Pending: FIX-154 higher-timeframe BUY validation

FIX-154 is separate from the local-candle change and is not implemented.

Its purpose is to let a historical bearish 1h signal affect a current 1m BUY
only when the historical evidence is fresh, quality-valid, and sufficiently
strong. `btcContextEntryAllowed=false` on a historical signal is investigatory
evidence, not a higher-timeframe quality gate; the current 1m BTC protection
remains unchanged.

The design includes reliability bands (`NOT_TRUSTED`, `SOFT_CONFLICT`,
`HARD_BEARISH_CANDIDATE`), required fresh/quality-valid bearish 5m support for
a hard veto, persisted `trade_signal.data_quality_valid`, recovery release
rules, immutable outcome audit evidence, and Replay/no-look-ahead parity.

The implementation design is maintained in `codex-docs/FIX-154_TECHNICAL_DESIGN.md`.
It supersedes no existing deployed behavior and is deliberately separate from
the unrelated legacy readiness document under `src/main/resources/md/FIX-146.md`.

## Deployment posture

Neither FIX-153 nor FIX-154 is deployment-ready. FIX-153 source hardening is
implemented but unverified; FIX-154 remains pending.

Required validation includes Java 21 build/tests, disposable MySQL bootstrap,
real-MySQL concurrency and lock-wait tests, crash/restart tests, stale-input
tests, Replay parity checks, OFF-mode health/admin checks, collector-free
runtime verification, deterministic dispatcher/detector/drain tests,
two-process Windows lock rehearsal, and sustained-load observation across
enabled symbols and intervals. This local workspace has Java 17 and cannot
compile the Java 21 project.

No production configuration, database, service, or deployment change is made
without explicit user approval.

## FIX-153 validation status (2026-10-09)

The reconciliation component now has deterministic Java 21 coverage for its
core admission and outcome contract. The tests execute the real audit and
outcome stores against H2 while mocking only Binance and the candle write
boundary. They prove that healthy delayed live closes are not taken over and
that recovery remains token-free/non-live when it is positively evidenced.

The real-MySQL suite contains matching update-only and insert-only assertions,
but it requires a deliberately disposable loopback MySQL instance and is not
run by the ordinary unit-test goal. This is a release gate, not a warning that
can be waived. The shared Binance 429/418 admission gate, universal mutation
barrier, structural historical-only isolation, financial-effect reconciler,
atomicity/crash proofs, replacement-time position protection, Replay parity,
and soak validation remain incomplete. Therefore `local-candle.reconciliation.enabled`
must stay `false`.

The single `local-candle` configuration mapping is a startup invariant. It now
contains both reconciliation and existing local-runtime properties; a second
mapping is invalid YAML and prevents the Spring application context from
starting.

### Shared Binance REST admission

`BinanceRestAdmissionGate` owns one process-wide REST cooldown. It is consulted
immediately before every call through `BinanceMarketDataClient`; a 429/418
response extends the cooldown using `Retry-After` when it is supplied, otherwise
the bounded fallback. This protects all local REST consumers from repeatedly
calling Binance during a rate-limit/ban response. It is deliberately not part
of the WebSocket or protection path and is not a persisted trading fence.

### Outcome-schema startup invariant

The active signal-processing runtime writes `candle_analysis_outcome` for
terminal analysis evidence. V108 therefore ships with the FIX-154 release even
though local-candle reconciliation remains disabled. `CandleAnalysisOutcomeSchemaVerifier`
runs after Flyway and fails application startup with a clear message if the
table is absent; this prevents later processing transactions from failing only
when they attempt their first outcome write.
# FIX-154 implementation update — 2026-10-10

`HigherTimeframeBuyAssessmentService` is the single pure assessment boundary for
the 5m/1h BUY context. It returns effective decisions separately from the JPA
entities, so a SOFT 1h conflict can be assessed as effective `NEUTRAL` without
ever changing the persisted signal. `TradeExecutionValidationService` uses it
for direct BUY and accumulated-evidence authority; `OpportunityConsolidationService`
uses the same assessment for consolidation. A trusted SOFT bearish 1h limits an
initial entry to 25%; a trusted HARD or unknown bearish 1h rejects it, while
the legacy 5m bearish veto keeps priority.

`buy_htf_assessment_audit` is append-only operational evidence. Its writer is
best-effort and uses a bounded dedicated executor: auditing never borrows an
execution transaction's database connection, is skipped in Replay, and cannot
be part of a BUY decision. The progressive add route retains its
existing raw-1h guard; it writes `SOFT_HTF_ADD_BLOCKED` evidence only after
that guard has already rejected an add.

`GET /api/fix154/health` exposes non-gating counters for untrusted bearish
blocks, soft caps, soft add blocks, and best-effort audit failures.

## FIX-153 automatic local-candle reconciliation — enabled 2026-10-10

In `shared-market.mode=OFF`, `LocalCandleReconciliationService` runs every
five seconds on its dedicated single-worker executor. It finds eligible
missing or interrupted candle identities inside the data-quality window, reads
the authoritative Binance candle, and writes only verified final evidence.
An existing open row is finalized by an update-only path; a missing row uses a
plain insert-if-absent backfill path. Neither route creates a close token,
dispatches analysis, or executes a trade. The next genuine live close resumes
ordinary analysis after the quality window is complete.

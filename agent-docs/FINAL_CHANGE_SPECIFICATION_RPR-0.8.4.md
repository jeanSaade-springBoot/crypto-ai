# FIX-156 — Replay / Production Business-Logic Parity

**Revision:** RPR-0.8.4-DRAFT (RPR-0.8.3-DRAFT plus the signal-generation, entry and position-state review in Appendix B item 11; changes listed in Appendix B)  
**Status:** NOT APPROVED — IMPLEMENTATION FORBIDDEN  
**Date:** 2026-10-10  
**Code compared:** `d66c7a79-src.zip` (SHA-256 `cbdab04093a8a569c45ddb685ffcae082fab0df5ed928f50d2b185cbcdf5910f`), source read only.  
**Review basis:** 12-round Codex/Claude review run `20261010-182733`.  
**Baseline to freeze before approval:** source, configuration, migrations, and complete worktree SHA-256.  

## 1. Decision and non-negotiable scope

This revision adopts the user's existing direction: **FIX-156 changes Replay only. Production source code, behaviour, database writes, account state, HTTP traffic, schedulers, and latency must remain unchanged.** No shared-service extraction is authorised in this fix.

Replay is a manual, single-symbol research tool. It produces traceable decision, position, and exit output at a fixed notional—not a wallet, balance, cash reserve, sizing ledger, daily budget, or account simulation. It may reproduce only non-wallet Production business rules through Replay-local adapters which are version-matched and tested against Production fixtures. It must never claim that it reproduces historical wallet state, live scheduling, MySQL locks, Binance availability, or actual fills.

Release labels are mutually exclusive:

| Label | Meaning |
|---|---|
| `ANALYSIS_PARITY` | A stored Production signal/outcome is reproducible under the defined as-of evidence and oracle contract. It makes no account/lifecycle claim. |
| `REFUSED_INSUFFICIENT_EVIDENCE` | Required evidence or lineage is absent, stale, ambiguous, or forbidden. No signal/trade artifact is published. |
| `FAILED_REPLAY` | Internal error/cancellation after staging began. Staged artifacts are discarded. |

Existing FIX-11K, FIX-11Q, pre-FIX-122, and `EXPERIMENTAL` paths remain displayable only; they cannot enter a baseline result.

## 2. Explicit Replay boundary

The boundary is the Regression Replay entry points: `RegressionTestService`, its controllers/requests, `RegressionTestWorker`, `ShadowProductionReplayService`, Replay observers, `/runs/**` output, and every asynchronous child task they start.

They must not read or write any Production account table: `wallet_asset`, `wallet_trade`, `wallet_managed_position`, `paper_position`, `wallet_settings`, account snapshots, daily-account statistics, or live execution state. They must not invoke Binance/HTTP clients or call a Production fallback branch. A run-scoped guard propagates to all Replay child threads and fails closed with `REPLAY_FORBIDDEN_DEPENDENCY` before publication.

`TradeReplayService.replay(positionId)` is an existing Production inspection view, not Regression Replay; it is explicitly outside this boundary and must not be changed by FIX-156.

## 3. Evidence contract and time model

### 3.1 Allowed evidence

Replay reads only durable market/decision evidence, each with an as-of rule:

| Evidence | Permitted use | Limitation / refusal rule |
|---|---|---|
| `candle` | indicator timeline | exact symbol/interval/open/expected-close identity; decision-time closure lineage required for parity; current `closed=1` alone is not proof. |
| `trade_signal`, `candle_analysis_outcome`, `signal_processing_work` | stored outcome/oracle | compare only fields and signal modes explicitly versioned by Section 7. |
| `market_price_event` | canonical execution/protection price timeline | accept only `LEGACY`/`APPLIED`; retain event id, `observed_at`, ordering slot and status. Coverage is per span, not a run-wide flag. |
| `order_book_snapshot`, `fix122_evaluation`, `buy_htf_assessment_audit`, `execution_opportunity` | diagnostic/context only | best-effort, rollback-surviving, or overwritten evidence cannot prove parity unless an immutable as-of snapshot exists. |
| sentiment/fundamental tables | analysis input only | require collection/availability time and versioned provider/config snapshot; otherwise refuse `ANALYSIS_PARITY`. |

`NON_CANONICAL`, `PROTECTION_NOT_APPLIED`, `PENDING`, and uncertain price events are excluded. No future candle, price, signal, configuration, provider-health value, or account state may be used.

### 3.2 Candle and signal eligibility

For every requested candle, Replay must apply the same 210-closed-candle data-quality rule and validate snapshot identity **after** construction. Expected close is `open_time + interval - 1 ms`. Open, backfill-ambiguous, missing, or identity-mismatched rows refuse parity. BTC and higher-timeframe visibility use the same signal-mode-specific as-of clock as the stored Production decision; if ordering cannot be reconstructed, parity is refused.

Recovery/backfill may make a candle present later. It cannot retroactively prove that it was available to Production at the original decision time.

### 3.3 Price coverage and fill policy

For a comparable decision output, each position-dependent decision span requires fresh accepted price evidence. Source check: Production resolves execution prices (entries and progressive adds) in `ExecutionPriceAuthorityService.resolve`, which accepts a price only if it is no older than `MAX_EXECUTION_PRICE_AGE`, 15 seconds. The Replay-scope branch of the same method applies the same 15-second limit to the Replay price timeline. That rule covers entry and add prices only. E02 must state which freshness rule governs protection (exit) spans, because the protection price stream is filtered by event status, not by this 15-second limit. A span with no usable price event is refused; it is never silently represented by one `EXACT_PRICE_REPLAY` flag for the entire run.

No candle-price fill assumption is part of this parity baseline. A future counterfactual research run may introduce one only under a separately approved design and must not claim parity.

The existing signal-price fallback is removed from the Replay baseline. It is reachable only for runs with **zero** price events, and does not execute the full Production exit order. Tests must use the actual Production signal-path sequence: advisory position management, TP, stop loss, rebase floor, profit lock, normal SELL validation, near-TP, then add suppression/handling as applicable. No rule order may be invented from the old Replay fallback.

### 3.4 Known Production/Replay differences register (source comparison)

Production's price path (`LivePositionProtectionService.onPrice`) and Replay's price path (`evaluateLivePrice`) follow the same exit order. The differences below are in the Production signal path (`PaperTradingService.processSignalBody`), in timing, and in Replay-only logic. Each row must be closed in E02 as **Replay change**, **Production-proven equivalent**, or **declared non-comparable**.

| ID | Difference | Production | Replay (current) | Required disposition |
|---|---|---|---|---|
| D01 | SELL signals that can close a position | SELL or STRONG_SELL on 1m, 5m or 1h, through `validateSell` | Only 1m SELL or STRONG_SELL | Replay change |
| D02 | Profit-lock suppression of SELL signals | Active profit lock ignores the SELL and holds | No such check before `validateSell` (confirmed by two independent source reads) | Replay change |
| D03 | Exits at the signal price | Signal path also checks TP, profit lock, stop loss, hard floor at the candle's signal price | Skipped in exact-price mode | Replay change, or proof that the price stream always acts first |
| D04 | TP at signal price | Closes as TAKE_PROFIT directly, no continuation or extension | Not evaluated at signal price | Same as D03 |
| D05 | Signal visibility to the price stream | Newest stored signal at processing time | Visible from `generatedAt` | Measure `created_at` minus `generated_at` per interval; use Production's availability time |
| D06 | Near-TP clock | `Instant.now()` at processing | Price event `observedAt` | One declared rule plus measured outcome impact |
| D07 | Entry price | Fresh price resolved at `Instant.now()` | Resolved at the signal's `generatedAt` | Same as D06 |
| D08 | Entry wake-up | Setup and confirmed-setup wake-ups tried on every signal | Wake-ups only on 5m; 1m goes to `evaluateBuy` | Verify whether the wake-up can fire on 1m; Replay change if so |
| D09 | Progressive adds | Only on 1m and only without an active profit lock (`PaperTradingService`); `evaluateBuy` is not called while the lock is active, so opportunity memory is not touched | No profit-lock check on the add path (confirmed by two independent source reads) | Replay change: skip the add evaluation while the lock is active |
| D10 | Failed mechanical exit | `executeMechanicalExit` can return false and the position stays open | Always exits | Declared non-comparable (wallet-driven) |
| D11 | Near-TP partial harvest skip | Skipped below Binance minimum for the real quantity | Same check, fixed-notional quantity | Declared non-comparable (quantity-driven) |
| D12 | Replay-only logic | None | Zero-event signal-price fallback, FIX-11K and FIX-11Q observers, run-wide exact/fallback boolean | Remove (Section 5.1) |
| D13 | Wallet inputs | Real wallet | `wallet_settings`, cash, daily budget, max positions, sizing | Remove (Section 4) |
| D14 | Profit-lock calculation | `DynamicProfitLockService.evaluatePrice`, persists state | Replay-local `profitLockState` over `ProfitLockPolicy` | Not yet compared; E02 must show equal outputs on shared fixtures |
| D15 | Same-timestamp signal order | Order of arrival at the processing queue | Sorted by `generatedAt`, then 1h, 5m, 1m (`intervalOrder`) | Declared rule; E07 measures how often signals share a timestamp and whether arrival order differed |
| D16 | Fallback exit order (`evaluateExit`) | Signal path order: TP, profit lock, stop loss, hard floor, SELL (suppressed while the profit lock is active). No normal HTF exit on the signal path | Fallback order: TP, stop loss, rebase floor, profit lock, normal HTF exit, then 1m `validateSell` | Remove with D12; do not reuse this order anywhere in the baseline |
| D17 | Experimental-only branches inside Production classes | `RegimeStateService.apply` returns the detected regime at once; `EntryAuthorityService.evaluate` always returns the normal decision | When an `EXPERIMENTAL` scope is active: N-candle regime persistence, and a breakout-transition probe capped near 25% | Not reachable today (both Replay entry points open the scope in `PRODUCTION_PARITY` mode; only a test opens `EXPERIMENTAL`). Keep unreachable and prove it with a guard test. Production cleanup is out of scope for FIX-156 |
| D18 | Replay-aware as-of branches in shared Production classes | Read the database at decision time | Read the Replay timeline when a scope is active: `BtcMarketContextService`, `MultiTimeframeConfluenceService`, `OpportunityConsolidationService`, `ExecutionIntelligenceService` (opportunity memory), `TradeExecutionValidationService`, `ExecutionPriceAuthorityService`, `EntryConsumptionPolicy`, `BuyHtfAssessmentAuditStore` (skips writes) | Not retired. E02 must show equivalent output on shared fixtures. `EntryConsumptionPolicy` depends on whether a BUY executed in the wallet, so in a Replay seeded at Production's recorded entries it must follow Production's recorded executions, not Replay's own |
| D19 | Replay-only profilers | None | `ReplayFreshSignalProfiler` (FIX-11L) and `analysisService.beginReplayAnalysisProfiling` (FIX-11M) | Declared observation-only. Baseline run must show identical output with them on and off, or disable them |
| D20 | Entry time | `openedAt`, `executedAt` and the managed position's `openedAt` are `Instant.now()` at execution | In-memory `entryTime` is the signal's `generatedAt`; the stored Replay rows use the fresh price's `observedAt`. The two disagree, and for a 5m wake-up the in-memory time can be up to 45 seconds before the trigger. It feeds the rule that decides whether a signal price may be used after entry | Replay change: one entry time, taken from Production's recorded execution time |
| D21 | Opportunity state after an add | `markExecuted` is called with the decision from before price revalidation | Called with the revalidated decision | Replay change (small): pass the same decision object as Production |
| D22 | Profit-lock enable flag on TP extension | `onTakeProfitExtended` evaluates with the lock enabled | `rebaseForExtendedTarget` passes the admin toggle `isDynamicProfitLockEnabled()` | Replay change (edge case when the toggle is off) |
| D23 | Advisory position management | `PositionManagementService.analyze(signal)` runs first on every signal and can apply a position stop loss | Not mirrored; Replay never calls it | Verify in E02 whether it can close a position that the price stream would not; Replay change if so |
| D24 | Run-time configuration | Profit-lock percentages and toggles are read from `wallet_settings` row 1 at each evaluation | The same row is read at Replay time, so a settings change after the original decision silently changes Replay | Profile must carry the settings in force at the original decision time; refuse when the history is not available |

### 3.5 Signal-generation differences register (source comparison)

Replay and Production both call `AnalysisService.buildSignal`. The differences are in the inputs and the clock passed to it. Each row must be closed in E08 as **Replay change**, **equivalent on fixtures**, or **non-comparable (excluded from the 99% denominator and counted)**.

| ID | Difference | Production | Replay (current) | Disposition |
|---|---|---|---|---|
| S01 | Signal clock | Live `generatedAt` is `Instant.now()` a short time after the candle close; recovery uses the close time | `evaluationTime = open time + interval` | Non-comparable for live-origin rows, or allow a measured latency tolerance |
| S02 | Higher-timeframe and BTC context | Read from the database at processing time; which same-minute signals exist is a race between workers | Read from the Replay timeline; at a given close the 1m always sees the 5m, 1h and BTC signals of that close | Non-comparable; Replay is a deterministic idealisation |
| S03 | Derivatives positioning | Live Binance futures call, not persisted | Historical mode returns unavailable or not applicable; error-path confidence adjustment is -10 in Production and 0 in Replay | Non-comparable |
| S04 | Order book / liquidity | In-memory samples, live fetch when empty | Persisted observations in the window; unavailable when none, where Production returns insufficient-data hold | Equivalent only where persisted sample rate matches the live window; otherwise non-comparable |
| S05 | Previous indicator snapshot | Stored `technical_indicator` row for the previous candle | Same query, but the current snapshot is recomputed and never stored | Replay change: pass a previous snapshot recomputed from the dataset; or equivalent on fixtures from the same code version |
| S06 | Data-quality gate | Candle-close worker validates the latest 210 closed candles (gaps, invalid OHLC) and skips the signal; backfilled candles are not analysed | No such gate; Replay analyses every candle in the database | Replay change if row-set parity matters; otherwise report extra rows as non-comparable |
| S07 | Open (unclosed) candles | Only closed candles | The helper that lists candles for the timeline has no closed filter; the dataset loader does filter. Which one feeds the timeline was not fully traced | Verify in E08; Replay change: filter to closed candles |
| S08 | Signal metadata | `signal_mode` is LIVE, RECOVERY_EXECUTABLE or HISTORICAL_ONLY; `created_at` is set on insert | `analyzeForRegression` never sets `signal_mode`; test row has no `created_at` | Replay change, or exclude these columns from the comparison |
| S09 | Sentiment | Provider health and configuration at decision time | Provider weights and flags read at Replay time; every Replay signal counts as historical | Non-comparable unless provider health and configuration history are persisted |
| S10 | Intervals | Production analyses whatever intervals are configured, possibly including 15m, 4h and 1d | Replay timeline holds only 1m, 5m and 1h (verified); context from other intervals is missing | Verify the configured intervals in E08; Replay change or non-comparable |
| S11 | Warm-up | Full signal history | 3 hours of seeded context | Compare only rows after the warm-up, or lengthen it |
| S12 | Configuration | Scoring, BTC, sentiment, fundamental, derivatives and order-book properties as deployed at the time | The same properties as deployed now | Non-comparable unless configuration is versioned (profile fingerprint) |
| S13 | Comparison tooling | n/a | `ReplayParityValidator` is not called anywhere in the source (verified). It compares 13 fields and omits scores, stop loss, take profit, price, decision path, time and quality fields. The worker's pass criteria are counts and cadence, not field parity against Production | New comparison component required; this is the 99% metric tool, not a refinement of an existing one |

## 4. Frozen Replay profile and fixed-notional output

Every run stores an immutable Replay-owned profile: fixed notional, strategy/exit thresholds, instrument constraints needed by non-wallet decision rules, strategy toggles, FIX-154 policy version, and all version/config fingerprints. It contains no balance, wallet, cash reserve, sizing, daily-loss, or max-open-position state.

The baseline profile mirrors current non-wallet paper-trading rules with fixed notional and current strategy/configuration snapshot. Any Production decision whose outcome depends on cash, daily limits, max-open-position cap, sizing, reserve, or account history is marked `WALLET_DRIVEN_NOT_COMPARABLE`, excluded from the 99% denominator, and counted in the certificate. Production wallet settings are never consulted.

Replay-local mirrors may not add a second strategy engine. Each mirrored method has a named Production source, input/output fixture, policy-version fingerprint, and regression comparison. A source change invalidates the Replay profile until its fixtures and mirror are reviewed.

## 5. Complete implementation registry

| ID | Replay-only artifact/change |
|---|---|
| RPR7-01 | `RegressionTestService`, requests/controllers: profile/mode propagation, start/cancel/archive/purge lifecycle, no pre-worker business writes. |
| RPR7-02 | `RegressionTestWorker`: bounded named Replay executor, pure qualification before artifacts, per-span evidence coverage, cancellation/restart cleanup. |
| RPR7-03 | `ReplayEvidenceQualifier` + certificate store: frozen baseline/config/evidence hashes, refusal reasons, as-of rules. |
| RPR7-04 | Replay profile + fixed-notional decision-state store: positions, entries, exits, protection levels and constraints; no wallet, balance, sizing or ledger. |
| RPR7-05 | `ReplayExecutionScope`: stable run IDs, clock, price identity/slot, async propagation, forbidden-dependency guard, `finally` cleanup. |
| RPR7-06 | `ShadowProductionReplayService`: remove zero-event signal-price fallback; implement Production-rule mirror order and Replay-only writes. |
| RPR7-07 | Replay-local mirrors/fixtures for non-wallet validation, dynamic lock, protection, position management, exit/profit-lock and FIX-154 assessment. Wallet-driven sizing/admission is classified, not simulated. |
| RPR7-08 | Replay observers: isolate FIX-11Q/defensive-risk observers; baseline never invokes experimental observers. |
| RPR7-09 | `ReplayParityValidator`: earned-label algorithm, required field set/tolerances, signal-mode clocks, explicit mismatch/refusal output. |
| RPR7-10 | run output/UI: correct `analysis_test_run` lifecycle, `analysis_test_signal`, `trade_signal_test`, Replay decision/position/exit output, `position_management_test`, `execution_opportunity_test`, observer output and display-only Production overlays. No `wallet_trade` or `paper_position` read for a Replay run. |
| RPR7-11 | next verified Flyway migration: additive profiles, certificates, staging, price-span coverage, fixed-notional decision-output and archive columns; migration number is chosen only after Flyway preflight. |
| RPR7-12 | configuration/logging/docs/tests listed in Sections 6 to 9. |

No item may modify a Production class. Any discovered required shared-code change stops FIX-156 and returns to design approval.

### 5.1 Retired Replay-only logic to remove from the baseline

These are **not** Production rules. They are old Replay experiments or fallback routes that must not remain in the new baseline because they make Replay behave differently from Production. Their historical rows remain evidence; this change does not delete Production data or edit an applied migration.

| Retired item | Current files / artifacts | Required disposition |
|---|---|---|
| FIX-11K defensive-risk-reduction counterfactual | `DefensiveRiskReductionReplayObserver`, its calls from `ShadowProductionReplayService`, controller endpoints, and `defensive_risk_reduction_observation_test` / archive display | Remove from the baseline execution graph. Preserve existing records as read-only historical research. If retained for future research, move it to a separately selected `EXPERIMENTAL` run type with its own label; it cannot start, observe, write, or affect a baseline run. |
| FIX-11Q one-candle continuation-grace counterfactual | `OneCandleContinuationGraceReplayObserver`, its `startRun/onPrice/onSignal/finishRun` calls, controller/archive endpoints, and `one_candle_continuation_grace_test` / archive display | Remove from the baseline execution graph and fixed-notional output. Preserve historic observations read-only. A future experiment requires a separate approved design and explicit experimental profile. |
| Pre-V64 / signal-price fallback execution | `ShadowProductionReplayService` fallback block, `evaluateNearTpAtSignalFallback`, `FallbackNearTpEvaluation`, and related fallback log messages | Delete/replace in the Replay redesign. It is a zero-price-event route, not a safe price-path substitute, and does not execute the full Production exit order. Do not remove Production FIX-11T; only remove the Replay-local legacy fallback. |
| Run-wide `EXACT_PRICE_REPLAY` versus `SIGNAL_PRICE_FALLBACK` label | `ShadowProductionReplayService` and `RegressionTestWorker` replay-price-mode persistence | Retire these misleading run-wide labels. Replace them with the earned labels in Section 1 plus per-span `replay_price_coverage`. Existing historical labels remain visible as legacy metadata only. |
| Replay `EXPERIMENTAL` scope as a baseline capability | `ExecutionReplayScope.ReplayLogicMode.EXPERIMENTAL` and automatic observer invocation | The enum value cannot be removed in FIX-156, because Production classes (`RegimeStateService`, `EntryAuthorityService`) read it. Instead: baseline entry points may open the scope only in `PRODUCTION_PARITY` mode; add a test that no baseline path can open `EXPERIMENTAL` and that both classes behave as Production while a `PRODUCTION_PARITY` scope is active. Remove automatic observer activation from the worker/service. If a future experimental scope survives, it must be a separately authorised entry point and cannot share baseline output/certificates. Deleting the dead branches is a separate Production change. |
| Legacy continuation/defensive archive maintenance as core run behavior | `RegressionTestService` archive/purge lists and summary counts | Remove these objects from normal baseline run, archive, purge, and dashboard summaries. Keep a read-only legacy archive viewer until retention expires; delete tables only through a separate, approved forward migration and retention plan. |

**Removal safety rules:** remove invocation and baseline write paths first; migrate/disable UI routes next; retain historical rows and read-only viewers; then consider schema cleanup only in a later approved migration. Never delete a table, archive, or prior run merely to make the new Replay route compile.

## 6. Lifecycle, staging and resources

### 6.1 Required technical change map

| Area | Current technical issue | Required Replay-only change |
|---|---|---|
| `RegressionTestService.start(...)` and run controllers | A run and FIX-122 revision can be written before the async worker qualifies evidence. Chart output can read `wallet_trade`. | Persist only a request/certificate shell at start. Move all result writes after qualification. Route chart/timeline endpoints to Replay-owned output only; prohibit a Replay request from querying `wallet_trade` or `paper_position`. |
| `RegressionTestWorker` | It creates generated artifacts before its late price-evidence check and uses an unspecified `@Async` executor. | Add `ReplayEvidenceQualifier.qualify(request)` before signal/result generation. Use a named bounded Replay executor. Carry profile, baseline hash, mode, scope and cancellation token into the worker. Enforce per-span rather than run-wide price coverage. |
| `ReplayDataset` / candle loading | Replay can process an open row and can re-analyse the preceding closed candle with later context. | Load only exact closed identities. After every indicator snapshot build, compare returned candle symbol, interval, open and expected close with the requested identity. Apply `CandleDataQualityService` semantics as of that decision point. |
| `ExecutionReplayScope` | Scope is a `ThreadLocal`; a missing scope can fall through to Production account reads/writes. It does not carry canonical price identity. | Add immutable run/profile/baseline/clock state, accepted price-event id/status/ordering-slot, cancellation marker and a scope-violation flag. Propagate it to every Replay-owned async task; `finally` clears it. Absence or an attempted account/HTTP dependency raises `ReplayForbiddenDependencyException`. |
| `ShadowProductionReplayService` | It reads `wallet_settings`; `exactPriceReplay` is one run-level boolean; its signal-price fallback runs only when a run has no price events; price path omits some Production exits/lock suppression. | Replace settings with `ReplayProfile`. Replace the boolean with `ReplayPriceCoverage` per decision span. Remove the fallback and use the documented Production-rule mirror order. Never call a repository/service that reads an account table or HTTP. |
| Replay policy mirrors | Production services such as validation, sizing, minimum execution, protection, paper trading and position exit have account/HTTP/transaction dependencies. | Introduce Replay-local `*PolicyMirror` classes with explicit inputs only: immutable profile, as-of market evidence and Replay decision-position state. Wallet-driven decisions are classified `WALLET_DRIVEN_NOT_COMPARABLE`, not mirrored. Each mirror names its Production source method and has recorded-Production-decision fixtures proving equivalent outputs for eligible inputs. No modification of the Production class is allowed. |
| `OneCandleContinuationGraceReplayObserver` and other observers | The observer reads wallet settings and writes experimental output during baseline runs. | Supply the immutable Replay profile; isolate observer writes in Replay tables. Baseline profiles do not instantiate/invoke experimental observers. |
| `ReplayParityValidator` | It compares a small, non-versioned field subset and has no earned-claim/refusal algorithm. | Require a versioned comparison schema, mandatory fields, tolerances, source/config identity, outcome/signal-mode timing, and a structured mismatch certificate. It may award only `ANALYSIS_PARITY`, never lifecycle/account parity. |
| `MarketPriceEventService` projection / Replay price model | The current `PriceEvent` projection loses durable id/status/ordering slot needed for equal-time ordering and audit. | Add a Replay-owned projection/DTO retaining these fields. Do not change ingestion. Query accepted events in `(observed_at, id)` order and record the event id/slot used for every fixed-notional decision/protection evaluation. |
| Sentiment/fundamental/context evidence | Current historical rows do not always retain collection time, provider health/config or immutable context. | Add a qualifier capability matrix. If an input cannot be reconstructed as of the decision time, refuse `ANALYSIS_PARITY`; the run is refused for `ANALYSIS_PARITY`. No substituted input is allowed in the baseline. |
| FIX-154 integration | Source includes higher-timeframe BUY assessment, but historical inputs/audit counters must not be mutated by Replay. | Replay profile carries frozen FIX-154 policy/config identity. Read only the eligible stored evidence. Route counters/audit output to Replay-owned records; prove no Production counter/audit write occurs. |
| Run lifecycle / archive / purge | Current run data can be incrementally visible and run IDs/output references can drift after purge. | Add immutable certificate/profile references, staging ownership key, terminal status, archive state, cancellation reason and retention policy. Promotion is atomic; purge never reuses a public run identity or deletes certificate lineage. |

### 6.2 Required schema and configuration changes

The next verified Flyway migration must be additive and contain, at minimum:

- `replay_profile`: immutable fixed-notional decision-profile configuration, profile hash and source policy version; no balance/ledger fields.
- `replay_run_certificate`: requested/earned label, baseline/config hashes, evidence counts/IDs, refusal/mismatch reasons, UTC timestamps and KSA display conversion metadata.
- Replay staging/promotion ownership columns or dedicated staging tables for every Replay result table.
- `replay_price_coverage`: one row per decision span, recording canonical event identity/order or the refusal reason. No price assumption exists in the baseline.
- Replay-only audit tables for fixed-notional entries/exits, decision positions, policy decisions, experimental observers and FIX-154 equivalent assessment evidence.
- Archive/retention indexes and unique keys that preserve run identity across cancellation/purge.

Configuration must name: the bounded Replay executor, one-run capacity, queue capacity, connection/query budget, maximum window/candle count, supported profile modes, fixed notional, debug decision logging, and the numeric Production-protection health thresholds used by the load test.

1. `start` creates only a minimal run request/certificate record; no FIX-122 revision, signal, trade, observer, or result write occurs before qualification.
2. Qualification takes a read-consistent evidence snapshot and produces qualified spans or a terminal refusal.
3. The worker writes only Replay staging rows. A cancellation, crash, invariant failure, or forbidden dependency discards staging and writes one terminal diagnostic.
4. Promotion publishes all terminal Replay output atomically. Archive/purge preserve certificate/profile/baseline hashes and cannot reuse a run identity.

Use a named, single-worker Replay executor with a bounded queue of one. Specify and test a database connection/query budget. A run is rejected when capacity is full. Under load, Production protection latency and `STALE_PRICE`/non-canonical rates must not worsen beyond an approved numeric threshold.

## 7. Earned parity algorithm

`ANALYSIS_PARITY` is earned only when the frozen source/config policy identity matches, all mandatory as-of evidence exists, candle/data-quality/signal-mode timing passes, and the validator compares the versioned mandatory field set (decision, raw/effective confidence, context/statuses, regime, strategy, entry permission, reason and outcome). Tolerances must be documented and versioned; missing liquidity, derivatives or sentiment uses the stored Production output, not a new live/historical approximation.

The FIX-156 outcome target is **at least 99% agreement among qualified, comparable decisions**. A unit is one Production-recorded signal decision or position outcome. It passes only when the versioned comparison agrees on entry versus no entry, exit versus hold, exit reason, exit timestamp, and exit price within the frozen tolerance. Wallet-driven cases, insufficient-evidence cases and refused cases are excluded from the denominator and reported separately; none may be counted as a match. Every disagreement has a one-line classified cause. Average P&L is not a substitute for this decision-level metric.

**Validity conditions (frozen before the first measured run):** (a) a minimum coverage, meaning comparable cases as a share of all Production decisions in the window, reported next to the agreement rate; (b) a minimum number of comparable cases; (c) a lower confidence bound on the agreement rate, and the 99% target is judged on that bound, not the raw percentage; (d) the numeric values are set by the team from E06 evidence and approved by the owner. A result that fails (a) or (b) is reported as **not evaluable**, never as a pass.

**Unit of comparison — recorded in Appendix C, item 1, awaiting owner confirmation at freeze:** Replay is seeded at Production's recorded entries, so each position's lifecycle is compared from the same entry onward and a wallet-driven BUY rejection in Production cannot cascade into later differences. A free-running lifecycle is not permitted while Replay has no wallet evidence. Appendix C items 2 and 3 also bind this section: thresholds are frozen before the final evaluation window, and D14 is blocking.

The certificate records baseline source hash, deployed-policy/JAR hash when known, config/profile hash, migration range, evidence IDs/counts, per-span price coverage, selected label, refusals, duration, executor utilisation, and UTC/KSA display timestamps. A source/deployed-JAR mismatch prevents a claim about deployed Production.

## 8. Mandatory validation

Tests must cover: account/HTTP/fall-through guard; async guard propagation; open/missing/identity-mismatched/backfilled candle; as-of 210-candle quality; same-boundary BTC/HTF visibility; price id/slot/equal-time ordering; missing/partial price spans; Replay-only SELL versus Production signal-path TP/SL/floor/lock/SELL/add order; profit-lock suppression; wallet-driven exclusion; cancellation/crash/restart/staging/promotion/archive/purge; observer isolation; FIX-154 counters unaffected; API/UI overlay isolation; executor/DB/protection-load limits; MySQL migration fresh/upgrade/V103 policy; and baseline/config hash mismatch.

Before any implementation approval, run bounded read-only measurements for price-event coverage by symbol/day, existing fallback-run count, warm-up sufficiency, and evidence-eligibility rate. If coverage is inadequate, refusal rates must be reported rather than hidden.

### 8.1 Pre-implementation evidence package — required for approval

The following eight deliverables must be attached to the next design-review request. They are read-only analysis artifacts, not implementation approval.

| Deliverable | Required content | Acceptance condition |
|---|---|---|
| E01 — dependency audit | A transitive dependency graph from every Regression Replay entry point and async child task to every Production service, repository, HTTP client, executor and table. Mark every direct/indirect account read/write and every possible fall-through when scope propagation is absent. | Each reachable dependency is classified: permitted evidence, Replay replacement, display-only, or forbidden. No unclassified account/HTTP path remains. |
| E02 — Production decision-order matrix | Method-level order for BUY admission, sizing, entry-price revalidation, position management, TP, SL, rebase floor, profit lock, SELL validation, near-TP and add handling. Include the current Replay order and each difference. | For every decision, the matrix names source class/method, exact input evidence and timestamp, position/protection state, strategy/context, decision precedence, state transition, output (`BUY`/`SELL`/`HOLD`/rejection), transaction boundary, and required Replay-mirror fixture. Matching method names alone is insufficient. The matrix must close every row of Section 3.4 (D01–D24), and must cover both the Production price path and the Production signal path. |
| E03 — historical evidence coverage report | Canonical accepted (`LEGACY`/`APPLIED`) price-event coverage by symbol and position-dependent span; gap lengths; eligible/refused-rate projection; count of current `SIGNAL_PRICE_FALLBACK` runs and their trade count. | Results are bounded, reproducible, timestamped and stored with the design review. A non-empty event list is never used as a coverage proxy. |
| E04 — policy-version invalidation contract | How the frozen source/config/policy fingerprint is generated, stored in profile/certificate, compared at run start, and invalidated after a Production policy/config change. | A changed fingerprint refuses a parity-labelled run until the mirror fixtures and profile are explicitly re-approved. |
| E05 — resource-isolation plan | Numeric maximums for Replay workers, queue, CPU/concurrency, duration/window, DB connections/queries, and measured Production protection-health thresholds. | Load validation proves Replay cannot exceed its budget or materially worsen protection latency, `STALE_PRICE`, or rejected/non-canonical price outcomes. |
| E06 — 99% baseline measurement | Apply the frozen decision-level metric to existing Production output; include agreement, exclusions and mismatch classifications. | Reports comparable-case count, agreement rate, wallet-driven count, refused count and every mismatch cause; it never substitutes aggregate P&L. |
| E07 — signal availability gap | For 1m, 5m and 1h signals: distribution of persistence time minus `generated_at`, per symbol and day, and the field-to-availability mapping for each timestamp. | Shows whether Replay's "visible at `generatedAt`" differs from what Production saw, and by how much. Needed to close D05. |
| E08 — signal-input reconstructability | For every row of Section 3.5 (S01–S13): which inputs are persisted and at what rate (derivatives, order book, sentiment provider health, configuration history), the intervals Production actually analyses, which candle list feeds the Replay timeline, and the count of Production signals affected. | Each S row is closed as Replay change, equivalent, or non-comparable, with the share of Production decisions it removes from the 99% denominator. |

The review also confirms this implementation boundary: Replay-local mirrors are permitted only as a temporary, version-matched solution. A future shared, side-effect-free Production decision-policy layer may be designed separately, but is explicitly out of scope for FIX-156 and cannot be introduced during this implementation.

Replay parity never asserts that a historical order would have filled at the Replay output price. This is especially important for a price-path-dependent continuation experiment such as FIX-155: FIX-155 remains Replay research only and cannot justify a Production trading-policy change until FIX-156's qualified baseline Replay has been implemented, validated and independently reviewed.

### 8.2 Delivery stages

**Stage 1 — decision-comparison baseline:** remove the zero-event signal-price fallback and automatic FIX-11K/FIX-11Q observers from baseline; remove all `wallet_settings` reads; introduce fixed-notional profile, per-span price coverage, price-event identity projection, Production-order mirror fixtures, 99% metric and mismatch report.

**Stage 2 — operational hardening:** certificates, staging/promotion, archive/purge lifecycle, resource-isolation load validation, and UI/display-overlay conversion. Stage 2 is required before release; the split only makes the delivery order reviewable.

## 9. Operational documentation

Successful implementation must update `docs/ARCHITECTURE.md`, `docs/CHANGELOG_AI.md`, `docs/TRADING_RULES.md`, and `docs/INVESTIGATION_LOG.md` with the final implemented state. It must also correct the FIX registry only after the code and validation actually exist.

## 10. Approval gate

This file must be copied/reconciled to the canonical `agent-docs/FINAL_CHANGE_SPECIFICATION.md`, then dual-reviewed as the identical frozen revision. A user must explicitly approve the reviewed revision before implementation. No current source code, schema, runtime configuration, or production data is authorised to change by this document.

## Appendix B — Changes from RPR-0.7-DRAFT

1. Revision label changed to RPR-0.8-DRAFT.
2. Removed leftover simulation and price-assumption wording (Section 5.1 baseline modes, Section 6.1 sentiment row, Section 6.2 price coverage, Section 8, E03).
3. Price-authority freshness rule: located in `ExecutionPriceAuthorityService` (15 seconds, entries and adds only); Section 3.3 updated. Protection-span freshness still to be stated in E02.
4. Added Section 3.4, the known-differences register D01–D14 (extended to D19 in item 10), from a source comparison of Production's price path and signal path with Replay.
5. E02 extended to close every register row.
6. Section 7: added validity conditions (coverage, minimum count, lower confidence bound, not-evaluable result) and an owner decision on the comparison unit.
7. Added E07 (signal availability gap); evidence package is now seven deliverables.
8. RPR-0.8.1: the circulated copy of 0.8 had lost the number after the section sign in several places and a block of text (end of Section 5.1 table, the "Removal safety rules" paragraph, the Section 6 and Section 6.1 headings and the first rows of the Section 6.1 table). Restored from the clean 0.8; Appendix C kept as written; Section 7 owner-decision wording aligned with Appendix C; E06 and E07 reordered.
9. RPR-0.8.2: the section sign followed by a number was stripped again in the circulated 0.8.1 copy, in the same places (the content block itself arrived intact). All section references are now written as the word "Section", which survived transfer. No other text changed.
10. RPR-0.8.3: source review of Replay-only logic not present in Production. Added D15–D19 to the register, corrected the `EXPERIMENTAL` row in Section 5.1 (the enum cannot be removed without editing Production classes), and recorded the 15-second rule's location and scope.
11. RPR-0.8.4: three parallel source reviews (signal generation, BUY admission and entry, position and profit-lock state), with the main points spot-checked. Added Section 3.5 (S01–S13), D20–D24, confirmed D02 and D09 as real differences, and added E08. Finding: the profit-lock lock price and trigger arithmetic is the same in both paths (both call `ProfitLockPolicy.evaluate` with the same arguments), so D14 is lower risk than the other rows but stays blocking until fixtures show equal output.


## Appendix C — Consolidated review decisions

1. **Lifecycle comparison unit:** seed Replay from each Production-recorded entry, then compare that position's protection and exit lifecycle. Measure signal and BUY-decision parity separately. A free-running entry-to-exit lifecycle is not permitted while Replay has no wallet evidence, because wallet-driven entry differences would cascade into later results.
2. **99% acceptance metric:** E06 may be a pilot used to propose coverage, comparable-count and confidence-interval thresholds. The numeric thresholds, the confidence-interval method and a separate final evaluation window must be frozen and owner-approved **before** the acceptance measurement; they may not be tuned after inspecting that final result.
3. **D14 is blocking:** the dynamic profit-lock calculation/state must match Production on recorded shared fixtures before any lifecycle-parity result can pass.
4. **PDF/Markdown source:** this project copy is made from the owner-provided RPR-0.8 Markdown counterpart. The PDF is retained as the presentation artifact; no PDF layout change is implied here.


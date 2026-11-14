# FIX-156 — Replay / Production Business-Logic Parity

**Revision:** RPR-0.7-DRAFT  
**Status:** NOT APPROVED — IMPLEMENTATION FORBIDDEN  
**Date:** 2026-10-10  
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
| `trade_signal`, `candle_analysis_outcome`, `signal_processing_work` | stored outcome/oracle | compare only fields and signal modes explicitly versioned by §7. |
| `market_price_event` | canonical execution/protection price timeline | accept only `LEGACY`/`APPLIED`; retain event id, `observed_at`, ordering slot and status. Coverage is per span, not a run-wide flag. |
| `order_book_snapshot`, `fix122_evaluation`, `buy_htf_assessment_audit`, `execution_opportunity` | diagnostic/context only | best-effort, rollback-surviving, or overwritten evidence cannot prove parity unless an immutable as-of snapshot exists. |
| sentiment/fundamental tables | analysis input only | require collection/availability time and versioned provider/config snapshot; otherwise refuse `ANALYSIS_PARITY`. |

`NON_CANONICAL`, `PROTECTION_NOT_APPLIED`, `PENDING`, and uncertain price events are excluded. No future candle, price, signal, configuration, provider-health value, or account state may be used.

### 3.2 Candle and signal eligibility

For every requested candle, Replay must apply the same 210-closed-candle data-quality rule and validate snapshot identity **after** construction. Expected close is `open_time + interval - 1 ms`. Open, backfill-ambiguous, missing, or identity-mismatched rows refuse parity. BTC and higher-timeframe visibility use the same signal-mode-specific as-of clock as the stored Production decision; if ordering cannot be reconstructed, parity is refused.

Recovery/backfill may make a candle present later. It cannot retroactively prove that it was available to Production at the original decision time.

### 3.3 Price coverage and fill policy

For a comparable decision output, each position-dependent decision span requires fresh accepted price evidence using Production's 15-second authority rule. A span with no usable price event is refused; it is never silently represented by one `EXACT_PRICE_REPLAY` flag for the entire run.

No candle-price fill assumption is part of this parity baseline. A future counterfactual research run may introduce one only under a separately approved design and must not claim parity.

The existing signal-price fallback is removed from the Replay baseline. It is reachable only for runs with **zero** price events, and does not execute the full Production exit order. Tests must use the actual Production signal-path sequence: advisory position management, TP, stop loss, rebase floor, profit lock, normal SELL validation, near-TP, then add suppression/handling as applicable. No rule order may be invented from the old Replay fallback.

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
| RPR7-12 | configuration/logging/docs/tests listed in §§6–9. |

No item may modify a Production class. Any discovered required shared-code change stops FIX-156 and returns to design approval.

### 5.1 Retired Replay-only logic to remove from the baseline

These are **not** Production rules. They are old Replay experiments or fallback routes that must not remain in the new baseline because they make Replay behave differently from Production. Their historical rows remain evidence; this change does not delete Production data or edit an applied migration.

| Retired item | Current files / artifacts | Required disposition |
|---|---|---|
| FIX-11K defensive-risk-reduction counterfactual | `DefensiveRiskReductionReplayObserver`, its calls from `ShadowProductionReplayService`, controller endpoints, and `defensive_risk_reduction_observation_test` / archive display | Remove from the baseline execution graph. Preserve existing records as read-only historical research. If retained for future research, move it to a separately selected `EXPERIMENTAL` run type with its own label; it cannot start, observe, write, or affect a baseline run. |
| FIX-11Q one-candle continuation-grace counterfactual | `OneCandleContinuationGraceReplayObserver`, its `startRun/onPrice/onSignal/finishRun` calls, controller/archive endpoints, and `one_candle_continuation_grace_test` / archive display | Remove from the baseline execution graph and fixed-notional output. Preserve historic observations read-only. A future experiment requires a separate approved design and explicit experimental profile. |
| Pre-V64 / signal-price fallback execution | `ShadowProductionReplayService` fallback block, `evaluateNearTpAtSignalFallback`, `FallbackNearTpEvaluation`, and related fallback log messages | Delete/replace in the Replay redesign. It is a zero-price-event route, not a safe price-path substitute, and does not execute the full Production exit order. Do not remove Production FIX-11T; only remove the Replay-local legacy fallback. |
| Run-wide `EXACT_PRICE_REPLAY` versus `SIGNAL_PRICE_FALLBACK` label | `ShadowProductionReplayService` and `RegressionTestWorker` replay-price-mode persistence | Retire these misleading run-wide labels. Replace them with the earned labels in §1 plus per-span `replay_price_coverage`. Existing historical labels remain visible as legacy metadata only. |
| Replay `EXPERIMENTAL` scope as a baseline capability | `ExecutionReplayScope.ReplayLogicMode.EXPERIMENTAL` and automatic observer invocation | Baseline runs allow only the new production-logic/analysis/refusal modes. Remove automatic experimental activation from the worker/service. If a future experimental scope survives, it must be a separately authorised entry point and cannot share baseline output/certificates. |
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
| Sentiment/fundamental/context evidence | Current historical rows do not always retain collection time, provider health/config or immutable context. | Add a qualifier capability matrix. If an input cannot be reconstructed as of the decision time, refuse `ANALYSIS_PARITY`; simulation may proceed only if the profile explicitly declares the substituted input and receives a non-parity label. |
| FIX-154 integration | Source includes higher-timeframe BUY assessment, but historical inputs/audit counters must not be mutated by Replay. | Replay profile carries frozen FIX-154 policy/config identity. Read only the eligible stored evidence. Route counters/audit output to Replay-owned records; prove no Production counter/audit write occurs. |
| Run lifecycle / archive / purge | Current run data can be incrementally visible and run IDs/output references can drift after purge. | Add immutable certificate/profile references, staging ownership key, terminal status, archive state, cancellation reason and retention policy. Promotion is atomic; purge never reuses a public run identity or deletes certificate lineage. |

### 6.2 Required schema and configuration changes

The next verified Flyway migration must be additive and contain, at minimum:

- `replay_profile`: immutable fixed-notional decision-profile configuration, profile hash and source policy version; no balance/ledger fields.
- `replay_run_certificate`: requested/earned label, baseline/config hashes, evidence counts/IDs, refusal/mismatch reasons, UTC timestamps and KSA display conversion metadata.
- Replay staging/promotion ownership columns or dedicated staging tables for every Replay result table.
- `replay_price_coverage`: one row per decision span, recording canonical event identity/order or the declared price assumption/refusal.
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

The certificate records baseline source hash, deployed-policy/JAR hash when known, config/profile hash, migration range, evidence IDs/counts, per-span price coverage, selected label, refusals, duration, executor utilisation, and UTC/KSA display timestamps. A source/deployed-JAR mismatch prevents a claim about deployed Production.

## 8. Mandatory validation

Tests must cover: account/HTTP/fall-through guard; async guard propagation; open/missing/identity-mismatched/backfilled candle; as-of 210-candle quality; same-boundary BTC/HTF visibility; price id/slot/equal-time ordering; missing/partial price spans; Replay-only SELL versus Production signal-path TP/SL/floor/lock/SELL/add order; profit-lock suppression; wallet-driven exclusion; cancellation/crash/restart/staging/promotion/archive/purge; observer isolation; FIX-154 counters unaffected; API/UI overlay isolation; executor/DB/protection-load limits; MySQL migration fresh/upgrade/V103 policy; and baseline/config hash mismatch.

Before any implementation approval, run bounded read-only measurements for price-event coverage by symbol/day, existing fallback-run count, warm-up sufficiency, and evidence-eligibility rate. If coverage is inadequate, refusal/assumption rates must be reported rather than hidden.

### 8.1 Pre-implementation evidence package — required for approval

The following five deliverables must be attached to the next design-review request. They are read-only analysis artifacts, not implementation approval.

| Deliverable | Required content | Acceptance condition |
|---|---|---|
| E01 — dependency audit | A transitive dependency graph from every Regression Replay entry point and async child task to every Production service, repository, HTTP client, executor and table. Mark every direct/indirect account read/write and every possible fall-through when scope propagation is absent. | Each reachable dependency is classified: permitted evidence, Replay replacement, display-only, or forbidden. No unclassified account/HTTP path remains. |
| E02 — Production decision-order matrix | Method-level order for BUY admission, sizing, entry-price revalidation, position management, TP, SL, rebase floor, profit lock, SELL validation, near-TP and add handling. Include the current Replay order and each difference. | For every decision, the matrix names source class/method, exact input evidence and timestamp, position/protection state, strategy/context, decision precedence, state transition, output (`BUY`/`SELL`/`HOLD`/rejection), transaction boundary, and required Replay-mirror fixture. Matching method names alone is insufficient. |
| E03 — historical evidence coverage report | Canonical accepted (`LEGACY`/`APPLIED`) price-event coverage by symbol and position-dependent span; gap lengths; eligible/refused/assumption-rate projection; count of current `SIGNAL_PRICE_FALLBACK` runs and their trade count. | Results are bounded, reproducible, timestamped and stored with the design review. A non-empty event list is never used as a coverage proxy. |
| E04 — policy-version invalidation contract | How the frozen source/config/policy fingerprint is generated, stored in profile/certificate, compared at run start, and invalidated after a Production policy/config change. | A changed fingerprint refuses a parity-labelled run until the mirror fixtures and profile are explicitly re-approved. |
| E05 — resource-isolation plan | Numeric maximums for Replay workers, queue, CPU/concurrency, duration/window, DB connections/queries, and measured Production protection-health thresholds. | Load validation proves Replay cannot exceed its budget or materially worsen protection latency, `STALE_PRICE`, or rejected/non-canonical price outcomes. |
| E06 — 99% baseline measurement | Apply the frozen decision-level metric to existing Production output; include agreement, exclusions and mismatch classifications. | Reports comparable-case count, agreement rate, wallet-driven count, refused count and every mismatch cause; it never substitutes aggregate P&L. |

The review also confirms this implementation boundary: Replay-local mirrors are permitted only as a temporary, version-matched solution. A future shared, side-effect-free Production decision-policy layer may be designed separately, but is explicitly out of scope for FIX-156 and cannot be introduced during this implementation.

Replay parity never asserts that a historical order would have filled at the Replay output price. This is especially important for a price-path-dependent continuation experiment such as FIX-155: FIX-155 remains Replay research only and cannot justify a Production trading-policy change until FIX-156's qualified baseline Replay has been implemented, validated and independently reviewed.

### 8.2 Delivery stages

**Stage 1 — decision-comparison baseline:** remove the zero-event signal-price fallback and automatic FIX-11K/FIX-11Q observers from baseline; remove all `wallet_settings` reads; introduce fixed-notional profile, per-span price coverage, price-event identity projection, Production-order mirror fixtures, 99% metric and mismatch report.

**Stage 2 — operational hardening:** certificates, staging/promotion, archive/purge lifecycle, resource-isolation load validation, and UI/display-overlay conversion. Stage 2 is required before release; the split only makes the delivery order reviewable.

## 9. Operational documentation

Successful implementation must update `docs/ARCHITECTURE.md`, `docs/CHANGELOG_AI.md`, `docs/TRADING_RULES.md`, and `docs/INVESTIGATION_LOG.md` with the final implemented state. It must also correct the FIX registry only after the code and validation actually exist.

## 10. Approval gate

This file must be copied/reconciled to the canonical `agent-docs/FINAL_CHANGE_SPECIFICATION.md`, then dual-reviewed as the identical frozen revision. A user must explicitly approve the reviewed revision before implementation. No current source code, schema, runtime configuration, or production data is authorised to change by this document.

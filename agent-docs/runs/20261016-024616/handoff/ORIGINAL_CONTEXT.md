# FIX-156 - Replay/Production Parity: Consolidated Preparation and Final Pre-Implementation Review

**Revision:** Consolidated preparation, E01/E02 draft evidence, and final static code review, 2026-10-15  
**Status:** NOT APPROVED FOR IMPLEMENTATION  
**Prepared:** 2026-10-11 KSA  
**Source design:** owner-provided `FIX-156-RPR-0.9.12-DRAFT.md`  
**RPR-0.9.12 SHA-256:** `ddfd5f64a95f6b55ed39e8bffa7b4c93bd65f4e6dd3b23d8bf821500de722b24`  
**Owner decision O-8:** RPR-0.9.12 incorporates option (a), the narrowly scoped `fix122_evaluation` exception in its Section 2.1 and in D48/R156-05/T156-09b.  
**This document review SHA-256:** Pending final freeze; calculate and record it in the review request only after the last content edit.  
**Application/configuration/migration/database/service/deployment changes made by this document:** NONE

## 1. Fixed scope and earned results

FIX-156 changes **Replay only**. It does not change Production source,
behaviour, database writes, account state, schedulers, HTTP traffic, or
latency. Replay is a manual single-symbol research tool with fixed notional
output. It is not a wallet, balance, cash, reserve, budget, portfolio, or
historical-fill simulator.

A run has two independent earned certification dimensions:

- `ANALYSIS_PARITY`: a qualified Production signal/outcome is reproduced
  using as-of evidence.
- `LIFECYCLE_PARITY`: a qualified recorded-entry lifecycle passes the
  separate lifecycle metric and critical-safety gate.

A run may earn neither, one, or both dimensions. `REFUSED_INSUFFICIENT_EVIDENCE`
and `FAILED_REPLAY` are terminal states which prevent either claim.
`FAILED_REPLAY` is only for a technical error, cancellation, or failed
staging. A business mismatch is published as parity-not-earned with its first
divergence; it is not a technical failure.

No result may claim wallet, account, exact-fill, live-scheduling, MySQL-lock,
or Binance-availability parity.

## 2. Decided owner rules

| Decision | Implementable rule |
| --- | --- |
| O-1 | Replay never calls `wallet_settings` or shared `validateBuy`. A Replay-local BUY mirror reads only a frozen policy profile. |
| O-2 | Signal and lifecycle parity are separate certification dimensions. |
| O-3 | Every class reachable from a Replay request or worker is inside the boundary. |
| O-4 | The 99% result is separate per metric, based on a frozen lower confidence bound, coverage floor, and critical-safety gate. |
| O-5 | A lifecycle fixture carries the full recorded timeline, not entry only. |
| O-6 | No separate Replay database identity/datasource is introduced. |
| O-7 | No shared-datasource SQL guard is introduced. Replay safety is application/test enforced, not a database-permission claim. |
| O-8 / D48 | Replay may use `fix122_evaluation` only through the exact run-owned and display-only operations in Section 4.2a. This exception never supplies decision, wallet, account, fill, or Production-trading authority. |

## 3. Blocking preparation gates

| Gate | Required evidence | Status |
| --- | --- | --- |
| P01 | Freeze exact source, configuration, migration, worktree, and RPR-0.9.12 hashes; reconcile that exact revision to `agent-docs/FINAL_CHANGE_SPECIFICATION.md`. | Pending |
| P02 | Complete E01-E10: dependency audit, decision-order matrix, evidence coverage, fingerprints, resource limits, signal availability, reconstructability, fixtures, and historical configuration evidence. | Pending |
| P03 | Resolve all D01-D46, E01 boundary/input rows D47-D56, and S01-S14 rows as Replay change, proven equivalent, refused, or non-comparable. | Pending |
| P04 | Create one complete, exact, approved canonical change specification and registry. | Pending |
| P05 | Obtain required review approvals for that exact frozen specification plus explicit user authority. | Pending |
| P06 | Prove the approved three-layer Replay guard using direct and async tests; set disposable-MySQL and numerical resource limits. | Pending |
| P07 | Keep the development/pilot dataset separate from a later sealed acceptance dataset. After E06, freeze thresholds, confidence-interval method, coverage/comparable minimums, and final period before the acceptance dataset is opened. | Pending |

## 4. Future code-change registry

No item below is permission to edit code. Every future implementation must map
to a registered item.

| ID | Owner / future change | Required behaviour | Canonical log evidence |
| --- | --- | --- | --- |
| R156-01 | `RegressionTestService` and Replay controllers | Qualify before staging; promote only an eligible completed run. | `RUN_REQUESTED`, `RUN_QUALIFIED`, `RUN_REFUSED`, `RUN_FAILED`, `RUN_PROMOTED` |
| R156-02 | `RegressionTestWorker` and named Replay executor | Bounded Replay-only execution; propagate cancellation and scope to every async child. Refuse capacity rather than use Production capacity. | Appropriate `RUN_*` event with reason/resource counts |
| R156-03 | New evidence qualifier/certificate | Verify as-of evidence, canonical prices, coverage, fingerprints, availability time, fixture hashes and classification before publication. | `RUN_QUALIFIED` or `RUN_REFUSED`, `PRICE_SPAN`, `POLICY_FINGERPRINT` |
| R156-04 | Read-only extraction plus Replay fixture readers | Outside Replay runtime, prepare immutable hashed (a) entry/full-lifecycle timeline; (b) dated Replay policy profile with BUY values and D14/D24 toggle/percentages; (c) dated D28 exchange-constraint/minimum-order fixture. Replay reads only these immutable inputs. | `FIXTURE_LOADED`, `POLICY_FINGERPRINT` |
| R156-05 | `ExecutionReplayScope`, Replay-owned data access, async boundaries | All Replay database access uses Replay-owned allow-listed access. The only transitional shared-table exception is the exact `fix122_evaluation` policy in Section 4.2a: run-owned diagnostics plus a read-only Production-row display query, neither of which is account/wallet authority or decision input. Add a static transitive reachability/architecture test that fails on a repository, JDBC template, table name, or HTTP client outside the approved list, plus a table allow-list test. Direct and async attempts to reach account/HTTP dependencies reject before publication. Use a Replay-owned stable signal key and consumption state; null signal IDs must not silently disable that behaviour. No separate datasource/user or shared SQL interceptor exists. | `FORBIDDEN_DEPENDENCY` |
| R156-06 | Price-event repository projection and Replay DTO | Retain accepted status, source, ID and ordering slot. Query accepted prices in `(observed_at, id)` order. | `PRICE_SPAN`, `DECISION` |
| R156-07 | `ShadowProductionReplayService` and Replay-owned state/output | Replace wallet/cash/budget/portfolio simulation with fixed-notional Replay state. Stop baseline writes and dependencies on wallet-shaped `*_test` artifacts; preserve existing rows/tables and read-only historical viewers. | `DECISION`, `FIRST_DIVERGENCE` |
| R156-08 | Replay timeline | Use E07-derived **signal availability time**, never simply `generated_at`. Apply the frozen same-boundary rule with canonical prices, drain the qualified price tail through `executionEnd`, ignore after-end events, and refuse incomplete tail coverage. Never manufacture a synthetic exit. When both paths remain open, record `OPEN_AT_REPLAY_END` with the final qualified mark price plus its event ID/time; when Production closed, publish the first divergence instead. | `PRICE_SPAN`, `DECISION`, `FIRST_DIVERGENCE` |
| R156-09 | Replay-local policy mirrors | Implement version-matched mirrors for D01-D46 and D47-D56. Keep the Production signal-path and price-path orders separate: stop-loss/profit-lock precedence differs. Mirror BUY only from the policy profile; no shared wallet/account/HTTP service. The Replay-local BUY mirror must match recorded Production BUY allow/reject decisions or publish a mismatch. Near-TP uses the dated D28 fixture, never HTTP. | `DECISION`, `POLICY_FINGERPRINT`, `FIRST_DIVERGENCE` |
| R156-10 | Replay observers and legacy modes | Exclude FIX-11K, FIX-11Q, pre-FIX-122 and `EXPERIMENTAL` logic from baseline execution; preserve display/history only. | `DECISION` with exclusion reason |
| R156-11 | Comparator replacing the unused 13-field validator | Compare versioned decision/lifecycle snapshots, evidence/fingerprint and exclusions. Report the first divergence, including all critical-safety classes. | `DECISION`, `FIRST_DIVERGENCE` |
| R156-12 | `/runs/**` output and all-trades chart | Use Replay-owned output and optional immutable fixture overlay only; remove direct `wallet_trade` access. | `RUN_PROMOTED`, `FORBIDDEN_DEPENDENCY` |
| R156-13 | Replay schema/config/verifier | Add only Replay-owned additive storage and namespaced Replay-only configuration after freeze. New Replay settings are disabled by default and define numeric limits for workers, queue, fixture size, price span, duration, artifact size and retention. Every new table records owner, retention, indexes and rollback note. Verify schema before a run. | `RUN_REFUSED` or `RUN_FAILED` with schema/resource reason |
| R156-14 | Unit/integration/fixture/architecture tests | Implement the numbered test set in Section 7; no silently skipped mandatory evidence. | Test evidence uses the registry events where runtime applies |
| R156-15 | Documentation/registry | Reconcile approved registry to actual implementation, then update required architecture/changelog/rules/investigation/validation documents. | Implementation report, not an application log |

### 4.2a `fix122_evaluation` transitional shared-table exception and delete policy

`fix122_evaluation` is a shared diagnostics/evidence table which Production
also writes. The approved D48 boundary exception is deliberately narrow: it
supports run-owned Replay diagnostic evidence plus one read-only Production
diagnostic display. It cannot participate in a Replay decision, parity
classification, qualification, entry, exit, sizing, or lifecycle transition.

The permitted run-owned operations are:

```sql
DELETE FROM fix122_evaluation WHERE test_run_id IS NOT NULL
```

For the eventual baseline, this is an explicit table allow-list exception, not
an untracked boundary violation. The allow-list and static reachability tests
must permit only the following exact operations:

- read, insert, and query rows whose `test_run_id` identifies the active
  Replay run;
- the scoped purge above, never an unqualified delete and never a delete of a
  row where `test_run_id IS NULL`;
- the existing read-only display query `SELECT * FROM fix122_evaluation WHERE
  test_run_id IS NULL AND symbol=? AND evaluated_at>=? AND evaluated_at<=?
  ORDER BY id`. It may populate diagnostics display only and must not feed a
  decision, parity result, fixture, or any mutable state;
- no other select, insert, update, delete, repository, JDBC template, or
  indirect access to this table.

The direct and background-task tests must fail the build for any operation
outside this exact policy. Any other shared-table access remains forbidden.
D48 remains open until the implemented policy and its direct/background-task
tests prove these limits. RPR-0.9.12 adopts this owner decision. It remains a
draft and is not an implementation authorization.

## 5. Retirements from the baseline only

| ID | Stop in baseline runs | Preserve / replacement |
| --- | --- | --- |
| RET-01 | Wallet settings, virtual cash, daily budget, reserve and portfolio input. | Fixed-notional Replay profile; wallet-driven decisions are non-comparable. |
| RET-02 | Wallet-shaped `*_test` data as a baseline dependency or output. | Preserve historic data/read-only viewers; write Replay-owned output only. |
| RET-03 | Direct `wallet_trade` read in the Replay chart. | Replay output plus immutable fixture overlay. |
| RET-04 | Zero-price-event signal-price fallback labelled exact. | Refuse missing price coverage. |
| RET-05 | FIX-11K, FIX-11Q, pre-FIX-122 and `EXPERIMENTAL` baseline invocation. | Display-only legacy history; separate future experiment entry point if authorised. |
| RET-06 | 13-field `ReplayParityValidator` as a certificate. | Versioned comparator for D01-D46, D47-D56 and S01-S14. |
| RET-07 | Unclassified lifecycle beyond `executionEnd`. | Never create a synthetic exit. Refuse incomplete final-tail coverage. `OPEN_AT_REPLAY_END` stores the final qualified mark price and its event ID/time and is censored only when Production is also open. Replay-open/Production-closed is a published mismatch. |
| RET-08 | Shared BUY validation and live Binance minimum-order call. | Replay-local mirror plus frozen policy/exchange-constraint fixtures. |

## 6. Mandatory logging registry

There is one registry only. Every Replay log starts
`[FIX-156][REPLAY]`, includes `runId`, `profileHash`, `baselineHash`,
`symbol`, and when present `fixtureId`, `signalId`, and `priceEventId`.
Every time is displayed as stored UTC **and** KSA (`+03:00`). No credential,
token, account number, raw SQL parameter, or unbounded payload is logged.

The only event IDs are:

- `RUN_REQUESTED`, `RUN_QUALIFIED`, `RUN_REFUSED`, `RUN_FAILED`, `RUN_PROMOTED`;
- `FIXTURE_LOADED`;
- `PRICE_SPAN`;
- `DECISION`;
- `FIRST_DIVERGENCE`;
- `FORBIDDEN_DEPENDENCY`; and
- `POLICY_FINGERPRINT`.

Event payloads include the RPR-0.9.10 required fields: reason/evidence counts,
fixture hash/count, price-span boundaries/gap/refusal, decision path and
Section-3.3.1 step, input event IDs, expected/actual first divergence, attempted
class/method/table/URL, and policy source/configuration/profile identity.
Volume is bounded by E05. Tests assert one correctly correlated event per
occurrence.

A critical-safety mismatch is `ERROR` and **parity-not-earned**. Its mismatch
certificate and first divergence are published. It never becomes
`FAILED_REPLAY` merely because the result disagrees. `FAILED_REPLAY` means
technical error/cancellation/failed staging and discards staged artifacts.

## 7. Numbered validation matrix

| Test ID | Required assertion |
| --- | --- |
| T156-01 | 1m/5m/1h SELL and active-profit-lock SELL suppression match Production. |
| T156-02a | Signal-path TP, profit-lock, stop-loss and hard-floor precedence matches Section 3.3.1. |
| T156-02b | Price-path TP, stop-loss, dynamic lock, rebase floor, lock breach, normal exit and near-TP precedence matches Section 3.3.1. |
| T156-03 | Equal-time prices retain `(observed_at,id)`; E07 availability time governs same-boundary signal order. |
| T156-04 | Qualified price tail drains through `executionEnd`; incomplete tail refuses; after-end events are ignored. |
| T156-05 | Entry fixture execution time controls gating; no Replay request queries account tables. |
| T156-05a | Missing/malformed/wrong-date/fingerprint-mismatched policy profile is non-comparable/refused; no fallback to current settings. |
| T156-05b | Full timeline fixture covers add, stop/TP change, extension, harvest and profit-lock transition; first divergence ends that lifecycle metric. |
| T156-05c | Replay-local BUY mirror matches each recorded Production BUY allow/reject decision in the development fixture set, or publishes a mismatch without calling shared validation. |
| T156-06 | Lock-active positions do not evaluate/add when Production skips it. |
| T156-07a | Dynamic profit lock toggle on and off, values, extension and rebase match the profile fixtures. |
| T156-07b | Near-TP consumes dated exchange constraints and makes no HTTP/Binance call. |
| T156-08 | Missing historical context/data-quality/configuration evidence produces the right refusal/exclusion/bucket. |
| T156-08a | Missing or non-as-of sentiment provider configuration/health or prior indicator snapshot refuses analysis parity; no current value is substituted. |
| T156-09 | Direct and async account/HTTP/fallback access emits `FORBIDDEN_DEPENDENCY` and publishes nothing. |
| T156-09a | Static reachability/architecture test rejects any Replay-reachable repository, JDBC template, table-name access or HTTP client outside the approved allow-list. |
| T156-09b | Table allow-list test rejects every read/write/delete outside approved market-evidence and Replay-owned tables, except the exact run-owned and display-only `fix122_evaluation` operations in Section 4.2a. |
| T156-09c | Replay output/diagnostics expose only Replay fixture or position keys, never `paper_position_id` or another account identifier. |
| T156-09d | Entry consumption handles both cases: source-row signals use their saved IDs; `analyzeForRegression` signals use a stable Replay-owned key. A repeated entry is consumed without account evidence reads/writes. |
| T156-10 | Legacy/experimental observers cannot change a baseline result. |
| T156-11 | Charts/output query only Replay-owned data plus immutable overlay. |
| T156-12 | Ordinary and critical mismatches publish the correct first divergence; critical mismatches prevent the affected parity claim without becoming technical failure. |
| T156-13 | Replay-open/Production-open is censored as `OPEN_AT_REPLAY_END` with the final qualified mark price, price-event ID and time; Replay-open/Production-closed is a mismatch. No synthetic exit is created. |
| T156-14 | Staging/cancellation/resource refusal has correct terminal result and artifact handling. |
| T156-15 | Logging emits only the Section 6 event IDs, once per occurrence, with required UTC+KSA correlation fields. |
| T156-16 | Disposable-MySQL migration/schema validation is mandatory in the release stage and cannot silently skip. |

## 8. Completion criteria

Before any parity claim, actual changes must reconcile to the approved registry;
all required tests and E01-E10 evidence must exist; the qualified/excluded/
refused/non-comparable/censored buckets must be reported by cause and exit
class; and the frozen coverage/count/lower-bound requirements must pass
separately for both dimensions.

There must be **zero unexplained critical-safety mismatches** in stop-loss,
profit-lock, take-profit, near-TP ownership and duplicate-exit classes. A
qualified cohort may earn its applicable `ANALYSIS_PARITY` and/or
`LIFECYCLE_PARITY` dimension only. It never earns account or exact-fill parity.

## 9. Evidence status: E01 and E02 draft results

E01 and E02 are now consolidated into this preparation document. They are
read-only source evidence drafts, not approved evidence and not implementation
authority. Their register changes are incorporated in RPR-0.9.12-DRAFT and
remain subject to formal review.

### E01 - dependency audit: confirmed current violations

The current Replay path is not inside the approved boundary yet:

- `wallet_settings` is Replay-reachable through `ShadowProductionReplayService`,
  `OneCandleContinuationGraceReplayObserver`, and shared BUY validation.
- Near-TP reaches live Binance `/api/v3/exchangeInfo` through
  `BinanceMinimumExecutionPolicy`.
- `wallet_trade` is read by the all-trades chart and proven-trade enrichment.
- If Replay scope propagation is absent, `EntryConsumptionPolicy` and
  `StopLossEvidenceStore` can fall through to Production-account reads.
- Replay currently reads, writes, and performs the scoped run-owned purge on
  shared `fix122_evaluation`; Section 4.2a defines the only allowed
  transitional exception and its delete policy. Replay can also write
  `shared_candle_input_audit` in shared-market LIVE mode.

The required future response is Replay-local policy/data adapters, immutable
policy and exchange-constraint fixtures, the allow-listed Replay data-access
layer, static reachability checks, and direct/async rejection tests. The E01
audit must also decide whether every shared-table write is replaced by a
Replay-owned record or made unreachable in the baseline.

RPR-0.9.12 records these formal boundary rows. They are not merely audit notes
because each one must be closed, tested and reconciled:

| Proposed row | E01 boundary violation | Required disposition |
| --- | --- | --- |
| D47 | `OneCandleContinuationGraceReplayObserver` reads `wallet_settings`. | Remove from baseline invocation or feed only the frozen Replay policy profile. |
| D48 | Replay reads, writes, and deletes from shared `fix122_evaluation`; the current delete is scoped to `test_run_id IS NOT NULL`, while Production also writes table rows. `StopLossEvidenceStore.productionDetails` separately reads `test_run_id IS NULL` rows for display. | Enforce Section 4.2a's exact run-owned and display-only allow-list/delete policy. The scoped purge and display query must not be reported as boundary violations; every other shared-table access remains forbidden. |
| D49 | Replay can write shared `shared_candle_input_audit` when shared-market mode is LIVE. | Make the path unreachable in baseline Replay; prove OFF/LIVE mode cannot bypass the boundary. |
| D50 | Missing Replay scope can fall through through `EntryConsumptionPolicy` to account evidence. | Fail closed through scope propagation and the Replay access allow-list. |
| D51 | Missing Replay scope can fall through through `StopLossEvidenceStore.latest` to `wallet_trade`. | Fail closed through scope propagation and the Replay access allow-list. |
| D52 | Missing scope can fall through to shared `execution_opportunity` reads/writes. | Route only to Replay-owned state or reject; never use shared mutable opportunity state. |
| D53 | Replay can read current sentiment-provider weights/health while evaluating historical time. | Require a dated immutable sentiment-provider configuration/health fixture; otherwise refuse analysis parity. |
| D54 | Replay can depend on a Production-saved prior indicator snapshot; a missing snapshot changes its result. | Include exact prior-snapshot identity/availability in the evidence qualifier; refuse when absent or not historically available. |
| D55 | Replay diagnostics display exposes `paper_position_id`, an account-table identifier. | Remove the account identifier from baseline Replay output and replace it with a Replay fixture/position key only. |
| D56 | Signals regenerated in memory by `analyzeForRegression` carry null IDs, making entry consumption inert and concealing the D50 fall-through path. Source-row signals loaded from saved `trade_signal` rows retain their saved IDs. | Give regenerated signals a stable Replay-owned signal key and explicit Replay-local entry-consumption state; preserve saved IDs for source-row signals and test both paths without querying or writing account evidence. |

### E02 - decision-order matrix: required register corrections

The following earlier register assertions are not valid for the source package
audited on 2026-10-14:

| Existing row | E02 result | RPR-0.9.12 action |
| --- | --- | --- |
| D01 | Refuted: normal SELL validation is effectively 1m-only in both Production and Replay. | Retain history; mark refuted, not a Replay change. |
| D08 | Refuted as a decision difference: both wake-up routes are 5m-only. | Retain history; mark refuted, not a Replay change. |
| D22 | Confirmed on re-check: this is not a TP-extension enable flag; it is the profit-lock enabled argument used during TP extension. Production `DynamicProfitLockService.onTakeProfitExtended` passes literal `true`, while Replay `rebaseForExtendedTarget` passes the administration toggle. | Retain history as a real Replay change; fixture-test TP-extension rebase with the administration toggle on and off. |

E02 introduces 17 additional source findings. RPR-0.9.12 registers them
as formal rows `D30` through `D46`, retaining `N01` through `N17` as audit
provenance:

| Proposed row | E02 finding | Required initial disposition |
| --- | --- | --- |
| D30 / N01 | Production signal path closes a profit lock before stop loss, directly; Replay price/fallback handling differs. | Blocking signal-path mirror fixture. |
| D31 / N02 | Production signal-path TP closes directly; Replay fallback can extend. | Retire fallback and fixture-test signal path. |
| D32 / N03 | Near-TP is absent from the Production signal path but appears in fallback. | Retire fallback. |
| D33 / N04 | Fallback normal exit/order is not the Production signal-path order. | Retire fallback. |
| D34 / N05 | Managed-stop and paper-stop sources can differ. | Fixture/evidence check; classify only after data evidence. |
| D35 / N06 | Production entry gates depend on wallet state/cross-symbol limits/balance/daily budget. | `WALLET_DRIVEN_NOT_COMPARABLE`. |
| D36 / N07 | Production and Replay use different clocks for entry fill-price selection. | Blocking as-of/fill-clock mirror fixture. |
| D37 / N08 | Replay assumes price events up to generated time precede signal visibility. | Resolve through E07 availability-time evidence. |
| D38 / N09 | Production can expire stale signal work before business logic; Replay currently processes all signals. | Blocking freshness/refusal behaviour. |
| D39 / N10 | Replay consumes opportunity state on price exits while Production does not. | Blocking lifecycle-state mirror. |
| D40 / N11 | Near-TP freshness uses wall clock in Production and event time in Replay. | As-of clock fixture. |
| D41 / N12 | Production latest-signal lookup/order may differ from Replay timeline ordering. | E07/order evidence and fixture. |
| D42 / N13 | Add clamping appears equivalent in traced cases. | Prove equivalent or mark non-comparable. |
| D43 / N14 | 1m SELL exit reason labels differ. | Evidence/output mapping fixture. |
| D44 / N15 | Production writes stop-evidence audit rows that Replay skips. | Evidence-only / Replay-owned audit decision. |
| D45 / N16 | 1m wake-up handling differs internally but appears decision-equivalent. | Prove equivalent fixture. |
| D46 / N17 | Rebase-floor arithmetic uses different precision policies. | Numeric tolerance fixture. |

### Consequence for approval gates

P02 and P03 remain pending. E01/E02 identify scope and register changes but do
not close E03-E10, do not prove runtime behaviour, and do not authorise a
Replay implementation. RPR-0.9.12-DRAFT must now be reviewed and frozen with
the required source/configuration/migration/worktree evidence before approval.

## 10. What was deliberately not done

- No Java source was modified.
- No SQL/Flyway migration was created or changed.
- No application or Replay configuration was changed.
- No database was queried, written, repaired, or migrated.
- No service was started/stopped and no deployment action occurred.
- No production behaviour was changed.

This preparation map authorises nothing. The canonical FIX-156 specification
remains **NOT APPROVED - IMPLEMENTATION FORBIDDEN**.

## 11. Final pre-implementation static code review

### 11.1 Review identity and method

This section replaces the former standalone
`FIX-156_FINAL_PREIMPLEMENTATION_REVIEW_RPR-0.9.12.md` file. It is part of
this single consolidated document.

Reviewed specification: `C:\Users\jeane\Downloads\FIX-156-RPR-0.9.12-DRAFT.md`.
Its SHA-256 is the value recorded in this document header.

Review method: static source review only. No source, configuration, database,
or production data was changed. No compilation, test suite, database query,
Replay run, or deployment validation was performed.

### 11.2 Final verdict

RPR-0.9.12 is a coherent pre-implementation contract, but the current Replay
code does not yet meet it. The workspace contains legacy Replay behaviour
that FIX-156 is designed to replace. This review therefore cannot finalize or
deliver an implementation.

The permitted outcome is an implementation baseline and a precise blocker
list. Production behaviour remains unchanged. No FIX-156 implementation may
start until the specification-review, evidence, freeze, and authorization
gates in Sections 3, 7, 9, and 11.5 are complete.

### 11.3 Source areas reviewed

The final source check covered the primary Replay path and direct dependencies:

- `ShadowProductionReplayService`
- `RegressionTestService`
- `ExecutionReplayScope`
- `DynamicProfitLockService`
- `StopLossEvidenceStore`
- existing Replay-focused test files

This is not a claim that every repository class was exhaustively validated.
The authoritative full difference register remains RPR-0.9.12 rows D01-D56
and S01-S14.

### 11.4 Confirmed corrections and current non-compliance

The following specification corrections are supported by the source and must
remain in the implementation contract:

| Register item | Source result |
| --- | --- |
| D01 | Refuted as a Production/Replay difference: Production SELL validation also rejects non-1m signals through base validation. Retain as history only. |
| D08 | Refuted as a decision difference: Production wake-up behaviour is also 5m-only. Retain as history only. |
| D22 | Confirmed active difference: `DynamicProfitLockService.onTakeProfitExtended` passes literal `true` to `ProfitLockPolicy.evaluate`; `ShadowProductionReplayService.rebaseForExtendedTarget` passes `WalletSettings.isDynamicProfitLockEnabled()`. |
| D56 | Confirmed limited scope: only in-memory signals regenerated by `analyzeForRegression` can have a null id; source-row signals retain their saved `trade_signal` id. |

The following current-source behaviour is incompatible with the approved
FIX-156 target and remains open:

| Area | Current static evidence | Required final state |
| --- | --- | --- |
| Current wallet settings | `ShadowProductionReplayService` injects `WalletSettingsRepository`; `walletSettings()` loads row `1`. | Replay reads only frozen, extracted fixture settings. |
| Live exchange constraints | Replay calls `BinanceMinimumExecutionPolicy.evaluate` for sell/harvest checks; it reaches Binance exchange-info data. | Fixture-backed historical exchange constraints; no live HTTP in Replay. |
| Shared diagnostics/evidence | `RegressionTestService` reads and executes `DELETE FROM fix122_evaluation WHERE test_run_id IS NOT NULL`; `StopLossEvidenceStore` inserts/reads that table and can read `wallet_trade`. | Enforce the exact run-owned `fix122_evaluation` exception/delete policy in Section 4.2a; all other shared access is forbidden and must be proven by allow-list and reachability tests. |
| Production trade enrichment/charting | `RegressionTestService.allTradesChart` and proven-trade detail paths query/enrich `wallet_trade`. | Historical extraction before a Replay run or a Replay-local replacement. |
| Profit-lock extension | Replay applies the current admin enable flag; Production forces enabled during extension rebase. | Frozen historical profile and exact Production input/precedence. |
| Opportunity lifecycle | Replay invokes `completePositionOpportunity` for price-driven exits as well as signal-driven exits. | Mirror the two Production decision orders and lifecycle transitions. |
| Signal identity/consumption | Scope is thread-local; regenerated signals may have null ids. | Replay-owned stable signal key and consumption state, while preserving saved source ids. |
| Parity measurement | Existing tests are limited and no demonstrated full decision-by-decision RPR-0.9.12 parity validator is invoked. | Registered comparator, divergence report, critical-safety gate, and fixture suite. |

Current discovered Replay-oriented tests include
`ShadowProductionReplayServiceFix120ParityTest`, `ReplayDatasetTest`, and
`ExecutionReplayScopeModeTest`. They do not demonstrate completion of
T156-01 through T156-16. In particular, the existing FIX-120 test mocks both
`WalletSettingsRepository` and `BinanceMinimumExecutionPolicy`, confirming
that those prohibited runtime dependencies are still part of the current
Replay design.

### 11.5 Mandatory gates before implementation

1. Complete P01 with exact source, configuration, migration, and clean-worktree hashes.
2. Review the exact RPR-0.9.12 revision and resolve all material findings.
3. Complete E03-E10 as read-only measured evidence, including historical settings, price-event coverage, signal availability, policy fingerprints, and resource limits.
4. Update the preparation registry only where that completed evidence requires it, while keeping RPR-0.9.12 as the canonical contract.
5. Obtain explicit user authorization for the exact reviewed and frozen revision.

The recorded P01 status reports a large dirty working tree. It cannot serve
as a reproducible baseline until the hashes and a clean baseline are
recorded.

### 11.6 Implementation boundary after authorization

FIX-156 may change Replay-owned code, fixtures, isolated Replay tables,
tests, and documentation only. It must not change Production trading logic,
Production wallet behaviour, Production database records, live Binance
behaviour, or deployment configuration.

The future implementation must include the registered structured logs with
UTC and KSA values, distinguish `ANALYSIS_PARITY` from `LIFECYCLE_PARITY`,
treat a business mismatch as parity-not-earned rather than `FAILED_REPLAY`,
and reject a parity claim for a critical-safety mismatch.

### 11.7 Consolidated conclusion

This document is now the single consolidated FIX-156 preparation and final
pre-implementation review artifact. It does not approve implementation.
Current code materially differs from the FIX-156 target by design. The next
safe action is completion of the evidence-and-freeze gates, followed by an
explicit implementation authorization for this exact RPR-0.9.12 revision or a
newer fully reviewed revision.

## 12. Implementation-authorisation evidence review — 2026-10-15

The owner supplied an implementation-authorisation note and a P01 freeze
record. They establish the intended Replay-only scope, but they do not yet
activate implementation under the repository's mandatory implementation
contract.

| Evidence | Verified identity | Result |
| --- | --- | --- |
| RPR-0.9.12 draft | `FIX-156-RPR-0.9.12-DRAFT (1).md`, SHA-256 `ddfd5f64a95f6b55ed39e8bffa7b4c93bd65f4e6dd3b23d8bf821500de722b24` | The document itself says `NOT APPROVED — IMPLEMENTATION FORBIDDEN`. |
| P01 freeze record | `FIX-156-P01-FREEZE-RECORD.md`, SHA-256 `b3ed8eaf2297143bdfdee3d23e38675275cc552dc1d80fdbfebb540b20cff5cc` | It says `NOT APPROVED FOR IMPLEMENTATION`; configuration identity, migration identity, clean worktree, and canonical-spec reconciliation remain open. |
| Owner authorisation | `FIX-156-IMPLEMENTATION-AUTHORISATION.md`, SHA-256 `2e22bd69976466e53b50705cb55e2d98764b3efff7cf75b245c6f884d90198af` | Scope authority is recorded, but it cannot override the P01 and frozen-specification gates. |
| Workspace check | Git HEAD is `0cb6a9a6cc64ba8ac9d33797bcc30af534fd46e3`; the worktree has many modified/deleted/untracked paths. | This is not the declared clean `930ab3d9` implementation baseline. |
| Canonical path | `agent-docs/FINAL_CHANGE_SPECIFICATION.md` | Still RPR-0.4-DRAFT, not the exact RPR-0.9.12 specification. |

Therefore no FIX-156 code, migration, fixture, configuration, or test change
has started. Before implementation, the exact RPR-0.9.12 content must be
installed and frozen at the canonical path, the clean `930ab3d9` baseline and
P01 identities must be verifiably present in the actual workspace, and the
required independent approvals must be recorded for that exact frozen
revision. The owner authorisation then becomes actionable without changing the
approved Replay-only boundary.


Two rounds. Replay only. Ignore wallet_settings. Do not edit the preparation file. Do not implement.
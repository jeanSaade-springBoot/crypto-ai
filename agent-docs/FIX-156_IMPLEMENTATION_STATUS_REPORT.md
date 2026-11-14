# FIX-156 — Replay / Production Parity Implementation Status Report

**Date:** 2026-10-16 (KSA)  
**Status:** IN PROGRESS — NOT READY FOR REVIEW ACCEPTANCE OR DEPLOYMENT  
**Authoritative design:** `FIX-156-RPR-0.9.12-DRAFT.md`  
**Design SHA-256:** `ddfd5f64a95f6b55ed39e8bffa7b4c93bd65f4e6dd3b23d8bf821500de722b24`  
**Jean review:** ACTIVE; `USR-REV-001` and `USR-REV-002` are OPEN.  
**Production impact:** NONE authorized. Replay-only work may not alter Production
source, wallet/account records, Binance runtime behavior, schedulers, or
deployment configuration.

## 2026-11-14 technical handoff for team review

### Implemented locally

- `ShadowProductionReplayService` no longer reaches the named wallet-settings,
  Binance-minimum, shared BUY authority, execution-price, execution-scope, or
  opportunity services. Static reachability coverage rejects those dependencies.
- Frozen `ReplayPolicyProfile` supplies Replay BUY/lifecycle policy inputs;
  `ReplayBuyValidationPolicy`, `ReplaySellValidationPolicy`, sizing, and
  minimum-order checks remain Replay-local.
- Replay execution lifecycle now writes `replay_position`, `replay_execution`,
  and `replay_position_management`; active run/trade/chart/management reads
  use those same Replay-owned tables.
- V112 is forward-only and additive. It creates the three Replay lifecycle
  tables from the legacy Replay test-table structure and adds run/time indexes.
  **V112 was not applied in this session.**
- Replay writes a `NOT_PARITY_CLAIMED` execution summary, `NOT_MEASURED` run
  certificate, per-BUY `NOT_MEASURED` decision trace, and qualified/non-qualified
  price-coverage record. Price coverage requires both execution-window boundaries
  and no gap greater than two minutes.
- `ReplayAccessPolicy` guards Replay-owned lifecycle stores and active Replay
  display reads; active market-candle reads are declared read-only evidence.

### Local validation evidence

- Java 21 targeted Replay suite passed on 2026-11-14, covering access policy,
  boundary reachability, policy profile, local BUY/SELL policies, output,
  certificate, decision trace, price coverage, comparator, FIX-122 operation
  policy, and Shadow Replay geometry parity.
- Java 21 `test-compile` passed after adding V112 assertions to the guarded
  disposable-MySQL Flyway test.

### Remaining review/validation items

- No qualified frozen Production decision/lifecycle fixture exists in the
  workspace for the same run/window. Therefore no Production-vs-Replay
  comparison, first-divergence record, or `ANALYSIS_PARITY`/
  `LIFECYCLE_PARITY` pass result has been produced.
- Disposable-MySQL execution was not run: this session has no dedicated
  `FIX153_TEST_MYSQL_URL`, disposable marker, or verified server UUID. The
  harness rejects non-disposable targets, and no trader database was contacted.
- Historical archive/proven-trade administration remains outside the active
  parity execution path and should be reviewed separately if it is to be
  brought inside the FIX-156 boundary.

### Accurate status for reviewers

**IMPLEMENTED UNDER JEAN OVERRIDE — local Replay isolation foundation and
evidence plumbing are implemented; end-to-end Production parity is NOT
MEASURED.**

**2026-11-08 progress under Jean override:** Replay now publishes an isolated
`NOT_PARITY_CLAIMED` execution summary to `replay_run_output`, and the shadow
orchestrator has no reachability to the named shared BUY, wallet-settings,
Binance-minimum, execution-price, execution-scope, or opportunity services.
Each completed Replay run also receives a fail-closed `NOT_MEASURED` analysis
and lifecycle certificate until qualified evidence/comparison is implemented.
Legacy test-table lifecycle persistence and end-to-end parity certification are
still outstanding, so this status is not completion or acceptance.

**2026-11-14 lifecycle progress:** the active Shadow Replay writer and active
run count/trade/chart/management readers use V112 Replay-owned lifecycle
tables. The static boundary test now rejects legacy lifecycle table names in
the Shadow Replay service. Outstanding work is SQL-boundary coverage,
qualified comparison/price-coverage evidence, and migration validation.

**2026-11-14 trace progress:** each Replay-local BUY-policy decision is now
persisted to the Replay-owned decision-trace table as `NOT_MEASURED` evidence.
It records no asserted Production equivalence; qualified expected evidence and
first-divergence comparison remain outstanding.

**2026-11-14 access-policy progress:** Replay lifecycle table access is now
registered and checked through `ReplayAccessPolicy`; legacy wallet test tables
remain rejected. The remaining SQL-boundary work is statement-level coverage
outside the Shadow lifecycle flow, plus qualified parity evidence.

## 1. Executive result

FIX-156 has a small Replay-only foundation, but it is not functional parity
work yet. The foundation creates isolated data structures and basic validation
types; it is not wired into the existing Replay request, worker, or Shadow
Production Replay path. The existing path continues to read wallet settings,
call the Binance minimum-execution policy, read `wallet_trade`, and use shared
validation/services.

Therefore this implementation cannot claim `ANALYSIS_PARITY`,
`LIFECYCLE_PARITY`, or an equivalent result. Until the evidence gates are
closed, any completed Replay result must remain **not measured**.

**2026-10-16 corrective progress:** the Replay foundation now has operation
categories for the O-8 diagnostic-table exception, a local SELL validation
mirror, pure local sizing/minimum-order primitives, and a complete profile
contract. These are not yet wired through the complete Replay lifecycle, so
the conclusion above is unchanged.

**2026-10-17 corrective progress:** the active baseline no longer reads the
legacy shared `fix122_replay_revision` switch, and `allTradesChart` no longer
queries `wallet_trade`. The broader Replay-local BUY/lifecycle replacement,
complete SQL-boundary enforcement, and required parity evidence remain open.

## 2. Implemented foundation

The following additions exist. No existing application Java file has been
modified by this initial foundation slice.

| Artifact | Current purpose | Current limitation |
| --- | --- | --- |
| `com.crypto.regression.replay.ReplayPolicyProfile` | Immutable Replay profile with run hash/time/fingerprint, execution/BUY settings, profit-lock configuration, and fixed notional. | Not called by the Replay run lifecycle yet. |
| `ReplayPolicyProfileStore` | Replay-local profile persistence helper. | Not called by the Replay run lifecycle. |
| `ReplayAccessPolicy` | Operation-aware allow-list, including the four exact O-8 categories. | Not used by every existing Replay SQL operation yet. |
| `ReplayForbiddenDependencyException` | Fail-closed exception for the initial access guard. | Not yet connected to all direct/async Replay paths. |
| `V110__fix_156_replay_owned_tables.sql` | Original four Replay-owned tables. | Confirmed already applied; it must remain byte-for-byte unchanged. |
| `V111__fix_156_replay_profile_evidence.sql` | Forward-only Replay profile detail, run certificate, and price-coverage tables. | Applied locally by the user-started application on 2026-10-17; still requires disposable-MySQL migration validation. |
| `ReplayAccessPolicyTest`, `ReplayPolicyProfileTest` | Basic unit tests for the initial guard/profile. | They do not implement T156-09, T156-09a, or T156-09b. |

## 3. Confirmed current Replay boundary violations

The following calls still exist in the active Replay area and are prohibited by
the approved FIX-156 direction:

| Current class | Confirmed dependency/behavior | Required replacement |
| --- | --- | --- |
| `ShadowProductionReplayService` | **Corrected 2026-10-16:** wallet settings are no longer injected/read. | Frozen `ReplayPolicyProfile` now supplies sizing and profit-lock values; a missing profile refuses. |
| `ShadowProductionReplayService` | **Corrected 2026-10-16:** no `BinanceMinimumExecutionPolicy` call remains. | Replay-local minimum-order mirror consumes frozen profile/fixture evidence; no HTTP. |
| `ShadowProductionReplayService` | **Partly corrected:** direct shared `TradeExecutionValidationService` SELL calls are removed. Shared BUY/execution services still remain. | Complete Replay-local BUY mirror, decision trace, and output lifecycle remain required. |
| `RegressionTestService` | `wallet_trade` is used by the all-trades chart/proven-trade enrichment. | Replay-owned output plus immutable fixture overlay only. |
| `RegressionTestService` / `StopLossEvidenceStore` | Reads/writes/purges `fix122_evaluation`. | Exact O-8 operation policy only; no decision input, parity classification, fixture, or mutable Replay state. |
| `OneCandleContinuationGraceReplayObserver` | Reads `WalletSettingsRepository`. | Exclude the legacy observer from baseline or replace its settings dependency with frozen Replay evidence only if separately authorized. |

## 4. Jean findings that block wiring and acceptance

### USR-REV-001 — OPEN

1. Make data access operation-aware. A table-name-only allow-list cannot
   distinguish the permitted O-8 `fix122_evaluation` statements from a
   forbidden one.
2. Define the complete approved Replay evidence/source table set and route
   every Replay SQL operation through the enforcement layer.
3. Complete the frozen profile fields and rejection checks.
4. Record V110 table owner, retention, indexes, rollback notes, and how run
   certificates/price coverage are stored.
5. Update the stale FIX-156 row in `src/main/resources/static/js/fix-registry.js`.

### USR-REV-002 — OPEN

1. Diff the eventual package against baseline `930ab3d9`; Production files
   must remain unchanged. The allowed documentation exception is the FIX-156
   registry entry.
2. Test every allowed and forbidden `fix122_evaluation` operation.
3. Require a complete profile including BUY-mirror fields and policy
   fingerprint, with no wallet fallback.
4. Remove the stated wallet/Binance/shared-validation/chart dependencies from
   `ShadowProductionReplayService` and surrounding Replay paths.
5. Provide Java 21 evidence for **T156-09**, **T156-09a** (static
   reachability), and **T156-09b** (operation/table policy).

Only Jean may close these findings.

## 5. Required implementation sequence

1. **Correct the boundary design before wiring.** Replace the current
   name-only `ReplayAccessPolicy` with an operation-level policy. It must allow
   only the RPR-0.9.12 Section 2.1 operations for `fix122_evaluation`:
   active-run reads/inserts, the exact scoped purge, and display-only
   Production-row query. All other statements must reject.
2. **Define the Replay data layer.** Add approved, read-only market-evidence
   access and Replay-owned output access. Each Replay SQL call must be inside
   this layer; raw repository/JDBC/HTTP reachability must fail static and
   async tests.
3. **Finish the frozen policy contract.** Require profile hash, as-of time,
   fingerprint, `requireNewBuyTransition`, execution profile, complete
   profit-lock toggles/percentages, BUY settings, fixed notional, and dated
   Binance minimum-order constraints. Refuse missing/invalid values.
4. **Build Replay-local mirrors.** Replace wallet-dependent BUY and
   minimum-order logic with pure Replay-local mirrors. Do not alter the
   Production services or make a shared class branch on `replay=true`.
5. **Rewire the Replay lifecycle.** `ShadowProductionReplayService` must take
   a qualified fixture/profile and use only Replay-local state/output. It must
   not call wallet settings, Binance, shared BUY validation, or wallet-trade
   charts.
6. **Implement trace/comparator/output.** Persist a decision trace and first
   divergence using Replay-owned tables. Before E03-E10 and threshold freeze,
   publish `not measured`, never parity earned.
7. **Retire baseline-only legacy paths.** Ensure FIX-11K, FIX-11Q,
   pre-FIX-122, and `EXPERIMENTAL` behavior cannot affect baseline output,
   while keeping historical display/archive data read-only.
8. **Complete verification and evidence.** Implement T156-01 through
   T156-16 as applicable; execute the mandatory Java 21 guard tests and the
   release-stage disposable-MySQL migration check; attach outputs to Jean’s
   review package.

## 6. Required data model/documentation completion

V110 needs explicit implementation documentation for every table:

| Table | Owner | Intended retention / rollback | Missing decision |
| --- | --- | --- | --- |
| `replay_policy_profile` | Replay only | Preserve immutable profile evidence per run; rollback is feature disable, not data mutation. | Final retention period and profile-version relationship. |
| `replay_fixture` | Replay only | Preserve fixture hashes/payload evidence; archive before expiration. | Fixture size limit and final retention. |
| `replay_run_output` | Replay only | Preserve run result/refusal/output evidence. | Whether it is the run certificate record or an additional table is required. |
| `replay_decision_trace` | Replay only | Preserve first divergence and qualified decision trace. | Price-coverage linkage and final retention/index plan. |

`fix-registry.js` also requires a documentation-only correction. Its current
FIX-156 entry still refers to RPR-0.6, an “isolated virtual account,” and old
outcome wording. It must identify RPR-0.9.12, frozen Replay inputs, no wallet
interaction, and the current not-implemented/in-progress status without
overstating completion.

## 7. Required validation evidence

| Gate | Current state |
| --- | --- |
| Java 21 initial foundation unit tests | Previously passed for the two small foundation tests only. This is not sufficient for FIX-156. |
| T156-09 direct forbidden dependency | Missing. |
| T156-09a static reachability | Missing. |
| T156-09b operation/table allow-list | Missing. |
| T156-01 to T156-16 parity/lifecycle/fixture coverage | Missing. |
| E03-E10 historical evidence/coverage/fingerprint/resource data | Pending; no parity metric can be earned. |
| Disposable-MySQL V110 validation | Missing. |
| Jean review closure | Pending; USR-REV-001 and USR-REV-002 remain OPEN. |

## 8. Explicit non-goals and protections

- No Production Java, runtime behavior, wallet/account row, scheduler,
  Binance runtime call, deployment configuration, or applied migration may be
  changed for FIX-156.
- The sole shared-table exception is `fix122_evaluation` under the exact O-8
  operation policy; it cannot influence Replay decisions or parity results.
- Replay must use only historically available, frozen inputs. Missing evidence
  causes refusal/exclusion/non-comparability, not a live lookup or a synthetic
  substitute.
- The current dirty workspace contains unrelated work. Do not use a broad
  cleanup, reset, or revert. Review only exact FIX-156 paths against the
  frozen baseline before a package is submitted.

## 9. Completion definition

FIX-156 is complete only when every registered RPR-0.9.12 change is accounted
for, both Jean findings are accepted by Jean, all required tests pass on Java
21, V110 is validated in disposable MySQL, E03-E10 are complete, no Production
file changed, documentation is reconciled, and a complete implementation
review package lists every changed file and its test evidence.

**2026-11-14 price-coverage progress:** Replay persists qualified/non-qualified
historical price coverage per run/symbol. Qualification requires both requested
window boundaries and no event gap greater than two minutes. A qualified record
still cannot earn parity without the Production-vs-Replay comparison.

**2026-11-14 active-read access progress:** active Replay run/display reads now
declare Replay-owned or market-evidence access through `ReplayAccessPolicy`.
Historical archive and regression-administration operations are excluded from
the parity execution path; qualified comparison and migration validation remain
outstanding.

**2026-11-14 MySQL validation preparation:** the disposable-MySQL Flyway test
now asserts V112 success plus `replay_position`, `replay_execution`, and
`replay_position_management`. Java 21 test compilation passed. Execution is
not possible in this session because the required dedicated disposable loopback
MySQL URL/marker/server UUID are absent; no trader database was contacted.

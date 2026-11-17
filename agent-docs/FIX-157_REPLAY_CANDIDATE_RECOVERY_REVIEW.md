# FIX-157 — Replay-Only Late-Entry Candidate Recovery Ledger

## Review status

- Status: **IMPLEMENTED UNDER JEAN OVERRIDE** (`JEAN-OVERRIDE-20261117-001`).
- Purpose: review the exact current implementation before deciding whether any
  late-entry rule should ever be designed for Production.
- Scope: completed Replay runs only.
- Production trading impact: **none**.
- Production database writes: **none**.
- Paper-wallet/paper-position writes: **none**.
- Parity claim: **none**. This is an experiment using Replay signal snapshots
  and candle OHLC evidence; it is not proof of executable historical fills.

## 1. Business objective

Several manually reviewed suspicious blocked BUYs were later followed by a
same-symbol entry at a higher price. The business question is not “should we
remove blockers?” It is:

> Can a narrow, costed, timestamp-safe recovery condition identify a later
> fresh BUY that is still near the blocked candidate, without chasing a move?

FIX-157 gives the team a repeatable way to measure that question inside one
selected Replay interval `[t1,t2]`. It deliberately does **not** change the
existing BUY admission logic, blocker behavior, execution flow, or protection
logic.

## 2. What changed

### Package-level owner-approved exceptions outside this Replay ledger

| Exception | Production effect | Owner approval |
|---|---|---|
| Trade Inspector `blocked_buy_review` and suspicious-mark POST | Writes review metadata only; does not alter admission/execution. | `JEAN-OVERRIDE-20261116-003` |
| Trade Inspector blocked-BUY evidence display and its JS test | Read-only UI/chart evidence and test coverage. | `JEAN-OVERRIDE-20261116-002` |
| `ExecutionIntelligenceServiceTest` Mockito repair | Test-only signature repair. | `JEAN-OVERRIDE-20261115-001` |

“Production unchanged” here means unchanged by FIX-157, not unchanged across
every file in the package.

| Area | Change | Why |
|---|---|---|
| Database | Additive Flyway migration `V114__replay_candidate_recovery_ledger.sql` | Persists a scenario audit trail separately from baseline Replay lifecycle data. |
| Policy | `ReplayCandidateRecoveryPolicy` | Holds deterministic candidate thresholds, anti-chase calculation, virtual notional, fee and slippage assumptions. |
| Service | `ReplayCandidateRecoveryLedgerService` | Reads completed Replay evidence and writes scenario events only to the new Replay ledger. |
| Boundary | `ReplayAccessPolicy` | Registers the new table as Replay-owned; the service has no wallet/paper/order dependency. |
| API | Two run-scoped endpoints | Load a prior ledger or rebuild one for a completed Replay run. |
| Existing chart | `all-trades-chart` response adds scenario event points | Shows the experiment on the same chart as baseline Replay trades. |
| UI | Proven Analysis adds **Build recovery ledger** table/action | Lets reviewers inspect the chronology and P&L without leaving existing Replay UI. |
| Tests | `ReplayCandidateRecoveryPolicyTest` and access-policy coverage | Verifies eligibility, virtual cost treatment, and allow-list registration. |

## 3. Exact business flow

```text
Completed Replay run [t1,t2]
        |
        v
Replay-generated 1m signal snapshots (trade_signal_test)
        |
        +-- blocked BUY candidate? -------------------------- no --> ignore
        |       |
        |       +-- score >= 75
        |       +-- raw confidence >= 65
        |       +-- blocker is RANGE_ENTRY_LOCATION or ATR_RISK
        |       +-- candidate price/stop/target retained in memory
        |
        v
Later fresh, final-entry-allowed 1m BUY inside 60 minutes
        |
        +-- price above anti-chase ceiling --> BUY_REJECTED, candidate remains open
        |
        +-- price at/below ceiling --> BUY_ALLOWED virtual entry
                                           |
                                           +-- candle reaches stop --> SELL_ALLOWED (virtual stop)
                                           +-- candle reaches target -> SELL_ALLOWED (virtual target)
                                           +-- both in same candle -> conservative virtual stop
                                           +-- no exit by t2 -> OPEN_AT_T2 (unrealized P&L)
        |
        +-- no eligible release in one hour --> EXPIRED
        |
        v
replay_candidate_recovery_ledger + chart markers + baseline comparison
```

## 4. Candidate and release rules implemented now

| Rule | Current implementation | Review intention |
|---|---|---|
| Candidate decision | `original_decision` is `BUY` or `STRONG_BUY`, but `final_entry_allowed=false` | Only examine a blocked bullish setup. |
| Candidate quality | total score ≥ 75 and raw confidence ≥ 65 | Avoid testing weak setups as recovery candidates. |
| Supported blocker | `RANGE_ENTRY_LOCATION` or `ATR_RISK` only | Keep the first experiment narrow; MTF/BTC/liquidity vetoes are not weakened. |
| Release signal | later 1m `BUY`/`STRONG_BUY` with `final_entry_allowed=true` | Requires a fresh ordinary Replay admission. |
| Candidate lifetime | 60 minutes | Prevent an old candidate from being revived much later. |
| Range anti-chase | candidate price × 1.0075 | Caps a release to 0.75% above its blocked price. |
| ATR anti-chase | persisted ATR retracement price when available | Uses the prior signal’s intended retracement level. |
| Virtual notional | 100 USDT | Gives a consistent, comparable scenario size. |
| Fee | 0.10% at entry and exit | Avoids treating gross price movement as profit. |
| Slippage | 0.05% adverse on each virtual fill | Makes virtual results less optimistic. |
| Intracandle stop/target ambiguity | choose stop if both occur in one 1m candle | Conservative OHLC-only assumption. |

## 5. Ledger events and financial meaning

| Event | Meaning | Financial effect |
|---|---|---|
| `BUY_ALLOWED` | Candidate released by a fresh eligible signal inside its ceiling | Virtual entry is recorded; no actual position exists. |
| `BUY_REJECTED` | Fresh BUY existed but was above the anti-chase ceiling | No virtual position and no P&L. |
| `SELL_ALLOWED` | Virtual stop or target exit was found from later 1m candle evidence | Realized virtual P&L, fees, and cumulative scenario P&L are recorded. |
| `EXPIRED` | No release arrived within 60 minutes or before Replay ended | No virtual position and no P&L. |
| `OPEN_AT_T2` | Virtual entry exists but no stop/target occurred by end time | Mark-to-market unrealized P&L only; it is not included as realized scenario P&L. |

The ledger also stores candidate/release signal IDs, times, blocker, candidate
and decision price, entry/exit fills, notional, fee/slippage assumptions,
reason text, and cumulative realized P&L. This makes every displayed scenario
row traceable to a Replay run.

## 6. Technical design and ownership

### New database table

`replay_candidate_recovery_ledger`

- Foreign key: `test_run_id -> analysis_test_run(id)` with `ON DELETE CASCADE`.
- Unique boundary: `(test_run_id, sequence_no)` maintains a deterministic
  chronology per scenario run.
- Ownership: Replay-only.
- It is additive. No existing table/migration was edited or applied.

### Read/write boundaries

| Component | Reads | Writes |
|---|---|---|
| `ReplayCandidateRecoveryLedgerService` | `analysis_test_run`, `trade_signal_test`, `candle`, `replay_position` | `replay_candidate_recovery_ledger` only |
| Existing Replay worker | unchanged | existing Replay lifecycle tables only |
| Production execution / paper trading services | not called | none |
| Chart endpoint | candles, baseline `replay_execution`, ledger points | none |

`ReplayAccessPolicy` allow-lists the ledger table. Its existing rejection of
wallet tables remains in place. The new service contains no dependency on
`WalletService`, `PaperTradingService`, order clients, or position executors.

### API added

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/administration/regression-tests/runs/{id}/candidate-recovery-ledger` | Load persisted scenario events, totals, and baseline Replay summary. |
| `POST` | `/api/administration/regression-tests/runs/{id}/candidate-recovery-ledger` | Delete/rebuild only that run’s Replay ledger after the run is no longer pending/running. |

### Chart/UI behavior

The existing **View All Trades on Chart** payload now includes
`candidateRecoveryEvents`. The existing Apex chart renders dedicated scatter
markers for `BUY_ALLOWED`, `SELL_ALLOWED`, `BUY_REJECTED`, `EXPIRED`, and
`OPEN_AT_T2`. Baseline Replay execution remains its own blue trade path; the
scenario does not replace or modify it.

## 7. Explicitly not changed

- No Production `trade_signal`, `paper_position`, `wallet_trade`, wallet
  balance, configuration, scheduler, or execution code is written by this
  feature.
- No Production BUY blocker is relaxed, bypassed, or reclassified.
- No normal Replay position, execution, or management row is created by the
  ledger.
- No deployment, remote Git action, or database migration application occurred
  during implementation.
- The feature does not claim that OHLC candle data proves exact exchange fill
  order or executable liquidity.

## 8. Reviewer checklist

### Business reviewers

- Is the first experiment limited to the correct blocker types?
- Are score 75, raw confidence 65, 60 minutes, and 0.75% anti-chase limits
  acceptable hypotheses for evaluation rather than assumed trading rules?
- Is 100 USDT suitable as a neutral comparison size?
- Should the results be evaluated by symbol/regime/blocker separately rather
  than only as one combined P&L number?

### Technical reviewers

- Confirm that the service can write only
  `replay_candidate_recovery_ledger`.
- Confirm it refuses a `PENDING` or `RUNNING` Replay run.
- Confirm the retained event sequence is deterministic and run-scoped.
- Confirm same-candle stop/target is conservatively a stop.
- Confirm chart markers are overlays, not normal Replay executions.
- Confirm Flyway V114 is forward-only/additive and has not been applied by this
  source change.

### Risk reviewers

- Do not interpret a favorable scenario result as permission to deploy a
  Production recovery rule.
- Require held-out Replay windows, per-blocker metrics, cost sensitivity, and
  adverse/outlier review before proposing a Production design.
- Require a separate Production design/review/authorization before any live or
  paper-trading behavior could change.

## 9. Verification already completed

- Java 21 compilation passed.
- Focused tests passed: `ReplayCandidateRecoveryPolicyTest` and
  `ReplayAccessPolicyTest` (5 tests total).
- Migration was deliberately **not applied**.
- UI runtime test was not run on this workstation because Node.js is not
  installed/on `PATH`; the Java API and static UI wiring were compiled/reviewed.

## 10. Known limitations and recommended next review decision

This is intentionally an initial research instrument, not a finished recovery
strategy. Current limitations include:

1. It uses candle OHLC for virtual stop/target detection and therefore cannot
   prove intrabar order; the conservative dual-hit rule reduces, but cannot
   remove, this uncertainty.
2. It evaluates only one virtual candidate cycle at a time and does not model
   partial exits, liquidity, exchange minimums, or concurrent portfolio risk.
3. Candidate thresholds and costs are fixed constants, not user-configurable.
4. The first version excludes MTF, BTC, liquidity, and derivatives blocks on
   purpose; those vetoes need separate evidence before any recovery hypothesis.

Recommended decision after reviewers run several disjoint Replay windows:

- **Reject**: poor costed results or excessive adverse outcomes; keep as
  investigation evidence only.
- **Refine Replay experiment**: improve filters or evidence while remaining
  Replay-only.
- **Propose Production design**: only if results are robust across held-out
  windows and the team explicitly starts a separate Production safety design.

## Appendix A — File and method-level implementation map

| File | Changed element | Contract and side effects |
|---|---|---|
| `V114__replay_candidate_recovery_ledger.sql` | `CREATE TABLE replay_candidate_recovery_ledger` | Creates the sole new persistent output. It is additive, run-scoped, and cascades only when its parent Replay run is removed. |
| `ReplayCandidateRecoveryPolicy.java` | `qualifies` | Pure eligibility predicate: score/raw-confidence/blocker only; no I/O and no mutable state. |
| same | `releaseCeiling` | Calculates ATR retracement ceiling when present, otherwise candidate × 1.0075. Throws if candidate price is absent. |
| same | `adverseBuyFill`, `adverseSellFill`, `settle` | Pure virtual pricing and fee computation; no database or execution dependency. |
| `ReplayCandidateRecoveryLedgerService.java` | `rebuild(long runId)` | Transactional replay-only rebuild: deletes only this run’s existing ledger and inserts only this run’s new ledger events. Rejects a pending/running run. |
| same | `ledger(long runId)` | Read-only response: ledger events, scenario totals, and baseline `replay_position` totals. |
| same | `findExit` | Reads only 1m closed candles after virtual entry; returns first stop/target outcome or no exit. |
| `ReplayAccessPolicy.java` | allow-list registration | Allows explicit Replay CRUD operations for the new ledger table while retaining the wallet-table deny behavior. |
| `RegressionTestService.java` | `rebuildCandidateRecoveryLedger`, `candidateRecoveryLedger` | Thin delegation; no normal Replay-worker behavior changed. |
| same | `allTradesChart` | Adds read-only `candidateRecoveryEvents` response data to the existing chart endpoint. |
| `RegressionTestController.java` | GET/POST ledger endpoints | Exposes only run-scoped Replay ledger operations. |
| `proven-analyzed-trades.html/js` | panel, action, chart scatter series | Displays scenario results and marker overlays; does not alter any API result or baseline execution. |

## Appendix B — Database contract

### Table schema purpose

| Column group | Columns | Reason |
|---|---|---|
| Identity | `id`, `test_run_id`, `sequence_no`, `symbol` | Identifies one deterministic scenario event inside one Replay run. |
| Timing/decision | `event_time`, `event_type`, `candidate_signal_id`, `release_signal_id`, `blocker`, `reason` | Reconstructs the decision chronology and its evidence. |
| Price model | `candidate_price`, `decision_price`, `virtual_entry_price`, `virtual_exit_price` | Separates the original candidate, observed decision, and adverse virtual fills. |
| Cost model | `virtual_notional_usdt`, `fees_usdt`, `slippage_percent` | Makes assumptions visible instead of hiding them in P&L. |
| Results | `realized_pnl_usdt`, `realized_pnl_percent`, `unrealized_pnl_usdt`, `unrealized_pnl_percent`, `cumulative_pnl_usdt` | Separates realized result from end-window mark-to-market result. |

### Integrity/performance constraints

```sql
PRIMARY KEY (id)
UNIQUE KEY uk_replay_candidate_ledger_sequence (test_run_id, sequence_no)
KEY idx_replay_candidate_ledger_run_time (test_run_id, event_time)
FOREIGN KEY (test_run_id) REFERENCES analysis_test_run(id) ON DELETE CASCADE
```

- The unique key prevents duplicate event positions for the same rebuilt run.
- The `(test_run_id, event_time)` index supports ordered UI/chart reads.
- The foreign key prevents orphan ledger evidence. It does not point to a
  Production trade, wallet, or position table.

## Appendix C — Detailed decision pseudocode

```text
assert Replay run exists
assert run.status is not PENDING/RUNNING
delete existing ledger rows for this run only

for each Replay-generated 1m signal ordered by generated_at, id:
    expire non-terminal candidates older than 60 minutes

    if signal is blocked BUY/STRONG_BUY
       and score >= 75
       and raw confidence >= 65
       and blocker in {RANGE_ENTRY_LOCATION, ATR_RISK}:
        remember candidate (price, stop, target, ATR retracement, timestamp)
        continue

    if signal is not a final-entry-allowed BUY/STRONG_BUY:
        continue

    for each non-terminal candidate:
        ceiling = ATR retracement price if ATR blocker and available
                  else candidate price * 1.0075
        if current price > ceiling:
            persist one BUY_REJECTED anti-chase event
            retain candidate until expiry
            continue

        persist BUY_ALLOWED with adverse virtual BUY fill
        search subsequent closed 1m candles through t2:
            if low <= stop and high >= target: virtual STOP (conservative)
            else if low <= stop: virtual STOP
            else if high >= target: virtual TARGET
        if exit found:
            persist SELL_ALLOWED with adverse virtual SELL fill and realized P&L
        else:
            persist OPEN_AT_T2 with mark-to-market unrealized P&L
        mark candidate terminal

persist EXPIRED for every remaining non-terminal candidate at t2
```

No point in this path invokes the normal Replay worker, paper trading, wallet
repositories, Binance clients, order services, or Production signal processing.

## Appendix D — Transaction, concurrency, and failure behavior

| Topic | Current behavior | Review question |
|---|---|---|
| Rebuild atomicity | `rebuild` is Spring `@Transactional`; a failure rolls back deletion and inserts together. | Is a per-run database lock needed if two operators can click Build simultaneously? Current behavior is safe from partial result but last successful rebuild wins. |
| Read consistency | `ledger` is `@Transactional(readOnly=true)`. | Is repeatable-read needed for a UI read? Normally no because completed Replay data is immutable. |
| Run eligibility | Pending/running runs return HTTP 409. | Should errored/stopped runs be permitted for partial evidence? Current design permits any non-pending/non-running status. |
| Missing stop/target | No virtual exit is created from unavailable protection levels. | Should these candidates get a dedicated `INSUFFICIENT_PLAN` event in a later Replay-only revision? |
| Candle absence | No exit is inferred when matching later 1m candle evidence is unavailable. | Should a data-coverage status be persisted before comparative analysis? |
| Same-candle stop + target | Stop wins. | Confirm this conservative rule matches team policy. |
| Rebuild semantics | A POST intentionally replaces the selected run’s prior ledger; baseline Replay rows stay untouched. | Do reviewers want immutable versioned scenario runs instead? |

## Appendix E — HTTP contracts

### `POST /api/administration/regression-tests/runs/{id}/candidate-recovery-ledger`

Preconditions:

- `analysis_test_run.id` exists.
- The run is not `PENDING` or `RUNNING`.
- V114 has been applied in the target environment.

Response shape:

```json
{
  "runId": 123,
  "mode": "REPLAY_ONLY",
  "productionMutation": false,
  "paperPositionMutation": false,
  "events": ["ordered ledger rows"],
  "summary": {
    "event_count": 0,
    "buys_allowed": 0,
    "sells_allowed": 0,
    "buys_rejected": 0,
    "expired": 0,
    "realized_pnl_usdt": 0,
    "unrealized_pnl_usdt": 0
  },
  "baseline": {
    "positions": 0,
    "closed_positions": 0,
    "realized_pnl_usdt": 0
  }
}
```

### `GET /api/administration/regression-tests/runs/{id}/candidate-recovery-ledger`

Returns the same response structure without rebuilding anything. This is what
the page uses when an operator reopens a completed run.

### Existing chart endpoint extension

`GET /api/administration/regression-tests/runs/{id}/all-trades-chart`

New additive field:

```json
"candidateRecoveryEvents": [
  {
    "event_time": "...",
    "event_type": "BUY_ALLOWED",
    "decision_price": 0,
    "virtual_entry_price": 0,
    "virtual_exit_price": null,
    "realized_pnl_usdt": null,
    "unrealized_pnl_usdt": null,
    "reason": "..."
  }
]
```

Existing `candles` and `replayExecutions` fields remain unchanged.

## Appendix F — Verification matrix and remaining test gaps

| Requirement | Current evidence | Gap/next test |
|---|---|---|
| Candidate eligibility boundaries | `ReplayCandidateRecoveryPolicyTest` | Add parameterized tests for null price/ATR-retracement boundaries. |
| Cost/slippage calculation | `ReplayCandidateRecoveryPolicyTest` | Add exact negative-P&L and break-even calculations. |
| Wallet/paper table deny behavior | `ReplayAccessPolicyTest` | Add service-level boundary test with an instrumented datasource. |
| Java compilation | Java 21 Maven compile passed | None for compilation. |
| Flyway V114 syntax/application | Not applied by this change | Run guarded disposable-MySQL Flyway migration test. |
| Full ledger chronology | Not yet backed by fixture integration test | Add deterministic in-memory/disposable-MySQL case covering all event types. |
| API semantics | Compiles through controller/service wiring | Add MVC integration test for 404/409/success cases. |
| Chart rendering | Static wiring reviewed | Run browser/UI test after Node/browser test tooling is available. |
| Production non-mutation | Dependency inspection and allow-list test | Add an architecture/reachability test if this experiment is retained. |

## Appendix G — Deployment/readiness statement

This source change is ready for local Replay evaluation only after its normal
application startup performs V114 in that local/test database. It is **not** a
Production trading deployment recommendation. A team approval of this review
document approves neither a migration run nor a Production rule change.


# Replay / Production Business-Logic-Parity Redesign

**Revision:** RPR-0.4-DRAFT  
**Status:** DRAFT — NOT APPROVED — IMPLEMENTATION FORBIDDEN  
**Created:** 2026-10-10  
**Scope owner:** Codex implementation / Claude independent review

## 1. Business objective

Make manual Replay reproduce the current paper-trading Production business flow
as closely as durable historical evidence permits, while remaining completely
isolated from Production trading state and wallet data.

The exact intended flow is:

```text
historical candle + historical context + canonical accepted price events
  -> shared Production indicator/signal/decision policies
  -> replay-local virtual wallet/account state
  -> shared sizing/allocation/entry revalidation policies
  -> replay-local position lifecycle
  -> shared TP/SL/profit-lock/continuation/exit policies
  -> replay output + explicit parity certificate
```

Replay must never write `wallet_*`, `paper_position`, `trade_signal`,
`signal_processing_work`, or any other Production trading table.

The result is not a claim that historical Java scheduling, MySQL lock timing,
network delay, external Binance availability, or the historical Production
wallet balance can be reproduced. It is a verified **business-logic parity**
claim only when all required market/decision evidence exists. Otherwise Replay
remains available as a labelled simulation and may not claim parity.

## 2. Confirmed root cause and evidence

The 2026-10-10 source audit established the following:

1. Signal, entry, normal-exit, continuation, profit-lock, and near-TP policy
   use several shared Production services.
2. `ShadowProductionReplayService` separately owns replay wallet cash,
   positions, daily budget, persistence, price-loop lifecycle, and result
   labels. It starts at `INITIAL_CAPITAL=10000`, runs one symbol/position, and
   does not reproduce Production wallet/global controls.
3. A run is labelled `EXACT_PRICE_REPLAY` when at least one accepted price
   event exists, rather than proving coverage for every interval while a
   position was open.
4. Historical derivatives information is not persisted;
   `DerivativesPositioningService.evaluateHistorical()` returns UNAVAILABLE.
   Historical order-book evaluation can likewise be unavailable when snapshots
   were not retained.
5. Replay-only FIX-11K and FIX-11Q observers, and the pre-FIX-122 UI revision,
   have no current Production equivalent and must not contaminate parity runs.
6. Current Replay reads `WalletSettingsRepository` directly and its near-TP
   path can call `BinanceMinimumExecutionPolicy`, which can request a current
   live Binance minimum notional. Both violate replay account/external-input
   isolation.

## 3. Boundaries

### In scope

- Replay input/evidence qualification and run-level parity status.
- Replay-local virtual wallet state, wallet controls, positions, executions,
  daily statistics, and global limit reproduction.
- Canonical price-event coverage qualification and event ordering.
- Historical context-evidence qualification for derivatives and order book.
- Removal of approved retired Replay-only business/experiment behaviour.
- Parity certificate, diagnostics, migrations, tests, documentation.

### Out of scope

- Any Production strategy change, sizing change, exit change, or wallet change.
- Live-exchange orders, fills, fees, slippage, or order-book execution modelling.
- Replaying database locks, thread scheduling, JVM pauses, WebSocket transport,
  external REST failures, or transaction retries as historical facts.
- FIX-153, FIX-154, and FIX-155 trading-policy changes.
- Deleting Production evidence or existing historical Replay/archive rows.

## 4. Contractual modes

| Mode | Purpose | Missing evidence behaviour | Claim allowed |
|---|---|---|---|
| `BUSINESS_LOGIC_PARITY` | Reproduce Production rules using an explicitly selected virtual Replay account and complete market/decision evidence. | Refuse the run before execution. | Business-logic parity. |
| `SIMULATION` | Manual what-if/research run. | Continue but persist all approximation reasons. | Simulation only. |
| `EXPERIMENTAL_RESEARCH` | Explicit, separately approved research such as FIX-155. | Per experiment contract. | Never Production parity. |

The default Administration Replay action must be `BUSINESS_LOGIC_PARITY`; the UI
must make `SIMULATION` and `EXPERIMENTAL_RESEARCH` explicit opt-in choices.

## 5. Business-logic-parity eligibility

A `BUSINESS_LOGIC_PARITY` run must fail before generated signals or shadow wallet
state are written unless all of the following hold:

1. Every requested candle identity is closed and within the quality contract.
2. A complete immutable `ReplayAccountProfile` is supplied: starting virtual
   USDT, reserve, daily budget, daily loss state, maximum-new-position/global
   limits, and wallet settings. It is copied to replay-only tables at run start
   and is never read from or written to a Production wallet table.
3. The selected replay universe is declared. The virtual account applies
   global limits across every symbol included in that run; no claim is made
   that it reproduces the historical Production wallet's other positions.
4. The selected profile contains versioned execution constraints for relevant
   symbols, including minimum executable notional. Replay never fetches a
   current exchange constraint while evaluating historical events.
5. Every canonical direct price observation required from the first replay
   position open through its terminal close is represented by accepted
   `LEGACY`/`APPLIED` evidence, with no unresolved `PENDING` row in the whole
   consumed range.
6. Required historical order-book evidence exists whenever the feature was
   enabled for the corresponding Production decision.
7. Required historical derivatives evidence exists whenever derivatives were
   enabled and could change confidence or entry authority. Until durable
   derivatives persistence exists, affected requests must be refused in this
   mode rather than silently treating UNAVAILABLE as equivalent.
8. The exact Production configuration/version fingerprint is available for the
   requested period, or all version-dependent decision paths are marked
   unavailable and the mode is refused.

`SIMULATION` may use configured defaults or fixed starting capital but must
store each deviation in its output. It may never be displayed as parity.

### 5.1 Wallet isolation boundary

Replay never reads Production `wallet_asset`, `wallet_settings`,
`wallet_trade`, `wallet_managed_position`, `paper_position`, daily-statistics,
or wallet-snapshot rows as a decision input. The existing data does not retain
complete historical wallet state and is deliberately irrelevant to a Replay
account. A user-selected/versioned `ReplayAccountProfile` is the only account
input. This preserves safe isolation while exercising the same pure sizing and
position-management formulas.

The profile also contains symbol execution constraints used by shared
paper-order guards. `BinanceMinimumExecutionPolicy` must be split into a pure
minimum-notional evaluator plus a Production-only exchange-constraint provider;
Replay receives immutable profile values and may not issue Binance HTTP.

## 6. Agreed behaviour changes

### 6.1 Replay starting state

Replace hard-coded starting capital with an immutable `ReplayStartingState`.
It is seeded from the selected, versioned `ReplayAccountProfile` and stored
under the replay run ID before processing begins. Shadow wallet/position
services consume only this replay-local state and replay tables.

The run-start state must include profile ID/version, creation timestamp, version/config fingerprint,
virtual USDT/asset balances, daily-budget state, daily loss state, virtual
managed positions, virtual paper positions, replay idempotency keys, and a
versioned wallet/settings/configuration fingerprint. It may not be refreshed
from Production while a run is executing.

### 6.2 Shared paper-wallet business authority

Extract or complete pure shared policies for:

- initial and progressive sizing;
- reserve, daily budget, daily-loss, maximum-new-position, and global
  maximum-open-position controls;
- buy/sell/partial-harvest quantity and cost-basis calculations;
- position average entry, target/stop updates, profit-lock state, and near-TP
  state transitions;
- idempotency-key interpretation.

Production retains its current transactional repositories. Replay receives
replay-local adapters implementing the same policy inputs/outputs. No policy
may branch on `replay=true` to produce a different business decision.

### 6.3 Price evidence

Replace the boolean/label derived from “any event exists” with a coverage
result containing requested interval, covered interval, gaps, accepted event
count, rejected/unresolved count, and every position-open interval requiring
protection.

`BUSINESS_LOGIC_PARITY` rejects incomplete coverage. `SIMULATION` may use
signal-price fallback only with the persisted reason `PRICE_PATH_INCOMPLETE`.

### 6.4 Historical decision evidence

Persist versioned derivatives and order-book observations required by
Production, or mark affected historical intervals ineligible for business-logic
parity. Replay must use the same normalized evaluator and only evidence at or
before the replay clock. It must never call live Binance endpoints.

### 6.5 Event ordering and time

Use canonical UTC `observed_at` and event IDs for price ordering. Define a
single deterministic tie order matching Production’s committed sequence:

1. accepted price protection event;
2. resulting position/wallet state;
3. closed-candle analysis signal;
4. signal-driven position action.

If durable evidence cannot establish the required tie order, business-logic parity
must refuse the run. Replay timestamps remain presentation-neutral UTC;
KSA conversion belongs only to UI/reporting.

### 6.6 Retired Replay-only behaviour

The following are **candidates for removal**, subject to complete-reference
review and approval. They must be removed from baseline parity runs now; any
future research requires an independently approved module and run mode.

| Registry ID | Candidate | Proposed disposition |
|---|---|---|
| RPR-RM-01 | `DefensiveRiskReductionReplayObserver` / FIX-11K | Remove source, controller/UI/output tables only after archive/export compatibility is proved. |
| RPR-RM-02 | `OneCandleContinuationGraceReplayObserver` / FIX-11Q | Remove source, controller/UI/output tables only after archive/export compatibility is proved. |
| RPR-RM-03 | pre-FIX-122 replay revision selector | Remove the UI/API option and old comparison path; Production-current FIX-122 remains the only parity rule. |
| RPR-RM-04 | `EXPERIMENTAL` logic mode if no approved user-facing experiment consumes it | Remove after exact reference search; otherwise retain as an isolated framework with no baseline access. |

`ReplayDataSource.DATABASE` versus `DATASET` is not retired business logic:
both must remain only if a test proves equal outputs. It is a data-access
implementation choice, not an old Production-rule selector.

## 7. Implementation change registry

No implementation item is authorized until this exact revision is approved.

| ID | Area | Required change |
|---|---|---|
| RPR-01 | run contract | Add persisted mode, evidence status, account profile/state ID, version/config fingerprint, and refusal reason to the replay run. |
| RPR-02 | replay account schema/service | Add `ReplayAccountProfile` and immutable run-start-state tables. Seed virtual state only from the selected Replay profile. |
| RPR-03 | wallet authority | Extract/reuse pure paper-wallet policy and provide Production/replay persistence adapters; remove direct Replay reads of `WalletSettingsRepository`. |
| RPR-03a | execution constraints | Split minimum-notional evaluation from live exchange constraint acquisition; inject immutable profile constraints into Replay and prohibit Replay Binance HTTP. |
| RPR-04 | shadow replay | Replace fixed capital/local single-position assumptions with replay-local state and multi-symbol/global-limit coordinator. |
| RPR-05 | price evidence | Add interval coverage validator and replace the current any-event exact-price label. |
| RPR-06 | historical context | Add versioned persistence/lookup/qualification for order book and derivatives, or explicit parity refusal. |
| RPR-07 | timing/order | Persist/use deterministic committed event ordering and reject unknown ordering for parity runs. |
| RPR-08 | certificate | Add immutable parity certificate containing field comparisons, evidence counts, deviations, and final claim. |
| RPR-09 | removals | Execute only the approved RPR-RM entries, including migration/archive compatibility. |
| RPR-10 | UI/API | Make mode explicit; default to business-logic parity; display refusal/deviation/certificate details. |
| RPR-11 | tests | Add unit, integration, MySQL, complete scenario, no-look-ahead, and Production/Replay comparison tests below. |
| RPR-12 | docs | Update architecture, rules, changelog, investigation, operational runbook, and parity contract. |

## 8. Error handling, concurrency, and transactions

- Replay-state creation is isolated and must be consistent for one profile version.
- Business-logic-parity evidence validation happens before replay execution; no
  partial shadow run may be labelled a parity run.
- Replay output writes are transactionally isolated by `test_run_id`; a failed
  run records its failure and certificate status without changing Production.
- Replay must not lock Production wallet/position rows for business mutation.
- Replay-profile/state reads must not expose credentials and must log only IDs,
  UTC times, symbol counts, mode, and evidence result.
- Cancellation preserves isolated run evidence and cannot leave a completed
  parity certificate.

## 9. Logging and observability registry

| ID | Event | Mandatory fields |
|---|---|---|
| RPR-L-01 | virtual starting state created/refused | runId, mode, profile ID/version, virtual balances/position counts, config fingerprint, reason |
| RPR-L-02 | evidence qualified/refused | runId, symbol(s), from/to UTC, candle/price/orderbook/derivatives status, gap counts |
| RPR-L-03 | shadow execution decision | runId, symbol, signal/candle ID, decision code, shared-policy source, replay state ID |
| RPR-L-04 | parity mismatch | runId, field, Production value, replay value, evidence reference, severity |
| RPR-L-05 | certificate completed | runId, claim, matched/mismatched field counts, simulation deviations, duration |
| RPR-L-06 | retired path blocked | runId, removed/experimental feature ID, requested source, outcome |

## 10. Database/configuration/trading impact

- **Database:** additive replay account/snapshot/certificate/evidence schema and any
  required durable historical derivatives observations. Applied migrations are
  never edited. Archive migration/retention is mandatory before deletion of
  retired Replay-only output tables.
- **Configuration:** add replay mode/evidence policy only. Production defaults
  and trading configuration remain unchanged.
- **Trading behaviour:** NONE. Production is read-only to this feature and no
  live/paper Production decision, wallet mutation, position, or protection
  rule may change.
- **Replay behaviour:** baseline becomes evidence-qualified business-logic parity
  or explicitly labelled simulation; retired experimental paths are removed or
  isolated only as approved above.

## 11. Test and validation registry

| ID | Required proof |
|---|---|
| RPR-T-01 | The same profile inputs produce equal Production/replay sizing, reserve, daily budget, daily-loss, and global-position-limit decisions through shared policies. |
| RPR-T-01a | Replay refuses `BUSINESS_LOGIC_PARITY` when no immutable replay account profile is selected; it must never silently use a Production wallet row. |
| RPR-T-02 | A missing, unresolved, rejected, or coverage-gapped price event refuses business-logic parity and labels simulation correctly. |
| RPR-T-03 | Price-event/signal same-time ordering matches Production for TP, SL, profit lock, normal exit, and signal SELL. |
| RPR-T-04 | End-to-end initial BUY, progressive add, target extension, rebase, stop, profit lock, near-TP partial harvest, terminal exit, and P&L lifecycle match field-for-field. |
| RPR-T-05 | Historical order book/derivatives use only as-of persisted data; missing evidence refuses parity; no live HTTP occurs. |
| RPR-T-05a | Replay never reads a Production wallet settings row or calls a Binance minimum-notional endpoint; profile values drive the same pure minimum-order decision. |
| RPR-T-06 | Multi-symbol global wallet/max-position test proves no isolated-symbol bypass. |
| RPR-T-07 | Cancellation/error tests prove Production tables remain unchanged and certificates cannot falsely pass. |
| RPR-T-08 | MySQL migration and transaction tests use a disposable database. |
| RPR-T-09 | Dataset and database replay inputs produce equivalent output for the same qualified evidence, or one path is removed. |
| RPR-T-10 | Removal tests prove baseline Replay cannot invoke FIX-11K, FIX-11Q, or pre-FIX-122 paths. |
| RPR-T-11 | Real historical parity suite compares selected known Production windows and produces stored certificates. |

## 12. Risks, rollback, and acceptance

- Risk: historical data required for verified parity may not exist. Mitigation:
  refuse parity and use clearly labelled simulation; do not invent evidence.
- Risk: snapshot schema increases storage. Mitigation: bounded retention and
  archive strategy, never deletion of active/investigation evidence.
- Risk: retiring observers breaks historical UI/archive reads. Mitigation:
  migration and archive/export compatibility tests before removal.
- Rollback: disable new business-logic-parity mode and retain existing archived
  output; Production requires no rollback because it is never mutated.

Acceptance requires: all registry IDs reconciled; no unregistered code change;
all test evidence green; disposable MySQL validation; complete source review by
Claude; Codex verification of the exact reviewed revision; and explicit user
authorization after both agents approve this same document.

## 13. Approval record

| Reviewer | Status | Revision | Date |
|---|---|---|---|
| Codex | PENDING | RPR-0.4-DRAFT | — |
| Claude | PENDING | RPR-0.4-DRAFT | — |
| User implementation authorization | PENDING | RPR-0.4-DRAFT | — |

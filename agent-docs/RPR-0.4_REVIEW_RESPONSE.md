# RPR-0.4 independent-review response

**Date:** 2026-10-10  
**Status:** Analysis response only — RPR-0.4 remains rejected — no implementation authorised  
**Inputs reviewed:** Coordinator run `20261010-095027`, Claude rounds 1–3 and Codex cross-examination.

## 1. Conclusion

The independent review correctly found that RPR-0.4 is a useful direction but
is not a complete implementation contract. It must not be implemented as
written. This response does not amend or approve RPR-0.4; it records the
required shape of a replacement revision (RPR-0.5 or later).

## 2. Accepted material findings and required correction

| ID | Accepted finding | Required correction in the next complete specification |
|---|---|---|
| CR-01 | The change registry is too high-level to reconcile implementation. | Supply a registry with one ID per artifact: exact file/class/method, before/after behaviour, configuration key, database migration/table/column/index, log event/level/context, tests, documentation, rollback and Production/Replay effect. |
| CR-02 | The proposal changes shared Production classes even if the intended business-policy change is Replay-only. | Replace the claim “Production is read-only/no rollback” with a per-item impact statement. Any shared extraction or evidence capture must have Production regression tests, transaction/latency analysis and a code rollback path. No intended production trading-policy change is permitted. |
| CR-03 | Current Replay misses production orchestration, not only wallet state. | Register the close-message/price-event lineage, label-free profit-lock exit, advisory stop-loss, SELL/add suppression during an active profit lock, progressive-add gate, analysis/fill clocks, historical-only/skipped candles, same-boundary higher-timeframe visibility and daily-budget timing. Each must be either implemented for parity, classified as an allowed divergence, or cause a parity refusal. |
| CR-04 | “Parity” has no usable oracle after the virtual account diverges from Production. | Define separate outcomes: (a) **decision parity**, compared with stored as-of Production decision evidence; (b) **lifecycle parity**, only for an authorised matching virtual starting state; and (c) **simulation**, useful output that must never claim parity. Classify scheduling/concurrency differences explicitly rather than silently accepting them. |
| CR-05 | Evidence qualification is incomplete and incorrectly treats deliberate exclusions as failures. | Define an era boundary and eligible evidence set. A parity run must use the same accepted/applied price observations Production could use; `NON_CANONICAL` and `PROTECTION_NOT_APPLIED` remain deliberate exclusions, not missing evidence. Require as-of candle identity/provenance, signal mode/outcome, configuration/version, sentiment availability time and applicable order-book/derivative evidence. A gap definition belongs in simulation diagnostics; parity refuses only when required accepted evidence is absent. |
| CR-06 | Default `BUSINESS_LOGIC_PARITY` is not currently reachable for evidence eras with derivatives/configuration history missing. | Do not make an impossible mode the default. The replacement must either (1) register forward capture of the missing evidence and make parity available only from that capture date, or (2) require the caller to choose `SIMULATION` explicitly and reject a parity request with exact reasons. |
| CR-07 | The base revision, migration sequencing and deployment facts conflict. | Freeze an exact Git commit/diff identity before design approval. Reconcile the observed migration/code state with AGENTS.md and actual Flyway history using read-only evidence. The replacement spec must state the base commit, migration numbers reserved, merge order, target database compatibility and the outcome if an expected table/migration is absent. |
| CR-08 | Multi-symbol/global-budget ordering is not designed. | Either restrict the first parity scope to one virtual account/symbol with an explicit refusal for multi-symbol runs, or register deterministic cross-symbol scheduling and global-limit claim ordering in `ExecutionReplayScope` with tests. |
| CR-09 | Replay-only removals are product decisions, not automatic parity repairs. | Do not remove FIX-11K, FIX-11Q, the pre-FIX122 selector, or `EXPERIMENTAL` mode in the parity change unless their callers, persisted data and user-facing workflows are inventoried and the user approves their retirement. Until then, isolate them from baseline parity and make their outcome clearly experimental. |

## 3. Additional source findings to preserve in the next scope

The independent review identified source-backed gaps that must become registry
items rather than prose aspirations:

- Replay currently can analyse a `closed=0` candle and use an earlier candle
  snapshot. A parity run needs durable candle identity/finality checks.
- The live close path and Replay do not necessarily see the same 1m decision
  at a candle boundary. Event lineage, not a loose timestamp sort, must define
  the replay ordering rule.
- Production can subsequently close an active profit lock without rechecking
  labels, while current Replay may retain the position. It also has an advisory
  signal-path stop-loss that Replay does not invoke.
- Historical derivatives/order-book/sentiment evidence may be unavailable or
  have only event time rather than record/availability time. Unknown evidence
  must produce a diagnostic refusal for parity, not a no-look-ahead claim.
- A Replay account must never read Production `wallet_*` state. The forbidden
  scope must name the Production tables precisely while allowing Replay-owned
  test/result tables where approved.
- A manual replay run executes in the same JVM and database pool as the trader.
  Its resource limits and cancellation behaviour must protect Production
  freshness and must be tested.

## 4. Non-material corrections to include

- Include the current configuration values that affect sizing and daily limits,
  including `max-open-positions` and `max-daily-loss-percent`.
- Define behaviour when the Production daily-budget check would fail or throw.
- Clear `RegimeStateService` replay state deterministically after every run.
- State whether any new Replay evidence pre-write extends or replaces the
  current pending-evidence guard.
- Document that current paper trades use zero fees, so parity is not a claim
  about exchange-net profitability.
- Test cancellation/worker completion, two-request admission behaviour,
  curated and random/incident windows, look-ahead, and refusal-rate reporting.

## 5. Required next review package

Before another document gate, provide one exact revised specification and no
implementation. It must include:

1. A frozen source baseline and migration/deployment reconciliation.
2. The full artifact registry described in CR-01.
3. The three parity levels and their explicit oracles/refusal conditions.
4. The evidence-era and availability-time model.
5. The single-symbol-first or deterministic multi-symbol decision.
6. A user decision table for each proposed Replay-only retirement.
7. A complete test/validation/rollback/documentation plan.

Until then, `RPR-0.4-DRAFT` remains **REJECTED** and no source, database,
configuration or deployment change is authorised.

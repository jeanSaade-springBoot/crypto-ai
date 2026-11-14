# What still must be done: Replay vs Production (RPR-0.9.1)

**Date:** 2026-10-11 (KSA)  
**Source spec (unchanged):** `agent-docs/FINAL_CHANGE_SPECIFICATION_RPR-0.9.1.md`  
**This file:** remaining work only. The spec was **not** edited.  
**Run:** `agent-docs/runs/20261011-224337` (2 rounds: Codex + Claude)  
**Implementation:** not authorized. No code or spec change.

Jean target: Replay-only output that matches Production **non-wallet** decision/exit output at **98–99%** on comparable trades. Production keeps `wallet_*` / `paper_position`. Replay writes Replay output tables only.

---

## Combined remaining work (both agents, after round 2)

Nothing from FIX-156 is in current Java. 98–99% cannot be claimed today.

### 1. Decisions that still block a freeze (no code)

1. **Shared BUY validation still reads `wallet_settings`.** Replay calls `evaluateBuy` → `validateBuy` → `settingsRepository.findById(1L)` with no Replay branch (`TradeExecutionValidationService`). The draft forbids account-table reads **and** forbids Production class changes. Both agents say that pair is impossible. Owner must pick one:
   - Replay-local copy of `validateBuy` (drift risk; “no second strategy engine”), or
   - a narrow, explicitly approved scope-aware settings source in Production, or
   - treat those BUY decisions as non-comparable.
   Capture `requireNewBuyTransition` and `executionProfile` in the policy/config evidence either way.
2. **Lifecycle label.** `ANALYSIS_PARITY` disclaims lifecycle claims, while acceptance requires lifecycle parity. Add a separate lifecycle label (example: `LIFECYCLE_PARITY_FIXED_NOTIONAL`) or state that lifecycle is a reported metric only.
3. **Register corrections (write them in a later spec revision; do not edit the 0.9.1 file):**
   - **D01:** Production `validateSell` already rejects non-1m via `validateBaseSignal`. Treat as equivalent + fixture, not a Replay change.
   - **D02 / D09:** Production only suppresses SELL / blocks adds when `profitLock.active()` **and** `canUseSignalPrice` is true. If the signal price predates entry, lock is forced inactive. Replay mirrors must copy that exact condition.
   - **S07:** unfiltered Replay candle query can put **open** candles on the timeline and emit extra signals. Close as a Replay change (closed-only timeline + snapshot identity check).
   - **POSITION_STOP_LOSS:** Production `PositionManagementService.analyze` can stop first; Replay never calls it. Add to exit-class list and critical-safety gate.
   - **D08:** treat as equivalent only after proving FIX-122 `stopLossEvidence` wrappers have no side effects on non-5m signals.
4. **Per-span / per-decision, not run-wide:** `PENDING` price-event refusal and FIX-122 on/off flag.
5. **Freeze measurement rules before measuring:** coverage minimum; **n ≈ 500+** comparable units per metric (Claude Wilson bound); BUY-decision coverage as a hard gate; confidence-interval method; separate final evaluation window; zero unexplained critical-exit mismatches.
6. **Freeze source SHA-256** of the exact worktree and re-check D01–D27 / S01–S14 against it. Current tree is dirty vs the zip hashes named in the draft.

### 2. Read-only evidence still missing (E01–E10)

No database was queried in this run. Still required:

| ID | Still needed |
|----|----------------|
| E01 | Dependency graph, including `validateBuy` / `wallet_settings` and FIX-122 wrappers |
| E02 | Decision-order matrix closing D01–D27, including D23 / POSITION_STOP_LOSS and D25 tail |
| E03 | Per-span price coverage including **final tail**; collector-era vs direct event **id order** |
| E04 | Policy fingerprint contract |
| E05 | Resource budget |
| E06 | Pilot measurement |
| E07 | Signal availability / delay |
| E08 | Reconstructability / exclusion rates for S01–S14 |
| E09 | Immutable entry-fixture set + content hash |
| E10 | Historical configuration (`wallet_settings` and related) |

Without this package, 98–99% is not a number, only a target.

### 3. Replay code still to build (after dual approval + Jean approval)

Replay output tables only. Do not write Production wallet / paper_position.

**Stage 1 (changes simulated trade output)**

- Closed-only Replay timeline + identity check after each snapshot (open-candle extra signals).
- Remove zero-event fallback, FIX-11K / FIX-11Q observers, cash/sizing simulation, run-wide `EXACT_PRICE_REPLAY` / “one price event = pass”.
- Immutable Replay-owned profile / fixed-notional state (no live `wallet_settings` at run time once the owner decision in §1 is implemented).
- Profit-lock SELL suppression and add-path guard using Production’s `canUseSignalPrice` condition.
- Signal-path order: TP → lock → SL → hard floor → then SELL (when price events exist, Replay currently skips this whole block).
- Same entry clock / `markExecuted` decision object as Production.
- Drain price events **after the last signal** to window end (D25).
- Positions still open at window end = **censored**, not marked at last signal price (D27).
- Position-management (D23) and profit-lock state (D14) Replay-local mirrors + fixtures.
- Id-preserving price-event projection (`observed_at`, `id`, status).
- Real comparator (RPR7-09): field-level signal + lifecycle match, exclusions, first-divergence, per-class critical-exit gate. Today `ReplayParityValidator` is unused and the worker pass flag is `!priceEvents.isEmpty()`.
- Per-span coverage replacing S14.
- Chart endpoint from Replay fixtures, **not** `wallet_trade`.

**Stage 2 (isolation / ops, does not invent extra strategy)**

- Fail-closed forbidden account/HTTP guard, including async tasks.
- Named bounded Replay executor instead of plain `@Async`.
- Qualifier + certificate before any business output.
- Staging, atomic promotion, archive, purge.
- Flyway after preflight (no edit of applied migrations).
- Load-isolation proof; cancel/crash/restart tests; Java 21 / MySQL suite.

**Docs (after implementation, not now)**

- Architecture / changelog / trading-rules / investigation log as required.
- `fix-registry.js` FIX-156 title still says “isolated virtual account”; spec forbids account simulation.

### 4. Acceptance still required before any 98–99% label

- Separate **signal**, **BUY-decision**, and **lifecycle** rates, each on a frozen lower confidence bound.
- Coverage is a **hard gate**, not a footnote (exclusions must not shrink the denominator into a hand-picked subset).
- Zero unexplained critical mismatches (hard floor, near-TP, POSITION_STOP_LOSS, etc. — rate is the wrong tool for rare exits).
- First-divergence report on every mismatch.
- Registry reconciliation; Claude full-code review; Codex verify exact reviewed state.

---

## What is already equivalent (do not treat as remaining Replay work)

Both agents, after round 2:

- **1m-only executable SELL** is already true in Production (`validateSell` → `NON_EXECUTION_TIMEFRAME` for non-1m). Do not “add 5m/1h SELL closes” in Replay.
- Wake-up on 5m-only in Replay vs try-on-every-signal in Production is **likely equivalent for entries**, pending FIX-122 wrapper side-effect proof (D08).
- Price-path order in `LivePositionProtectionService` (TP → SL → rebase → lock → breach → normal → near-TP) is the same idea on both sides; the gap is Replay **not running** the Production signal-path / advisory / tail / lock-guard logic.

---

## Agent verdicts (completeness of this remaining-work list, not implementation)

| Round | Codex | Claude |
|-------|--------|--------|
| 1 | `VERDICT: INSUFFICIENT_EVIDENCE` | `VERDICT: REJECTED` |
| 2 | `VERDICT: INSUFFICIENT_EVIDENCE` | `VERDICT: REJECTED` |

Meaning: remaining work is identified from current Java; E01–E10 numbers are unmeasured; the spec cannot be frozen until the `wallet_settings` / Production-boundary conflict and lifecycle label are decided. **Not** an implementation go.

Coordinator MaxRounds=2 never writes `FINAL_TECHNICAL_REVIEW`. This file is the human product.

Transcripts:

- `agent-docs/runs/20261011-224337/round01-codex.txt`
- `agent-docs/runs/20261011-224337/round01-claude.txt`
- `agent-docs/runs/20261011-224337/round02-codex.txt`
- `agent-docs/runs/20261011-224337/round02-claude.txt`

---

## Codex remaining work (round 2, verbatim summary)

1. Revise and freeze the specification: correct D01; resolve shared BUY-validation/account-read conflict; add `POSITION_STOP_LOSS`; clarify lifecycle labeling; add the pre-entry signal-price/profit-lock suppression fixture; close D08 wrapper effects.
2. Produce E01–E10 read-only evidence (per-span and final-tail price coverage, accepted-event ordering, entry fixtures, historical config, signal availability, reconstructability/exclusion rates).
3. Pre-register acceptance: separate signal, BUY-decision, and lifecycle coverage/denominators; minimum counts; confidence-bound method; tolerances; final evaluation window; zero unexplained critical-exit mismatches.
4. After approvals only: Replay-output-table isolation — immutable Replay profile, local non-wallet policy mirrors, fail-closed dependency/HTTP guard, as-of qualification before output, id-preserving price evidence, per-span refusals, final-tail processing, censored end-of-window lifecycle.
5. Replace run-wide price pass/fallback labels with decision/span certificates; remove baseline observer/fallback execution; prevent `/runs/**` from reading account tables; staging + atomic promotion; bounded named Replay executor.
6. Validate with fixture parity, cancellation/crash/restart, async propagation, migration preflight, MySQL/Java 21 tests, resource-isolation load tests, and first-divergence reports before any parity label.

---

## Claude remaining work (round 2, verbatim summary)

**A. Spec revision (needed before freeze)**  
1. Owner decision on `validateBuy` `wallet_settings` reads: (i) Replay mirror of `validateBuy`, or (ii) approved scope-aware settings source in Production. Add `requireNewBuyTransition` and `executionProfile` to 3.6 and E10.  
2. Add a lifecycle result label, or state lifecycle parity is only a reported metric.  
3. Register: D01 equivalent; D08 equivalent after wrapper check; D02/D09 tied to `canUseSignalPrice`; S07 Replay change; POSITION_STOP_LOSS in D23, 7.1, 7.2.  
4. Per-span/per-decision `PENDING` refusal and FIX-122 flag.  
5. Freeze: coverage, n ≈ 500+ per metric, Wilson method, BUY-decision coverage gate, separate final window.  
6. Freeze source SHA-256 and re-verify every register row.

**B. Evidence:** E01–E10, D25 tail in E02/E03, collector-era ordering in E03.

**C. Implementation (Replay output only; after dual + user approval)**  
Stage 1: closed-only timeline + snapshot identity; remove fallback / FIX-11K/11Q / cash-sizing / run-wide flags; lock suppression and add-path with Production condition; TP/lock/SL/floor then SELL; Production entry time and `markExecuted`; D25 tail and D27 censor; D23/D14 mirrors; id-preserving prices; new comparator; per-span coverage; chart from fixtures not `wallet_trade`.  
Stage 2: forbidden-dependency guard on async; named executor; qualifier/certificate; staging/promotion; Flyway after preflight; load isolation.

**D. Acceptance:** separate rates on lower bounds; zero unexplained critical mismatches; first-divergence; registry + Claude full review; docs including `fix-registry.js` title.

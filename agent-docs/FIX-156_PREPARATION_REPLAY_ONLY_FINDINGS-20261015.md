# FIX-156 preparation findings — Replay-only, no Production code (2 rounds)

**Date:** 2026-10-15 (KSA)  
**Reviewed file (unchanged):** `agent-docs/FIX-156_IMPLEMENTATION_PREPARATION.md`  
**Run:** `agent-docs/runs/20261015-011117`  
**Owner rule for this review:** Replay must match Production **non-wallet** output. **Zero Production code changes.**  
**Implementation:** not authorized.

| Round | Codex | Claude |
|-------|--------|--------|
| 1 | `VERDICT: REJECTED` | `VERDICT: REJECTED` |
| 2 | `VERDICT: REJECTED` | `VERDICT: REJECTED` |

**Freeze-ready under “no Production touch”: no.**

Both agents say the **goal is possible** without editing Production source, but **this document is not implementable as written** under that rule.

---

## Combined findings for Jean

1. **R156-05 and R156-06 still name shared Production classes** (`ExecutionReplayScope`, `MarketPriceEventService`). That contradicts §1 / “Replay only.” The file-level registry must list only Replay-owned new/changed files and reject any shared-class edit.
2. **BUY path is the hard O-1 problem.** Replay still calls shared `evaluateBuy` → `validateBuy` → `wallet_settings`. SELL validation does **not** read wallet settings. Choose one and register it:
   - copy BUY logic into Replay (needs a drift-detection test), or
   - run **unchanged** Production BUY classes with Replay-owned fixture settings/minimums injected as dependencies (Claude recommends this).
3. **Jean rulings still needed:**
   - Does **redeploying** the one app that contains Replay count as a Production change?
   - Does an **additive Flyway** Replay table on the shared database (R156-13) count as Production touch?
4. **Canonical spec is still RPR-0.4** in `agent-docs/FINAL_CHANGE_SPECIFICATION.md`. “Section 3.3.1” (TP / SL / lock / rebase / near-TP order) is cited but not in the repo. Put RPR-0.9.12 on that canonical path before freeze.
5. **P01–P07 pending; E03–E10 missing.** Dirty worktree. No historical source for the single `wallet_settings` row or Binance min-notional unless you refuse those periods.
6. **Analysis fixtures are incomplete** (sentiment on/off, fundamentals availability, ATR, market context). D53/D54 are not the full graph. FIX-154 BUY path has no register row.
7. **99% method still unfrozen** (confidence method, denominator, exclusion cap, sealed acceptance window).
8. **Smaller than last review (not freeze blockers):**
   - Price SQL already orders by `(observed_at, id)`; Replay’s time-only re-sort keeps that order. Gap is **no event id on the DTO** for logs/certificates — fix with a **Replay-owned reader**, not by editing the shared service.
   - A named Replay executor is fine if it is **not** `taskExecutor` and uses `@Async("replayExecutor")`.
   - Regime state does nothing in baseline `PRODUCTION_PARITY` runs.

---

## Codex FINDINGS FOR JEAN (round 2)

Do not freeze under “Replay-only, zero Production-code touch” yet. Require an exact file-level registry that marks every changed/new file Replay-owned and rejects edits to shared/Production source, including `ExecutionReplayScope`, `MarketPriceEventService`, `AnalysisService`, and validation/execution services. Per path: Replay mirror, unmodified pure policy, or refusal. Add source-version drift detection, qualified async execution, and an explicit ruling on shared-schema migration.

`VERDICT: REJECTED`  
`agent-docs/runs/20261015-011117/round02-codex.txt`

---

## Claude FINDINGS FOR JEAN (round 2)

1. Not freeze-ready. Goal is achievable without Production source edits; the document still assigns changes to `ExecutionReplayScope` and the price-event service.
2. One decision unblocks most work: copy BUY logic into Replay, or run unchanged Production BUY classes with Replay-supplied fixtures. Recommend the second. SELL does not touch wallet settings.
3. Rulings: app redeploy? Flyway Replay tables on the shared DB?
4. Move RPR-0.9.12 into `agent-docs/FINAL_CHANGE_SPECIFICATION.md` so Section 3.3.1 is in the repo.
5. Round-1 downgrades: price order OK today; Replay pool won’t change Production `@Async` if not named `taskExecutor`; regime state does not carry in baseline.

`VERDICT: REJECTED`  
`agent-docs/runs/20261015-011117/round02-claude.txt`

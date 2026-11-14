# FIX-156 pre-approval findings (2 rounds)

**Date:** 2026-10-11 (KSA)  
**Reviewed file (unchanged):** `agent-docs/FINAL_CHANGE_SPECIFICATION_RPR-0.9.1.md`  
**Run:** `agent-docs/runs/20261011-230204`  
**Implementation:** not authorized. Spec not edited. No Production / DB / git change.

Jean asked for two rounds of Codex + Claude findings **before** handing this over and approving a change.

| Round | Codex | Claude |
|-------|--------|--------|
| 1 | `VERDICT: REJECTED` | `VERDICT: REJECTED` |
| 2 | `VERDICT: REJECTED` | `VERDICT: REJECTED` |

**Freeze-ready for Jean to approve implementation: no.**

Coordinator MaxRounds=2 never writes `FINAL_TECHNICAL_REVIEW`. This file is the human product.

---

## Combined findings for Jean

Both agents reject freeze. Their reasons add up; they do not cancel each other.

### Spec claims that are wrong against current Java (must fix before you redo/freeze)

1. **§3.3 signal-path order is wrong.** Production is: position management → TP → profit-lock trigger → stop loss → rebase hard floor → SELL (suppressed if lock active) → add. Near-TP is **not** on this path. §3.3 says a different order (`TP → SL → rebase → lock → SELL → near-TP → add`). D16 is closer to reality. Tests frozen to §3.3 would certify the wrong stop-loss / lock precedence.
2. **D14 / Appendix B “same profit-lock arguments” is false when Dynamic Profit Lock is off.** Production returns inactive before `ProfitLockPolicy`. Replay still calls the policy with the toggle and still runs rebase.
3. **Replay still makes a live Binance HTTP call** (`BinanceMinimumExecutionPolicy` → `GET /api/v3/exchangeInfo`) from near-TP minimum-execution evaluation. The draft forbids HTTP; D11 treats this as a quantity difference only. Outcome can depend on the network.
4. **Shared `validateBuy` still reads `wallet_settings`** (`requireNewBuyTransition`, `executionProfile`) with no Replay branch. “Remove all wallet_settings reads” plus “no Production class change” plus “keep using shared BUY validation” cannot all be true. Jean must pick: Replay-local `validateBuy` mirror, a narrow approved Production settings source, or mark those BUY decisions non-comparable.

### Current Replay still cannot support 98–99% (code facts)

- Wallet cash / sizing / daily budget still used in `ShadowProductionReplayService`.
- Zero-event fallback still exists; with any price events, the Production **signal-path** TP/lock/SL/floor block is skipped.
- Price events drained only up to each signal; **final tail** after last signal is not processed.
- FIX-11K / FIX-11Q observers still on the Replay graph.
- Run “price parity pass” is still “at least one accepted price event in the window.”
- Price projection drops event `id` and status, so equal-timestamp order cannot be audited.
- Chart / proven-trade paths can still read `wallet_trade` (`all-trades-chart`; proven-trade enrich). `/runs/{id}/fix127` reading `signal_processing_work` is **allowed** evidence, not a D26 violation.

### Design still underspecified (cannot measure 99% yet)

- Seeded lifecycle: fixture covers initial entry only. Production **adds** change average entry / SL / TP; TP-extension history lives on `wallet_managed_position`. Without that, lifecycle “parity” mixes in signal differences.
- `ANALYSIS_PARITY` disclaims lifecycle claims while §§7.1–7.2 require lifecycle gates. Need a separate lifecycle label or say lifecycle is reported only.
- E01–E10 artifacts do not exist (no coverage, config history, fixtures, dependency graph).
- No mandatory logging registry (IDs, correlation, UTC/KSA).
- File name is RPR-0.9.1; body says RPR-0.9.4-DRAFT; canonical `FINAL_CHANGE_SPECIFICATION.md` is still RPR-0.4. Worktree is dirty vs the zip hashes in the draft.
- 99% per-exit-class vs overall is unset. Rare exits (hard floor, near-TP, POSITION_STOP_LOSS) will never have enough n for their own rate; the zero-unexplained-critical-mismatch gate must carry safety, and coverage must be a hard floor so exclusions cannot shrink the denominator.

### Owner decisions needed before a later spec redo

1. How Replay stops reading `wallet_settings` without an illegal Production change (or which BUYs are non-comparable).
2. Lifecycle certificate label vs reported-only metric.
3. Whether proven-trade / `RegressionTestService` wallet reads are **in** the Replay boundary or carved out.
4. Whether 99% is overall only, with per-class critical-zero as the safety gate.
5. Seeded lifecycle: include adds, partial harvests, and TP-extension history, or exclude those lifecycles.

---

## Codex FINDINGS FOR JEAN (round 2)

This is not freeze-ready. Correct the wrong signal-path order; explicitly resolve HTTP and `wallet_settings` dependencies; define seeded-lifecycle mechanics and lifecycle certification; widen or delimit the Replay API boundary; then produce E01–E10.

`VERDICT: REJECTED`

Transcript: `agent-docs/runs/20261011-230204/round02-codex.txt`

---

## Claude FINDINGS FOR JEAN (round 2)

1. Not freeze-ready. Codex and Claude agree; reasons add up.
2. Spec claims wrong in four places: §3.3 order; D14 same-arguments; unregistered live Binance call; shared `wallet_settings` vs Replay-only/no-Production-change.
3. Jean must decide F-05 (`validateBuy` settings).
4. Governance: unambiguous revision identity; lifecycle label; logging registry; E01–E10; 99% per class vs overall.
5. Round-1 corrections: `/runs/{id}/fix127` is allowed; proven-trade `wallet_trade` is a boundary question, not a confirmed D26 gap.
6. Seeded-entry design must carry Production TP-extension history, not only TP at entry.

`VERDICT: REJECTED`

Transcript: `agent-docs/runs/20261011-230204/round02-claude.txt`

---

When you redo the spec, use this file plus the two round-2 transcripts. Do not treat this as authorization to change code. After a new revision, run another dual review on **that** exact file before approval.

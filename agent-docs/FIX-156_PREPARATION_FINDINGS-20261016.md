# FIX-156 preparation findings (2 rounds) — 2026-10-16

**Reviewed file (unchanged):** `agent-docs/FIX-156_IMPLEMENTATION_PREPARATION.md`  
**Run:** `agent-docs/runs/20261016-024616`  
**Owner rules applied:** Replay-only; no Production class edits; match non-wallet decisions/exits; **`wallet_settings` not a blocker**; later Production enhancements must also be done on Replay.  
**Implementation:** not authorized.

| Round | Codex | Claude |
|-------|--------|--------|
| 1 | `VERDICT: REJECTED` | `VERDICT: REJECTED` |
| 2 | `VERDICT: REJECTED` | `VERDICT: REJECTED` |

**Freeze-ready: no.** `wallet_settings` was not treated as a blocker.

---

## Combined remaining work (both agents)

1. **Canonical spec is still RPR-0.4** in `agent-docs/FINAL_CHANGE_SPECIFICATION.md` (virtual wallet / old flow). RPR-0.9.12 and **Section 3.3.1** (exit order) are not in the repo. Put the hashed spec on the canonical path before freeze.
2. **R156-05 / R156-06 still name shared Production classes.** Registry must list Replay-owned files only. Do not edit `ExecutionReplayScope` or `MarketPriceEventService`.
3. **Live Binance HTTP still on Replay near-TP** (`ShadowProductionReplayService` ~534 and ~687). Replace with the dated D28 fixture. Independent of wallet settings.
4. **D22 still real:** Production TP-extension rebase passes literal `true` into profit-lock; Replay passes the admin toggle.
5. **V110 already exists** (`replay_*` tables) but current Replay does not use them. Preparation §12 still says no FIX-156 code/migration started. Delete Data resets `analysis_test_run` IDs and does **not** delete `replay_*` rows — reused run IDs can attach stale fixtures. Add `replay_*` to Delete Data (or use a UUID identity).
6. **Standing Production→Replay rule is missing from the registry.** Need a mandatory “Replay impact” row plus a parity test on the same fixture whenever Production decision/exit code changes.
7. **FIX-154 BUY path** has no independently reviewable in-repo register row (mirror vs non-comparable).
8. **P01–P07 pending; E03–E10 missing.** 99% method (confidence, denominator, exclusion cap, sealed window) unfrozen.
9. **Signal-path exits in exact-price Replay:** Claude: Production still runs lock-before-SL / hard floor on the **signal** path; Replay exact mode skips that. “Retire fallback” alone loses Production behaviour. Codex: keep Replay-local mirrors with version tests rather than copying the whole execution service.

### Agent disagreement (Jean should pick)

- **Codex:** Replay-local BUY/exit **mirrors** + CI that fails if Production changes and Replay is not updated. Do not reuse shared `validateBuy`.
- **Claude:** **Reuse** shared decision services (wallet_settings waived) so future Production changes land in Replay automatically. Revise O-1 / R156-09 accordingly.

Jean’s last spoken rule (“each Production change must also be done on Replay”) matches Codex’s impact-row + test, or Claude’s reuse. The prep file currently still says Replay-local BUY mirror (O-1).

### Not blockers

- Price SQL already orders by `(observed_at, id)`; gap is missing event id on the Replay DTO.
- `wallet_settings` waived.

---

## Codex FINDINGS FOR JEAN

Do not freeze yet. First hashed in-repo spec: exit precedence, FIX-154 treatment, no-Production-file boundary, V110/DB disposition, run-ID lifecycle. Binding rule: every future Production decision/exit change needs a Replay-impact registry entry and mirror/fixture/test update. Keep wallet_settings waived; keep HTTP, shared-table, run-ID, missing evidence, undefined-precedence blockers.

`VERDICT: REJECTED` — `agent-docs/runs/20261016-024616/round02-codex.txt`

---

## Claude FINDINGS FOR JEAN

1. Register a Replay **signal-path** mirror in exact mode (lock-before-SL, direct close, hard floor). Retire-fallback alone loses Production.
2. Reuse shared decision services under the wallet_settings waiver so future Production changes apply automatically.
3. Every Production decision/exit change carries a Replay-impact row and a both-paths fixture test.
4. Fingerprint current settings per run (“current settings assumed”).
5. Small register: `replay_*` on Delete Data; `fix122_replay_revision` allow-list or retire; Replay-owned price-event id; D56 keys; FIX-154 / metric counters.
6. Install RPR-0.9.12 at canonical path; correct §12 (V110 exists); JEAN_REVIEW vs dual-agent approval.

`VERDICT: REJECTED` — `agent-docs/runs/20261016-024616/round02-claude.txt`

# FIX-156 implementation-preparation findings (2 rounds)

**Date:** 2026-10-15 (KSA)  
**Reviewed file (unchanged):** `agent-docs/FIX-156_IMPLEMENTATION_PREPARATION.md`  
**Run:** `agent-docs/runs/20261015-005147`  
**Implementation:** not authorized. Preparation file not edited.

The failed-trade WINDOW1 study was stopped first (`fts-WINDOW1-20261014-235941`, interrupted at Claude iteration 4 DB query).

| Round | Codex | Claude |
|-------|--------|--------|
| 1 | `VERDICT: REJECTED` | `VERDICT: REJECTED` |
| 2 | `VERDICT: REJECTED` | `VERDICT: REJECTED` |

**Freeze / implementation-ready: no.**

---

## Combined findings for Jean

Both agents reject freeze. The preparation is a useful inventory, not an approved specification.

1. **Gates P01–P07 are still Pending.** E03–E10 are missing. Canonical `agent-docs/FINAL_CHANGE_SPECIFICATION.md` is still RPR-0.4-DRAFT, not RPR-0.9.11/0.9.12.
2. **Section 3.3.1 is missing.** Tests T156-02a/b require that precedence oracle; this document has no such section.
3. **“No Production source changes” will not hold as written.** Replay scope goes through Production classes. R156-05/R156-06 either need explicit Production edits (with tests that Replay-off behaviour is unchanged) or new Replay-only types.
4. **Delete-data recycles `analysis_test_run` IDs.** The shared-table exception in §4.2a does not match the actual global purge + AUTO_INCREMENT reset. Certificates/logs can point at the wrong later run. Need a UUID/content-hash identity that survives purge.
5. **Replay uses Production’s default async pool.** R156-02 must name a dedicated executor, queue size, rejection behaviour, and how scope is carried to async children.
6. **Fixture contract is incomplete.** Analysis also uses sentiment, fundamentals, ATR/regime, scoring, confluence, BTC context, liquidity, derivatives. R156-04 only names BUY/DPL profile + D28 exchange constraints. Each extra input needs an as-of fixture or a refusal rule. Same for historical `wallet_settings` and Binance minimums (or refuse those periods).
7. **Boundary gaps:** `/proven-trades`, `/archives`, and several per-fix diagnostics sit outside the planned `/runs/**` cut. Current Replay still reads `wallet_settings`, shared `validateBuy`, live `exchangeInfo`, `wallet_trade` chart, fallback, and experimental observers — valid target gaps, not a green light to code.
8. **FIX-154 BUY path** is undecided: mirror or exclude.
9. **99% is still underdefined** until confidence method, denominator, exclusion cap, and overall-vs-per-class thresholds are frozen before the acceptance dataset is opened.

Claude round-2 corrections: SQL already orders prices by `(observed_at, id)`; the ID is dropped from the DTO, not mis-sorted. D48 option (a) is Jean’s recorded owner decision, but it still must land in the canonical spec before freeze.

---

## Codex FINDINGS FOR JEAN (round 2)

Useful preparation inventory, not freeze- or implementation-ready. Complete P01–P07/E03–E10; put the exact reviewed specification on the canonical path; add the missing precedence oracle; correct §4.2a for global purge/run-ID reuse; fully register as-of analysis inputs and refusals. The acceptance protocol must prevent selective exclusions, look-ahead, and Replay/Production drift before any parity percentage is interpreted.

`VERDICT: REJECTED`  
`agent-docs/runs/20261015-005147/round02-codex.txt`

---

## Claude FINDINGS FOR JEAN (round 2)

1. Not ready. P01–P07 pending, E03–E10 missing, canonical spec still RPR-0.4.
2. Two Round-1 self-corrections: price SQL order is correct (ID dropped); D48 (a) is owner decision, still needs RPR-0.9.12.
3. Choose Production-class edits vs Replay-only types for scope/price projection.
4. `/proven-trades` and `/archives` plus diagnostics are outside `/runs/**`.
5. Named Replay executor required.
6. Run identity must survive purge.
7. Decide historical sources for settings/minimums and extra analysis inputs, or refuse those runs. Decide FIX-154.

`VERDICT: REJECTED`  
`agent-docs/runs/20261015-005147/round02-claude.txt`

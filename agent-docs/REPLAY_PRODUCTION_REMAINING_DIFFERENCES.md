# What still differs: Replay vs Production (current code)

**Date:** 2026-10-11  
**Source:** current workspace Java (not agent opinions).  
**Jean target:** simulate a trade and get the same decision/exit output. **98–99% on comparable trades is enough.** Replay final output = Replay tables only. Production account = `wallet_*` / `paper_position`.  
**Implementation:** not started. This is a gap list, not a Codex/Claude score.

---

## What you already accepted (not a “bug”)

| Topic | Production | Replay | 99% treatment |
|---|---|---|---|
| Account of record | `wallet_trade`, `wallet_managed_position`, `paper_position` | `wallet_position_test`, `wallet_execution_test`, Replay run output | Do **not** compare wallet rows. Compare decision/exit **output**. |
| Cash, reserve, daily new-position cap, sizing | Real wallet + `wallet_settings` | Replay still **reads `wallet_settings` and simulates cash** today | Mark `WALLET_DRIVEN_NOT_COMPARABLE`; drop from the 99% denominator. |
| Open position after the Replay window | May exit later in live | Marks last signal price; no post-window exit | Censored; do not count as a match or a miss. |
| Live HTTP (Binance min-notional, derivatives) | Live | Historical / unavailable | Exclude from 99%. |

---

## Still differs and **will change trade output** (close these for 98–99%)

Checked in `ShadowProductionReplayService` vs `PaperTradingService` / `LivePositionProtectionService`.

| # | What still differs | Production today | Replay today | Effect on simulated trades |
|---|--------------------|------------------|--------------|----------------------------|
| 1 | SELL on 5m/1h can close | `PaperTradingService` SELL/STRONG_SELL on **any** interval, then `validateSell` | Exact-price path only sells on **1m** bearish (`"1m".equals(interval) && bearish`) | Replay holds trades Production would close on 5m/1h SELL. |
| 2 | Profit lock does not suppress SELL | Active lock **ignores** normal SELL and holds | Exact-price 1m SELL has **no** `profitLock.active()` guard before `validateSell` | Replay can SELL while Production stays in lock. |
| 3 | Profit lock blocks progressive adds | Adds only on 1m **and** lock inactive | Add path has **no** lock skip | Replay can add size while Production would not. |
| 4 | Signal-price mechanical exits when price stream exists | Signal path still checks TP / lock / SL / hard floor at the **signal** price | If any live prices exist (`exactPriceReplay`), that whole signal-price exit block is **skipped** | Some Production signal-path exits never fire in Replay. |
| 5 | Advisory position management | `PositionManagementService.analyze(signal)` runs first; can apply POSITION_STOP_LOSS | **Never called** | Replay can miss an advisory stop. |
| 6 | Zero-price-event fallback | Production has no “no ticks → use candle close as full exit engine” | `evaluateExit` + `evaluateNearTpAtSignalFallback` when `livePrices` is empty; **different rule order** (normal HTF exit in the fallback) | Old windows without price events are not Production. Must retire for baseline. |
| 7 | Price-event tail after last signal | Protection keeps running on every price until the real exit | Prices are consumed **before each generated signal**; leftover ticks to `executionEnd` are not fully drained | Stop/TP/lock in the last minutes of the window can be missed. |
| 8 | Replay still reads account settings | Lock % / sizing from **current** `wallet_settings` row 1 | Same live row at Replay time | A settings change after the original trade silently changes Replay. |
| 9 | Chart overlay queries Production wallet | n/a | `RegressionTestService.allTradesChart` **SELECT FROM wallet_trade** | Mixes Production fills into Replay UI. Does not change sim math, but violates “output-only Replay”. |
| 10 | “Passed” is not 99% trade match | n/a | `RegressionTestWorker`: `livePriceParityPass = !productionPriceEvents.isEmpty()` (one event anywhere) | Green Replay can still disagree on exits. Need a real per-trade comparison, not this flag. |
| 11 | `ReplayParityValidator` unused | n/a | Compares 13 signal fields only; **no caller** | Cannot measure 98–99% today. |
| 12 | Experimental Replay observers still on the baseline graph | Production has no FIX-11K/11Q | `DefensiveRiskReductionReplayObserver` and `OneCandleContinuationGraceReplayObserver` still invoked | Extra Replay-only behaviour beside the Production path. |
| 13 | Entry / add clocks | `Instant.now()` at fill; protection `onPrice` uses observation time | Mix of `generatedAt` vs price `observedAt`; 5m wake-up can be tens of seconds early | Same rules, different as-of time → different TP/lock hits. |
| 14 | 1m setup wake-up | Production tries setup/confirm wake-ups on the signal path | Wake-up only on **5m**; 1m goes straight to `evaluateBuy` | Some Production entries never appear in Replay. |

---

## Already aligned (do not “fix” these as if they were missing)

- Replay **does** call shared `ExecutionIntelligenceService.evaluateBuy` and `TradeExecutionValidationService.validateSell` (not a second strategy engine).
- Price-path exit order in `evaluateLivePrice` is intended to follow Production `LivePositionProtectionService.onPrice` (TP → SL → rebase floor → profit lock → normal exit → near-TP). Remaining gaps are the **signal path**, **lock suppression**, **tail drain**, and **wallet inputs** above.
- Replay already writes shadow tables (`wallet_position_test` / execution test), not Production `paper_position`. The remaining leak is **reading** `wallet_settings` and **chart** `wallet_trade`.

---

## What “98–99% similar” should mean (so the score is honest)

**In the 99% denominator (must match):**  
same symbol, same recorded entry fixture, then same **exit reason family** (TP / SL / profit-lock / validated SELL / hold-to-censor) and same **protection geometry** (SL/TP/lock on/off) on the Replay output row.

**Out of the denominator (count, do not fail the 99%):**  
wallet/sizing/min-notional quantity, live HTTP, missing historical config, missing price span, open-at-window-end, sentiment/provider races.

Until items **1–7, 10–11** are closed in Replay-only code, you cannot honestly claim 98–99% on “simulate any trade.”

---

Agents are still walking the same source on run `20261011-023447`. Their job is extra evidence. **This file is the difference list to read.** No implementation has been authorized.

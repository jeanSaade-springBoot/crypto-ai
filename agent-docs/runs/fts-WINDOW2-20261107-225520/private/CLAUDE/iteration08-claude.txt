I've finished reading the evidence. I couldn't save the file because this session is read-only (plan mode) and has no Write tool, so the full frozen study is below.

# CLAUDE_FAILED_TRADE_STUDY.md — FTS WINDOW2 (Iteration 8, FROZEN)

## Summary fields

| Field | Value |
|---|---|
| Agent / track | CLAUDE, daily failed-trade study, independent self-review (no cross-review) |
| Iteration | 8 of 8: final classification, **FROZEN** |
| Study window | WINDOW2 |
| Snapshot time | 2026-11-07 19:55:22 UTC = 2026-11-07 22:55:20 KSA |
| Snapshot drift | Intended 2026-11-07 18:00:00 KSA. The exact window clock was not preserved (from the charter). |
| Authoritative entity | `crypto_ai.paper_position` |
| Selection | `status='CLOSED' AND closed_at IS NOT NULL AND realized_pnl < 0 ORDER BY closed_at DESC LIMIT 6` |
| Frozen IDs | 182, 172, 162, 158, 143, 137. None substituted. |
| Proof queries | DBQ-FTS-001 … DBQ-FTS-005, plus Claude queries I1-001 … I7-004 |
| Total loss of the six trades | −0.273187 USDT (paper, zero fees) |
| Classification counts | **1 logic defect (143)**, 5 valid losing trades (137, 158, 162, 172, 182), 0 stale data, 0 execution delay, 0 configuration issue |
| Design gaps (not defects; candidate fixes only) | 172, 182, 162, 158 (Replay parity), plus Entry Quality and volatility at population level |
| Exit behaviour | All six exits followed the rules. 143's exit loss was a feed or observation gap, not a protection defect. |
| Main study limitation | The `CLOSED`-only selection excludes every `STOPPED` loss (75 positions, −15.876 USDT gross). The frozen sample is unrepresentative of the main way the system loses money. |
| Doc contradiction | FIX-154 code and migration V107 were live in Production before these trades. AGENTS.md and the claude-docs say "not implemented". Reported, not fixed. Needs dual-agent review. |
| Constraints followed | No code, config, DB, git or deploy writes. Did not read the Codex folders, LIVE_TEAM_STATUS.md or LIVE_EVENTS.log. |
| Output | stdout only. No Write tool in this session, so the file was not saved. |

**Time convention:** DB timestamps are UTC. KSA = UTC+3 is shown next to them. 137's exit and all of 143's events fall on the KSA date 2026-10-12.

---

## Mandatory per-trade table

| ID | Symbol | Entry UTC → KSA | Exit UTC → KSA | Entry → exit price | Realized PnL (USDT) | Close reason | Classification | Primary cause | Evidence strength | Reproducible in Replay | Candidate fix (not authorised) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **182** | ACEUSDT | 10-12 09:57:06 → 12:57:06 | 10:05:20 → 13:05:20 | 0.1884 → 0.1880 (−0.21%) | −0.031847 | SELL_CONFIRMED | **VALID LOSING TRADE**, with a design gap | Bounce entry in WEAK_DOWNTREND after 16 straight 1m STRONG_SELLs. The bearish 5m signals were masked as NEUTRAL by 1h WATCH trend score 16 (threshold 15). The same 1h signal was then used as BUY approval. Entry Quality 50, right at the cutoff. Price **never rose above entry**. | Confirmed (rules plus price) | Expected yes (does not depend on ordering); **not run** | Consider `original_decision` on masked 5m signals. Do not let one 1h context both mask the 5m and approve the BUY. |
| **172** | SHIBUSDT | 10-12 06:36:05 → 09:36:05 | 07:15:45 → 10:15:45 | 5.49e-6 → 5.48e-6 (−1 tick, −0.18%) | −0.045537 | HTF_SELL_CONFIRMED | **VALID LOSING TRADE**, with a design gap | Bought the top of a 3-tick range. The 5m 171559 used as approval had `final_entry_allowed=0` and confidence 49. `balancedBuy` ignores both. The 1m ATR (0.124%) is 0.68 of a price tick. Best move was +1 tick. | Confirmed (rules plus price) | Expected yes; **not run** | One 5m approval contract (`final_entry_allowed`) across all routes. Make the ATR plan tick-aware. |
| **162** | DOGEUSDT | 10-12 04:02:06 → 07:02:06 | 04:26:04 → 07:26:04 | 0.08705 → 0.08675 (−0.34%) | −0.051694 | STRONG_SELL | **VALID LOSING TRADE**, with a weak exemption | Late (LATE_ENTRY) accumulated-evidence entry at the 30-minute high, on a 1m WATCH. The FIX-119 guard was bypassed by one BUY among 20 observations, and that BUY's age is not checked. Entry Quality 50. Best move +0.07%. | Confirmed (rules plus price) | Expected yes; **not run** | Make the FIX-119 exemption check how recent the BUY is, or require more than one. |
| **158** | XRPUSDT | 10-12 02:35:04 → 05:35:04 | 03:30:06 → 06:30:06 | 1.4108 → 1.4090 (−0.13%) | −0.031896 | SELL_CONFIRMED | **VALID LOSING TRADE**; Replay parity gap | WATCH-1m entry through the 5m wakeup route (`FRESH_5M_CONFIRMATION`, trigger 5m 165415), at 77% of a narrow range. Planned reward:risk 0.33; the target of 0.164% is below an assumed 0.2% round-trip fee. Best move +0.06%. | Confirmed (rules); Replay divergence inferred from code | **Probably not**: Replay always orders 5m before 1m, so the wakeup's 45 s 1m age gate fails | Order Replay and Production by candle identity. Add a fee-aware net-target gate. |
| **143** | EGLDUSDT | 10-11 22:40:04 → 10-12 01:40:04 | 22:43:36 → 10-12 01:43:36 | 4.033 → 4.027 (−0.15%) | −0.037193 | PROFIT_LOCK_HARD_EXIT | **LOGIC DEFECT** on entry. **Feed or observation gap** on exit. | **Entry:** `SETUP_TIMEFRAME_ATR` approved on 5m BUY 159285 from the superseded 22:30 candle. The current 5m, 159407 WATCH, was generated 2.0 ms after the 1m and existed 3.2 s before the fill. Nothing re-checks the 5m before execution. **Exit:** peak +1.69% (4.101). In an 8.0 s EGLD tick gap the price fell from 4.100 to 4.027, straight through the lock (4.0758) and floor (4.035). Protection reacted in 25 ms. | Defect mechanism **confirmed** (timestamps plus code). That 143 would not have opened is AGENT INFERENCE. | **Probably not**: Replay sees the same-boundary 5m WATCH, so the route is closed | Select the 5m and 1h approvals by candle identity (FIX-141 pattern). Re-check the 5m in `revalidateAtExecutionPrice`. Investigate the feed gaps separately. |
| **137** | EGLDUSDT | 10-11 20:55:04 → 23:55:04 | 21:00:12 → 10-12 00:00:12 | 3.999 → 3.987 (−0.30%) | −0.075019 | SELL_CONFIRMED | **VALID LOSING TRADE** | Bounce entry after 10 straight 1m STRONG_SELLs, on the 25% exploratory route `BALANCED_NEUTRAL_5M_WATCH_1H` (FIX-11G): 5m NEUTRAL, 1h WATCH 55 minutes old. Entry Quality 58, including a recent-rejection penalty. Planned reward:risk 0.64. Best move +0.15%. | Confirmed (rules) | Expected yes; **not run** | Low priority: review the exploratory-route admission (see population findings). |

**How the entries were assessed:** each verdict uses only information available at entry time. Price movement after entry (best and worst moves, peak) is kept separate as outcome evidence and was not used to grade the entries.

---

## 1. What happened (PROJECT EVIDENCE)

| ID | 1m execution signal | 5m used as approval | 1h | Route / decision code | Exit trigger |
|---|---|---|---|---|---|
| 137 | 156723 STRONG_BUY, generated 20:55:01.576 UTC | 156721 NEUTRAL (current) | WATCH | IMMEDIATE_VALIDATION / `BALANCED_NEUTRAL_5M_WATCH_1H` (25%) | 5m SELL 156852, 21:00:02.921 UTC |
| 143 | 159408 WATCH, 22:40:01.634 UTC | **159285 BUY (22:30 candle, superseded)**. The current 159407 WATCH came 2 ms later. | NEUTRAL | SETUP_TIMEFRAME_ATR / `REDUCED_POSITION_ALLOWED`, opportunity 36524 | Tick below floor at 22:43:35.021 UTC |
| 158 | 165412 WATCH, 02:35:01.469 UTC | Trigger 165415 BUY, 21 ms after the 1m | NEUTRAL | SETUP_CONFIRMATION_WAKEUP / `FRESH_5M_CONFIRMATION` | 5m SELL 166821, 03:30:01.464 UTC |
| 162 | 167668 WATCH, 04:02:02.813 UTC | 167616 WATCH (current) | WATCH | ACCUMULATED_EVIDENCE / `OPPORTUNITY_CONFIRMED` (LATE_ENTRY, Entry Quality 50) | 1m STRONG_SELL 168277, 04:26:02.794 UTC |
| 172 | 171589 BUY, 06:36:01.507 UTC | 171559 BUY, `final_entry_allowed=0`, confidence 49 | NEUTRAL | IMMEDIATE_VALIDATION / `BALANCED_STRONG` (capped to 25% by Entry Quality 60) | 5m NEUTRAL 172588 at 07:15:04.578 UTC, after 1h SELL 172201 |
| 182 | 176710 BUY, 09:57:01.850 UTC | 176655 NEUTRAL (original decision STRONG_SELL, status CONFLICT) | 175258 WATCH, trend score 16 | IMMEDIATE_VALIDATION / `BALANCED_NEUTRAL_5M_WATCH_1H` (25%) | 5m STRONG_SELL 176934 at 10:05:03.926 UTC, after 1h 176791 dropped to trend score 13 |

**Price moves while each trade was open** (I6-001, outcome only):

| ID | Best move in its favour | Worst move against it | Ticks observed |
|---|---|---|---|
| 137 | +0.15% | −0.30% (exit at the low) | 30 |
| 143 | **+1.69%** | −0.27% | 57 |
| 158 | +0.06% | −0.13% | 856 |
| 162 | +0.07% | −0.40% | 393 |
| 172 | +1 tick | −1 tick | 200 |
| 182 | **never above entry** | −0.21% | 30 |

There are gaps at the start of the windows: 182's first tick came 20 s after entry and 172's came 34 s after entry.

## 2. Why the system entered and exited

- **Entries.** Every entry passed its route's gates under the current code, except 143, whose gate was satisfied by a superseded 5m signal.
- **Fills were prompt.** Every fill came about 3 s after the 1m candle closed, at the signal price or within one tick of it. This rules out stale data and execution delay.
- **Exits.**
  - Five of the six exited on the next price event after the signal that made the exit possible. Delays were 1.3–38.9 s, and none of them cost money (I3-004 / I4-009).
  - 143 exited through the hard floor 25 ms after the first tick below the floor.
  - None of the six hit its stop-loss.

## 3. Did the behaviour follow the intended rules?

- **137, 158, 162, 172, 182: yes.**
  - For 172 and 182 the rules themselves are inconsistent: an approval the 5m's own gate rejected was accepted, and a bearish 5m was masked and then not vetoed.
  - For 162 the rule has a weak exemption.
  - For 158 the route cannot be reproduced in Replay.
- **143: no.** The intended meaning of "the fresh 5m BUY" (the stored opportunity text) was broken. The approval came from a 5m candle that had already been replaced.

## 4. Confirmed defects and missing evidence

**Confirmed defect (one only).**
- **D-1:** the 5m approval at a shared 1m/5m boundary is chosen by analysis completion time (`generated_at`), not by candle identity, and is never re-read before execution.
- The same race appears in 7 executed entries across the population (I3-002): 3 won, 4 lost, net −1.283 USDT. The decision path is wrong, but the financial cost cannot be shown statistically from 7 entries.

**Missing evidence and unknowns.**
- **ME-1:** No Production-parity Replay has been run. Every Replay statement is inferred from code (U-I4-5).
- **ME-2:** Production logs for 2026-10-11 and 2026-10-12 were not searched.
  - `logs/crypto-ai-trader.log` starts 2026-10-19 (a local development run).
  - **Correction to earlier iterations:** `logs/` also holds `crypto-ai-trader.2026-10-12.0–10.log.gz`, `2026-10-13.0` and `2026-10-14.0`. They are gzip-compressed and my read-only tools cannot search them. Their origin (Production or local) and the time zone of their file dates are unverified.
- **ME-3:** The components of the three Entry Quality scores of exactly 50 (158, 162, 182) are not stored. A clamp versus a coincidence cannot be told apart (U-I6-2).
- **ME-4:** The cause of the 6–25 s EGLD tick gaps (stream, reconnect, or local processing) is unknown (U-I7-5).
- **ME-5:** The real Binance fee tier is unverified (U-I2-7).
- **ME-6:** Which exact build was running on 10-11 and 10-12 is not proven beyond the V107/V109 Flyway dates and the explanation texts matching the current code word for word. The Flyway `installed_on` time zone is assumed UTC.

---

## DATABASE EVIDENCE (PROJECT EVIDENCE unless marked)

All queries were read-only and bounded, and run by the coordinator from emitted DB_REQUESTs.

| Query | Finding used |
|---|---|
| DBQ-FTS-001…005 | `paper_position` schema. Frozen rows: realized PnL, open and close times, close reason, signal ID (the six rows above, total −0.273187 USDT). |
| I2-001…009 | Price ticks, profit-lock events, wallet trades, signals and the population split by reward:risk (69 below 1: −0.120 USDT; 124 at 1 or more: −4.050). 75 STOPPED positions, −15.876 USDT gross. |
| I3-001 | 159408 generated 22:40:01.633945 UTC. 159407 WATCH generated 22:40:01.635944 UTC (2.0 ms later). |
| I3-002 | 70 boundary entries where the 5m came after the 1m. 7 were approved on a stale, more bullish 5m: opportunities 5718, 13272, 18874, 26899, 35234, **36524 (143)**, 38059. Net −1.283 USDT. |
| I3-003 / I3-004 | Exit ticks. The EGLD gap from 22:43:27.038 to 22:43:35.021 UTC was EGLD-only; 12 other symbols kept delivering prices. |
| I3-005 | STOPPED losses by day: 10-12 had 19 (−4.507 USDT), and 23 closed after trade 182. |
| I4-001 | Flyway: V107 (FIX-154) installed 2026-10-10 00:15:21, V109 at 01:52:45 (assumed UTC). |
| I4-002 | `buy_htf_assessment_audit` has Production rows (`replay_mode=0`) for 137, 158, 162, 172 and 182. For 182 it records the raw 5m as NEUTRAL, while `original_decision` is STRONG_SELL. |
| I4-004 / I5-007 | Masked-bearish-5m IMMEDIATE_VALIDATION entries: 8, net −0.431 USDT. `final_entry_allowed=0` approvals: 3, net −0.026. FIX-11G route: 35 entries, net ≈ −0.29 USDT. |
| I5-001 / I5-002 / I5-006 | Execution-time Entry Quality: 137 = 58, 143 = 70, 158 = 50, 162 = 50, 172 = 60, 182 = 50. 143's decision-time score was 60 (5m ATR). 5m 159285's plan was computed at price 3.986. |
| I5-003 | 30-minute 1m windows: range position and the STRONG_SELL streaks before 137 and 182. |
| I6-001 | Best and worst moves per trade (table above). |
| I6-002 | Entry Quality × ATR band, n = 193. The loss gradient disappears inside the low and high ATR bands. ATR ≥ 0.25%: n 63, 67% losers, −4.13 USDT. |
| I6-003 | Stop distance below 2.5 ATR: 97 positions, 59% stopped, −4.94 USDT. 2.5 ATR or more: 96, 19% stopped, +0.77. |
| I6-004 | 1m signal at execution: WATCH n 115, −1.22 USDT; BUY or STRONG_BUY n 69, −2.22; NEUTRAL n 9, −0.72 (all 7 losses stopped). |
| I6-005 | 32 of 90 winners made under 0.2% gross (1.05 of 13.11 USDT won). Only 30 of 920 1m BUYs were followed by a bearish 1m one minute later. |
| **I7-001 (new)** | 75 STOPPED positions, checked over the 30 minutes after the stop-out: **38 (51%) traded back up to entry**, **7 (9%) reached take-profit** (positions 6, 64, 70, 92, 126, 170, 173), and 37 never recovered to entry. |
| **I7-002 (new)** | `atr_percent` is a percentage: recomputed `atr_at_signal / entry × 100` matches it to 4 decimal places. Frozen ATR%: 137 0.107, 143 0.270, 158 0.055, 162 0.073, 172 0.124, 182 0.080. Stop distance: 0.500% (the floor) for 137, 162, 172 and 182; 0.493% for 158; 0.565% for 143. Notional: 25 USDT, except 15 for 162 and 182. Population: 193 positions, 6,195.00 USDT total notional. |
| **I7-003 (new)** | Positions with a progressive add: CLOSED 20 of 118 (+6.016 USDT), STOPPED **24 of 75 (−7.818 USDT)**. 44 of 193 positions (23%) in total. |
| **I7-004 (new)** | 1m execution signal within 1–3 minutes after a 1m STRONG_SELL: n 6, 2 losers, 0 stopped, **+0.408 USDT**. All others: n 187, 99 losers, 75 stopped, −4.579 USDT. |

**Reading of the iteration-7 results.** Everything here is AGENT INFERENCE on the PROJECT EVIDENCE above unless marked otherwise.

- **I7-001 (stops).** Many stops were hit by noise: half the stopped positions came back to entry within 30 minutes. But only 9% went on to reach take-profit. Many also fell well below the stop afterwards, and this query cannot show the order of moves after the stop. So "wider stops would help" is **not shown**, and the stop-placement candidate stays a weak hypothesis.
- **I7-002 (ATR and stops).**
  - It confirms the implied ATRs in F-I7-08. Five of the six frozen trades are in the low-ATR band (below 0.1667%), which nets about −0.12 USDT over 69 positions.
  - The stop distances below the floor are explained by the stop being anchored to the signal price, not the fill price. For 158: 1.4109 × 0.995 versus a fill at 1.4108. This is consistent with `AtrRiskService` and is not a defect.
  - None of the six had a progressive add: each notional equals its initial allocation.
  - Note the column layout of the `population_total` row: the `stop_pct` column holds COUNT = 193.
- **I7-002 (fees).** 0.2% × 6,195 USDT ≈ **12.39 USDT** of fee drag. The fee rate is EXTERNAL RESEARCH and unverified. That is about 3× the population's paper net loss (−4.17 USDT). With real fees, the paper result would be about −16.6 USDT.
- **I7-003 (progressive adds).**
  - Stopped positions with an add lost −7.82 USDT. That is 49% of all STOPPED gross losses, from 32% of the stopped positions.
  - Adds raise both the stop and the size, so they magnify stop-out losses.
  - This also means 44 rows in I6-003 have a distorted stop-distance measure. The −4.94 / +0.77 split is therefore indicative only.
- **I7-004 (BUY after STRONG_SELL).**
  - The hypothesis that a BUY straight after a STRONG_SELL does worse is **not supported** (n = 6, net positive).
  - Reconciliation check: by candle open time, 137's STRONG_SELL candle (20:52) and 182's (09:53) fall inside the 1–3 minute window. 143's (22:33) does not: its 1m execution candle is 22:39.
  - So the group's 2 losers are consistent with being 137 and 182. This is inference; the IDs were not returned.

## LOG EVIDENCE

- **PROJECT EVIDENCE:** `logs/crypto-ai-trader.log` starts 2026-10-19 14:32:18 (local development run, PID 77592) and has no entries for the trade dates.
- **PROJECT EVIDENCE (new this iteration):** `logs/crypto-ai-trader.2026-10-12.{0..10}.log.gz`, `…2026-10-13.0.log.gz` and `…2026-10-14.0.log.gz` exist locally.
  - A plain-text Grep for `EGLDUSDT` in `.2026-10-12.0.log.gz` found nothing, because the file is compressed.
  - So **no log line was read**, and no finding in this study relies on logs.
- **Status:** every timing finding comes from persisted DB timestamps (microsecond `generated_at`, `observed_at`, `created_at`, `opened_at` and `closed_at`).
- **Log searches still to run** (coordinator or SSH):
  - `Execution Intelligence did not open a BUY`
  - `FIX-11G transitional BUY authority granted` (signal 176710)
  - `[FIX-118][PRODUCTION][LIVE_TRIGGER]` and `Live PROFIT_LOCK protected hold` (EGLD around 22:43 UTC)
  - The analysis-dispatch order at the 22:40:00 UTC boundary
  - The Binance kline stream around 22:43:27–22:43:35 UTC (ME-4)

## CODE EVIDENCE (PROJECT EVIDENCE; traced in the current working tree, version caveat ME-6)

**Defect D-1 (trade 143): how the stale 5m was selected.**
- `ExecutionIntelligenceService.java:1472-1492` (`setupTimeframeAtrAuthoritySignal`) calls `latestAtOrBefore` (`:2455-2462`).
- That lookup goes through `findTopBy…GeneratedAtLessThanEqualOrderByGeneratedAtDesc` (`:2606-2609`), with `FIVE_MINUTE_MAX_AGE` = 20 minutes (`:66`).
- So the 5m is selected by completion time, not by candle.

**D-1: why the race exists.**
- `CandleAnalysisDispatcher.java:34` serialises work only per `symbol|interval`.
- The 8–16-thread pool (`CandleAnalysisAsyncConfig.java:36-38`) runs the 1m and 5m analyses in parallel.
- Production stamps `generated_at = Instant.now()` when the analysis finishes (`AnalysisService.java:160-161`).

**D-1: nothing re-checks the 5m.**
- `PaperTradingService.java:349` calls `revalidateAtExecutionPrice` (`ExecutionIntelligenceService.java:1150-1169`).
- That re-checks only the stop, the target and Entry Quality at the fresh price.

**D-1: counterfactual (AGENT INFERENCE).**
- With 5m = WATCH, `:1486` returns null.
- The later route at `:644` then rejects with `ATR_ENTRY_BLOCKED`, because the 1m was `WAIT_FOR_RETRACEMENT` at 0%.

**Replay parity (158 and 143).**
- Replay sets `generatedAt` to the candle close (`RegressionTestWorker.java:584,616`; `AnalysisService.java:183-190`).
- It sorts signals 1h → 5m → 1m at equal timestamps (`RegressionTestWorker.java:557-560`; `ShadowProductionReplayService.java:68-71,1000`).
- Its lookups include equal timestamps (`ExecutionReplayScope.java:112`).
- The wakeup's 1m age limit is 45 s (`ExecutionIntelligenceService.java:71`, `:423-424`).
- FIX-141 already selects the BTC context by candle identity (`ExecutionReplayScope.java:122-137`; `TradeSignalRepository.java:34-39`; `BtcMarketContextService.java:84`). That pattern was not applied to the 5m and 1h approval lookups.

**172: the 5m's own gate is ignored on this route.**
- `TradeExecutionValidationService.java:81-112` passes only the 5m decision label to `HigherTimeframeBuyAssessmentService.assess` (`:20-37`) and then to `balancedBuy` (`:300-309`).
- That path never checks `final_entry_allowed`.
- The other routes do require it: `ExecutionIntelligenceService.java:1487` and `:402`.

**182: masking, then approval.**
- `MultiTimeframeConfluenceService.java:136-138` downgrades a bearish 5m to NEUTRAL when the higher-timeframe context is "bullish": decision BUY or `trend_score >= 15` (`:24`, `:337-339`).
- The veto then checks the final decision, not `original_decision` (`HigherTimeframeBuyAssessmentService.java:20-26`).
- FIX-11G grants 25% on 5m NEUTRAL + 1h WATCH (`TradeExecutionValidationService.java:263-284`).
- The audit stores `signal.getDecision()`, not `original_decision` (`BuyHtfAssessmentAuditStore.java:87`).

**162: the late-entry exemption.**
- The FIX-119 guard at `ExecutionIntelligenceService.java:1257-1280` defers only when there are zero BUY observations, and it does not check how old the BUY is.

**Entry Quality.**
- The reward:risk penalty is −30 (`:991`); the block threshold is 50 (`:229`); the rejection-penalty clamp is at `:1000-1002`; the size caps are at `:1171-1183`.
- Two ATR bases are used: 5m at decision (`:634`), 1m at execution (`:858`).
- The stored score is the execution-time score (`PaperTradingService.java:377-378`). Allocation = ATR plan × Entry Quality cap (`:359-360`).

**Stop and target geometry.**
- `AtrRiskService.java:34-45` with `application.yml:137-144`: stop = max(2 × ATR, 0.5%) capped at 8%; target = 3 × ATR. The entry-type bands are at `AtrRiskService.java:143-174`.

**Progressive add.**
- An add raises the stop and averages the entry price (`PaperTradingService.java:263-271`).

**Exits.**
- Live exits run only on the next price event: `BinanceKlineService.java:108` and `LivePositionProtectionService.java:54-72`.
- A stop-loss sets status `STOPPED` (`LivePositionProtectionService.java:174-183`). This is the root of the selection blind spot.
- Lock and floor logic: `PositionExitPolicy.java:25-33` and `:88`.
- Replay checks the floor and lock per recorded price event (`ShadowProductionReplayService.java:460-490`), so 143's exit gap should reproduce in Replay.

---

## Population context

This does not change any per-trade verdict.

1. **ATR band explains outcomes better than Entry Quality does** (PROJECT EVIDENCE: I6-002).
   - About 99% of the net loss (−4.13 of −4.17 USDT) sits in the ATR ≥ 0.25% band.
   - Entry Quality is acting mostly as a stand-in for volatility (AGENT INFERENCE: low-ATR entries pay the −30 reward:risk penalty but are rarely stopped out).
2. **Stop-outs carry the losses** (PROJECT EVIDENCE: I3, I6-003, I7-003).
   - STOPPED positions lost −15.876 USDT gross, and positions with a progressive add account for −7.82 of that.
3. **Fees would dominate in live trading** (EXTERNAL RESEARCH for the rate, PROJECT EVIDENCE for the notional).
   - About 12.39 USDT of drag against a −4.17 paper net.
   - 32 of the 90 winners would flip to losses.
4. **Withdrawn hypotheses** (PROJECT EVIDENCE):
   - "Require a current 1m BUY" (I6-004).
   - "1m labels flip within a minute" as a general claim (I6-005).
   - "A BUY straight after a STRONG_SELL does worse" (I7-004).
   - "Wider stops would help" is unproven (I7-001).
5. **Selection bias** (PROJECT EVIDENCE plus AGENT INFERENCE).
   - Five of the six frozen trades come from the low-ATR, roughly breakeven bucket. The study can say little about the system's main loss mechanism.

## 5. Candidate corrections and regression checks

These are candidates only. **NO CHANGE is authorised.** Each one needs the governed specification process and user approval.

| ID | Candidate | Basis | Expected PnL impact |
|---|---|---|---|
| C-1 | Choose the 5m and 1h approvals by candle identity (FIX-141 pattern). When the same-boundary 5m is pending, defer with a durable, bounded outcome. Re-check the 5m in `revalidateAtExecutionPrice`. | D-1 (143), F-I4-02 | Corrects the decision path; cost not proven (n = 7) |
| C-2 | Align Replay ordering with C-1 so Replay and Production use the same candle-identity rule. | F-I4-03 (143, 158, about 30 wakeup entries) | Parity, not PnL |
| C-3 | One 5m approval contract across all routes (`final_entry_allowed`, and `original_decision` when the 5m was masked). Do not let one 1h context both mask the 5m and approve the BUY. | 172, 182 | Low (8 + 3 entries) |
| C-4 | FIX-119 exemption: check how recent the BUY is, or require several. | 162 | Low |
| C-5 | Separate Entry Quality from volatility, or add a stop-in-noise or volatility term. Use one ATR basis at decision and execution. | I6-002, F-I5-04 | Population-level; needs calibration |
| C-6 | Fee- and tick-aware net-target gate. | 158, 172, I7-002 | Material if live fees apply |
| C-7 | Review the risk of progressive adds on volatile entries. | I7-003 (−7.82 USDT on stopped adds) | Potentially material; hypothesis |
| C-8 | Investigate the per-symbol feed gaps as a protection risk. | 142, 143, ME-4 | Unknown |
| C-9 | Future study selection: `status IN ('CLOSED','STOPPED')`. | F-I3-01 | Study quality |
| C-10 | Record `original_decision` in `buy_htf_assessment_audit` (forward migration only). | F-I5-08 | Auditability |

**Regression checks:**
- Same-boundary cases on every route that reads the 5m: 1m finishing before the 5m, and the 5m finishing before the 1m.
- Production-parity Replay over EGLD 2026-10-11 22:30–22:45 UTC (10-12 01:30–01:45 KSA) and XRP 2026-10-12 02:30–03:31 UTC (05:30–06:31 KSA), compared with positions 143 and 158.
- A check that Replay reproduces 143's exit gap from the stored `market_price_event` rows.
- Entry Quality calibration against a held-out set of positions, split by ATR band.
- A tick-size fixture where ATR is smaller than one tick (SHIB-like).

## Contradiction to escalate (not resolved here)

- **F-I5-08:** FIX-154 audit code (V107 / V109) was live in Production from 2026-10-10. AGENTS.md, `claude-docs/ARCHITECTURE.md:19` and `TRADING_RULES.md:124` call it pending or not implemented.
- I did not edit any docs. This needs dual-agent review before `AGREED_KNOWLEDGE.md` changes.

## Final self-review (iteration 8)

- **Time labels:** every timestamp is labelled UTC, with KSA alongside. No raw UTC is labelled as KSA.
- **Evidence labels:**
  - PROJECT EVIDENCE: DB results and code references.
  - AGENT INFERENCE: the 143 counterfactual, every Replay outcome, the Entry Quality–volatility mechanism, how I7-001, I7-003 and I7-004 are read, and the fact that none of the six had a progressive add.
  - EXTERNAL RESEARCH: the Binance fee rate and the short-horizon reversal literature cited in iteration 6.
- **Corrections in this iteration:**
  - The claim "no logs for 10-12 exist" is replaced: compressed archives exist but were not read.
  - The stop-placement hypothesis is weakened (I7-001).
  - The post-STRONG_SELL hypothesis is withdrawn (I7-004).
  - The stop-distance split is marked as distorted by progressive adds (I7-003).
- **Not claimed:**
  - That D-1 costs money overall.
  - That any Replay result has been verified.
  - That reward:risk, fees, or Entry Quality caused any of the six losses.
- **No new DB_REQUEST.** This is the final iteration. Any further evidence (logs, a Replay run) is a coordinator action.

**STATUS: FROZEN, CLAUDE iteration 8.** Implementation is not authorised.

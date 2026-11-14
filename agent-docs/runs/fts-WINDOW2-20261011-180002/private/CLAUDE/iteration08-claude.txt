# CLAUDE_FAILED_TRADE_STUDY.md (FROZEN, Iteration 8 of 8)

## Summary

| Field | Value |
|---|---|
| Agent | CLAUDE (independent track, self-review only) |
| Study window | WINDOW2 |
| Run | `agent-docs/runs/fts-WINDOW2-20261011-180002` |
| Snapshot | 2026-10-11 18:00:02 KSA (15:00:04 UTC), taken on schedule |
| Data source | `crypto_ai.paper_position`, Production paper trading (Replay table `wallet_position_test` excluded) |
| Selection rule | `status='CLOSED' AND closed_at IS NOT NULL AND realized_pnl < 0`, newest 6 by `closed_at` |
| Frozen trades | 104, 101, 96, 91, 82, 78. None substituted. |
| Total loss of the six | −0.262403 USDT gross. Paper P&L excludes fees. |
| Changes made | None. Analysis only: no code, config, DB writes, git or deploy. |
| Isolation | CODEX folders, LIVE_TEAM_STATUS.md, LIVE_EVENTS.log and the other agent's memory were not read. In iteration 3, one repo-wide grep showed two lines of a Codex stderr file. That file was never opened and nothing here relies on it. |
| Overall verdict | **1 confirmed protection defect** caused a loss (101). **5 losses followed the rules as written**, but came from weak entries the rules allow (78, 91, 96, 104, 82). One confirmed entry/exit design inconsistency (R2) was the exit mechanism in 4 trades. It made the loss smaller in 3 of them and gave up a later win in 1 (91). |

### Time convention (PROJECT EVIDENCE, DBQ-CLAUDE-I3-001)
- The database stores `timestamp(6)` values in UTC, and the application reads and writes in UTC.
- The investigation query session uses `time_zone=SYSTEM` (Arab Standard Time, +180 min). So **every value our queries return is displayed in KSA**.
- Proof:
  - signal 150942 displays 17:07:00, while the application log shows `candleOpenTime=2026-10-11T14:07:00Z`;
  - `NOW()` minus `UTC_TIMESTAMP()` is 180 minutes.
- In this document, **KSA = the value as displayed** and **UTC = displayed − 3 h**.
- This conflicts with `DATABASE_INVESTIGATION_RULES.md` and with the prompt's "DB timestamps are UTC" as it applies to query output. I am reporting the conflict, not resolving it.
- Iteration 1–2 times labelled "UTC" were really KSA. All times below are corrected.

### Iteration 8 self-review corrections (new this iteration)
- **S1. My iteration-7 claim C1 ("I5-001's join double-counted trades") was wrong.** (PROJECT EVIDENCE: DBQ-FTS-002, I5-001, I4-004, I6-005, I7-001)
  - I5-001 returned 120 rows with **64 losers**. That equals 19 losing `CLOSED` rows plus **45 `STOPPED`** rows, all losing (DBQ-FTS-002).
  - I6-005 and I7-001 filter `status='CLOSED'` and so leave out every stop-loss exit.
  - The difference therefore comes from the status filter, not from double counting.
  - I5-001, I5-002 and I4-004 are reinstated as approximate statistics for the full population. They still use the symbol + 5 s join, and a −0.80 USDT gross residual remains unexplained.
- **S2. My iteration-7 claim C5 ("the system is profitable before fees") was wrong.** (PROJECT EVIDENCE: I2-007, I6-005)
  - `CLOSED` trades: +7.19 gross (76 trades).
  - `STOPPED` trades: **−9.13** gross (45 trades, all `STOP_LOSS`: −5.950 after V107 and −3.178 before).
  - The full population is **about −1.9 to −2.7 USDT before fees** (the two figures come from I6-005 + I2-007 and from I5-001). Fees make it worse.
  - The statement "no trade closed on STOP_LOSS" was only true inside the `CLOSED` filter.
- **S3. Restart scope restated.** (PROJECT EVIDENCE: I6-001)
  - Between 18:01:01 and 18:02:19 KSA on 2026-10-10 there were **32** closed candle rows with no analysis outcome: 25 `RUNTIME` rows finalised in place and 7 `BACKFILL` 1m rows inserted.
  - Iteration 7's "29" and "218" are withdrawn. I6-002 counts the bursts using the fingerprint "closed, RUNTIME, no token"; that fingerprint does not prove "no outcome" for every row.
- **S4. The exit counterfactual is strengthened.** (PROJECT EVIDENCE: I2-007, I6-005)
  - The average `STOP_LOSS` loss is **−0.203** per trade (−9.128 over 45).
  - The average `HTF_SELL_CONFIRMED` loss is **−0.062** per trade (−0.433 over 7).
- **S5. DBQ-CLAUDE-I7-002 (the "what if we had held" check for all HTF_SELL trades) has no result file.** It was not executed, so U2 remains open.

---

## Mandatory table: one row per frozen trade

Times are shown as KSA / UTC. Prices are in USDT. "R/R" is the reward-to-risk ratio.

| ID | Symbol | Opened KSA / UTC | Closed KSA / UTC | Close reason | realized_pnl | Entry route | Entry quality (at entry time only) | Root cause (classification) | Exit mechanism, and outcome of the exit | Rules followed? | Defect? | Confidence | Replay reproducible? |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 104 | ENAUSDT | 2026-10-11 05:35:07 / 02:35:07 | 05:40:11 / 02:40:11 | HTF_SELL_CONFIRMED | −0.013345 | Direct BUY rejected (audit 1558, `BALANCED_CONFIRMATION_INSUFFICIENT`), then SETUP_WAKEUP admitted it about 1 s later (audit 1560). 1h SELL 132367 (2103 s old) treated as SOFT_CONFLICT with a 25% cap. | 1m BUY (BREAKOUT), score 79. RVOL 15.25. Price 2.91 ATR from SMA20. The last two 1m candles rose +0.76% (about 4× ATR) on 25–40× volume. R/R 1.09, or 0.52 after fees. | **VALID LOSS UNDER CURRENT RULES**: it chased a volume spike (AGENT INFERENCE). Entry-quality design gap: the 1m `atrImmediateEntryAllowed` flag did not catch a two-candle impulse (U4). | R2: an old 1h SELL plus the first 5m NEUTRAL (133376). The stop would have been touched at 05:51 KSA and the target never. **The exit avoided a larger loss.** | Yes | R2 inconsistency present; not causal | Medium-high | Expected yes (AGENT INFERENCE; not run) |
| 101 | AEROUSDT | 2026-10-11 02:12:06 / 10-10 23:12:06 | 02:21:27 / 10-10 23:21:27 | PROFIT_LOCK_HARD_EXIT | −0.004681 | Direct BUY of 15% at 0.9504 (wallet trade 257), then an add of 35% at 0.9481 (wallet trade 258). Average entry 0.948789. 1h BUY (723 s old), 5m WATCH. | Sound. Context aligned, score 83, R/R about 1.5, add made at a lower price. | **CONFIRMED PROTECTION DESIGN DEFECT (T2/R9)**, sequence in KSA: 02:18:41.676 lock activates at 0.954694 (+0.62%); 02:19:19.684 TP 0.958631 is reached and extended to 0.963551; progress drops to 67.8% (below 70%), giving `TP_EXTENSION_REBASE`; the earned lock is suspended, and the 02:20:43–45 breach of the lock is deliberately ignored; 02:21:25.7 price drops 10 ticks in about 2 s, 0.9497 to 0.9487, below the floor 0.949263 (entry × 1.0005); exit at 0.9487, below entry. | Hard-floor exit. In the next 60 min price fell to 0.9353 and the stop was touched at 02:27 KSA. The exit itself was not harmful; **the lost +0.62% lock was.** | Yes (code ran as written) | **Yes: protection design defect** | High | Likely yes, from stored price events (AGENT INFERENCE; not run) |
| 96 | PEPEUSDT | 2026-10-10 22:28:04 / 19:28:04 | 22:45:09 / 19:45:09 | HTF_SELL_CONFIRMED | −0.061576 | Direct BUY (audit 1362). 1h SELL 121720 treated as NEUTRAL, SOFT_CONFLICT, 25% cap. | Looked strong: BUY (BREAKOUT), score 83, entry quality 100. But from 22:18 to 22:30 PEPE traded only at 4.05e-6 and 4.06e-6; one tick is about 0.25%. The "breakout" was a one-tick move inside a two-tick range, and 4.06 had already printed three times. R/R in ticks 1.00; after fees 0.57. | **VALID LOSS UNDER CURRENT RULES**: a false breakout caused by tick size (AGENT INFERENCE from PROJECT EVIDENCE). Design gap: the breakout and entry-quality scoring ignore tick size. | R2: 5m NEUTRAL (122860). Highest price after entry was the entry price (0 ticks). Stop touched at 01:00 KSA on 10-11; target never. **The exit avoided a larger loss.** | Yes | R2 present; not causal | Medium-high | Expected yes (not run) |
| 91 | SHIBUSDT | 2026-10-10 21:40:07 / 18:40:07 | 21:45:30 / 18:45:30 | HTF_SELL_CONFIRMED | −0.045620 | SETUP_WAKEUP (audit 1318). 1h SELL 120186, 2404 s old, SOFT_CONFLICT, 25% cap. Used the 1m signal's stop and target (R/R 0.546), not those of the 5m trigger 121201 (R/R 1.136). | Weak: 1m WATCH at the minimum score of 60. ATR 0.09%. Stored R/R 0.546; 0.67 in ticks; **0.22 after fees**. | **VALID LOSS UNDER CURRENT RULES** from a weak entry the wake-up path allowed (E1/E2/R3). | R2: 5m NEUTRAL (121327). The exit came about 26 s later, on the next SHIB 1m price event (R4; not a defect). **Target touched first, at 23:40 KSA; stop touched at 00:44 KSA on 10-11. The early exit gave up about +0.36% gross (about +0.16% net).** Caveat: this assumes no other exit would have fired first. | Yes | **R2 inconsistency contributed to the outcome** | Medium | Expected yes (not run) |
| 82 | XLMUSDT | 2026-10-10 18:03:06 / 15:03:06 | 19:25:16 / 16:25:16 | SELL_CONFIRMED | −0.075605 | ACCUMULATED_EVIDENCE: 2 BUY signals (115479, 115502) from 17:51–17:53 KSA, before the outage. 5m signal 482 s old; 1h signal 114163, 3782 s old (16:00 KSA candle). | Poor, and flagged at the time: "Entry Quality 50/100 (LATE_ENTRY); recent rejection zone near 0.198600". The target 0.198849 sat above that resistance; the fill 0.1984 was 2 ticks below it. R/R 0.453; 0.50 in ticks; **0.07 after fees**. It re-bought 4 minutes after taking profit at 0.1983. | **VALID LOSS UNDER CURRENT RULES.** Entry-quality design weakness (E5): the rejection penalty is floored at 50, so it can never block an entry. No rule checks whether accumulated evidence spans an outage. | Valid SELL_CONFIRMED: 1m STRONG_SELL 117760 + 5m SELL 117761 + 1h WATCH. Highest price was exactly 0.1986, the known resistance. Stop touched at 20:59 KSA. **The exit avoided a larger loss.** | Yes | G2 import defect present on the XLM 5m 17:55 / 1h 17:00 candles, **not causal** | Medium | Uncertain: the restart and outage context may not be reproduced (AGENT INFERENCE) |
| 78 | PEPEUSDT | 2026-10-10 17:35:06 / 14:35:06 | 17:50:12 / 14:50:12 | HTF_SELL_CONFIRMED | −0.061576 | SETUP_WAKEUP (audit 1106). 1h SELL 114160, 2104 s old, SOFT_CONFLICT, 25% cap. Filled 1 tick above the signal price (4.06e-6 against 4.05e-6, wallet trade 195). | Weak: 1m WATCH at the minimum score of 60, RVOL 0.40. Signal R/R 1.5 became **0.76 at the fill price** (0.75 in ticks; 0.45 after fees). Nothing re-gated it. | **VALID LOSS UNDER CURRENT RULES** from a weak entry (E1/E2/E6). There is no minimum R/R at fill time (T3). | R2: 5m NEUTRAL (115428). Best move +1 tick. Stop touched at 18:11 KSA; target never. **The exit avoided a larger loss.** | Yes | R2 present; not causal | Medium-high | Expected yes (not run) |

**Labels for the table:**
- **PROJECT EVIDENCE:** IDs, times, prices, signals, audits and the counterfactual touch times.
- **AGENT INFERENCE:** "chase", "false breakout", the after-fee R/R (it applies an external fee to a paper system), and the Replay expectations.
- **EXTERNAL RESEARCH:** the 0.1% per-side fee and the tick sizes.

---

## FINDINGS

### Confirmed defects (PROJECT EVIDENCE)

**D1. Take-profit-extension rebase removes earned protection (trade 101; it caused that loss)**
- When the take-profit is extended, the lock state becomes `TP_EXTENSION_REBASE` (`DynamicProfitLockService.java:238-245`). During that state the earned lock cannot trigger (`:107-111`).
- What is left is a hard floor of entry × 1.0005 (`:164-166`; `LivePositionProtectionService.java:185-199,210`). That is +0.05%: smaller than one normal 2-second move and smaller than the round-trip fee.
- The same pattern also occurred before V107: a `PROFIT_LOCK_HARD_EXIT` loss of −0.0779 (I2-007).
- The code comment at `LivePositionProtectionService.java:187-190` ("not left completely unprotected") does not match what happens.

**D2. Startup and backfill import finalises live open candles without an analysis outcome or audit (did not cause any frozen loss)**
- `MarketDataService.java:41-55` calls `CandleRepository.upsert(..., closed=true, BACKFILL)`.
- The BACKFILL duplicate-key clause at `CandleRepository.java:173-188` sets `closed=GREATEST(...)` on an existing `closed=0` row. It keeps the `RUNTIME` source and the null token, so no dispatch, no outcome and no audit follow.
- Reconciliation cannot repair the row afterwards, because `finalizeExistingOpenFromAuthoritative` needs `closed=0`.
- The comment at `:205` ("backfill inserts missing identities only, so it cannot fabricate a runtime close") contradicts the clause at `:173-188`.
- Evidence (I6-001): 32 rows on 2026-10-10 between 18:01:01 and 18:02:19 KSA (15:01–15:02 UTC):
  - processed symbol by symbol in alphabetical order, about 2 s per symbol, 1m then 5m then 1h;
  - this includes the 1h 17:00 KSA and 5m 17:55 KSA candles for ENA, ETH, ICP, ONG, PEPE, SHIB, SOL, SUI, TAO, UNI, XLM and XRP.
- It conflicts with AGENTS.md: "Each candle/work path must write one durable terminal analysis outcome", and backfill "must never use a duplicate-key update that could finalise or alter a live row".

**D3. Design inconsistency in how the 1h SELL is read (R2/E3; exit mechanism for 78, 91, 96 and 104)**
- **Entry** softens a quality-valid 1h SELL to NEUTRAL with a 25% cap (`HigherTimeframeBuyAssessmentService.java:33-34,47`).
- **Add-on** requires the raw 1h to be WATCH or bullish, so the same 1h SELL blocks every add (`ExecutionIntelligenceService.java:1044-1045,1077-1086`).
- **Exit** loads the newest 1m, 5m and 1h signals with no age, quality or "after entry" check (`LivePositionProtectionService.java:95-97`). It then exits with `HTF_SELL_CONFIRMED` (`PositionExitPolicy.java:25-32`).
- The exit message "1h turned SELL" is false for all four trades: no new 1h signal arrived between entry and exit. The class comment at `:14` says "a fresh 1h SELL", but the code does not check freshness.
- Across all 7 `HTF_SELL_CONFIRMED` exits, 5 used a 1h signal from before entry (I4-003).
- **Effect:** in 3 of 4 frozen trades it made the loss smaller (it closed at about −0.06 against a stop average of −0.20). In 1 trade (91) it gave up a target hit. **It is not the root cause of these losses.**

### Confirmed design gaps (PROJECT EVIDENCE; not breaches of current rules)
- **G-a. No general minimum risk/reward, and no tick-size awareness.**
  - The stop distance is `max(2×ATR, 0.5%)` and the target is `3×ATR` with no minimum (`AtrRiskService.java:34-45`, `application.yml:137-144`). So R/R = 6 × ATR% whenever ATR is below 0.25%.
  - The fill-time check `revalidateAtExecutionPrice` (`ExecutionIntelligenceService.java:1150-1168`) checks only that the fill is above the stop and below the target.
  - The only R/R minimum is `BREAKOUT_RETRACEMENT_MIN_RR=1.15` (`:212,1539`).
  - Population check (I4-004, approximate): R/R below 1 gave 44 trades, −0.45; R/R 1.5 and above gave 33 trades, −2.79. **There is no proven link between R/R and loss.**
- **G-b. The wake-up path (`ExecutionIntelligenceService.java:397-457`) skips most initial-entry checks.**
  - It has no minimum R/R, no FIX-064 closed-candle price-action check, no opportunity health or momentum check, and no late-entry deferral.
  - Its chase cutoff is 50 (`:1173`). The scout path, by comparison, needs entry quality ≥80, R/R ≥1.25 and a supportive raw 1h (`:1054-1059`).
  - Population: SETUP_CONFIRMATION_WAKEUP had 32 trades, 21 losers, −0.50 across all statuses (I5-001). Among `CLOSED` only it had 22 trades and 11 losers, +0.58 gross and −0.42 net (I7-001). So 10 wake-up trades were stopped out.
  - **This is the worst loss rate of any entry stage.**
- **G-c. Paper P&L ignores fees.**
  - `WalletAutoExecutionService.java:173,260,349,443,540,653` all use `.feeUsdt(ZERO)`.
  - The profit-lock floor (+0.05%) sits inside the fee band. Among `CLOSED` trades, 13 of 30 PROFIT_LOCK_EXIT trades and 5 of 16 PROFIT_LOCK trades turn into net losers after fees (I6-005).
- **G-d. No rule checks whether accumulated evidence spans a feed outage or restart (trade 82).**
- **G-e. Exits are evaluated only on 1m price events** (`BinanceKlineService.java:95,106-108`). On quiet coins this adds latency of up to about 26 s (trade 91; no price was rejected).

### Operational findings
- **O1. The 18:00 KSA outage on 2026-10-10 was very probably an application restart.** (Strong AGENT INFERENCE from PROJECT EVIDENCE; no log line yet.)
  - Runtime writes stopped at 17:59:01. The bootstrap-pattern import ran from 18:01:01 to 18:02:19. Runtime 5m writes resumed at 18:02:21.
  - There were five restart bursts in total (I6-002, KSA):
    - 2026-10-09 10:28
    - 2026-10-10 00:16–00:18 (Flyway V107 and V108 installed at 00:15:21)
    - 2026-10-10 01:53–01:55 (V109 installed at 01:52:45)
    - 2026-10-10 18:00–18:02 (no migration)
    - 2026-10-11 02:42–02:43 (no migration)
  - This replaces the iteration-4 R7 explanation ("Binance 24-hour disconnect").
- **O2. Positions open across restart windows** (I7-003; this is evidence for the unresolved release gate on protection during application replacement):
  - 36 SHIB (00:16 burst);
  - 79 XLM (closed 17:59:03, just before the outage);
  - **80 UNI**: opened 17:44:05, closed 18:15:47 KSA with `STOP_LOSS`, −0.2349;
  - 81 AERO.
  - No frozen trade was open during a restart. Trade 82 opened about 47 s after the import finished.
  - Whether trade 80's stop was breached while protection was offline is **unknown**.
- **O3. FIX-154 is running in Production, but AGENTS.md says it is not implemented.**
  - V107 was installed 2026-10-10 00:15:21 KSA (2026-10-09 21:15:21 UTC), and live `buy_htf_assessment_audit` rows exist.
  - The code also hard-vetoes on a 1h STRONG_SELL with confidence ≥70 alone (`HigherTimeframeBuyAssessmentService.java:46`). AGENTS.md requires fresh bearish 5m confirmation as well. This did not affect any frozen trade.
  - I am reporting this contradiction, not resolving it.
- **O4. No stale-data or execution-delay defect in any of the six entries.**
  - Candle close to decision: 1.7–4.6 s. Decision to wallet: 1.4–2.0 s. Price age at fill: 0.4–2.6 s.
  - The host clock was about 2.7 s behind exchange time around 05:40 KSA on 10-11. Exchange-clock correction was active (`MarketPriceEventService.java:39-44`).

---

## DATABASE EVIDENCE (PROJECT EVIDENCE)

All queries were read-only and bounded, and run by the coordinator. Times are displayed in KSA.

| Query ID | What it showed | Status |
|---|---|---|
| DBQ-FTS-001…005 | Schema; status counts (CLOSED 74, 19 losers; **STOPPED 45, all losers**); wallet_trade cross-check (64 losing SELLs = 19 + 45); Replay table kept separate; the frozen six | Used |
| I1-001 / I6-003 | Flyway V106 installed 10-09 01:12:00; V107 and V108 10-10 00:15:21; V109 10-10 01:52:45 (KSA) | Used |
| I1-002 | Full rows for the six positions (quantity, entry, stop, target, exit, reasons) | Used |
| I1-003 / I2-008 | Entry signals: decision, score, R/R, ATR | Used |
| I1-004 | 5m and 1h signal sequences; no new 1h signal between entry and exit for 78, 91, 96 and 104 | Used |
| I1-005 / I1-008 | Exit audits; wallet trades 195, 204/205, 231, 257/258/259, 264 | Used |
| I1-006 / I2-001 | FIX-154 audits 1106, 1132, 1318, 1362, 1523, 1558, 1560 | Used |
| I2-002 / I2-003 / I2-004 | Trade 101 management events, AERO price events, wallet positions | Used |
| I2-006 | Returned empty (possible wrapper error) | Superseded by I3-002/003/004 |
| I2-007 | Results by close reason and status, before and after V107 (STOP_LOSS: 45 trades, −9.128) | Used (re-read in iteration 8) |
| I3-001 | Session time zone SYSTEM (Arab Standard Time), offset 180 min | Used |
| I3-002…006 | Signal coverage at the outage boundary; candle rows; outcomes; exit-trigger price events; highs and lows while each trade was open | Used |
| I4-001…005 | Recovery bursts; reconciliation audit 360–363; HTF timing (5 of 7 used a pre-entry 1h); R/R buckets (approximate, all statuses); backfill outcomes | Used |
| I5-001…005 | Entry stage × 1m decision (approximate join, all statuses, reinstated per S1); 1h decision at entry (approximate); PEPE and ENA candles; XLM evidence before the outage; XLM 17:55/17:00 rows with no outcome or audit | Used |
| I6-001 | 32 rows with no outcome, 18:01:01–18:02:19 KSA, 10-10 | Used |
| I6-002 | Restart bursts by minute | Used |
| I6-004 | "What if we had held" for the six trades | Used |
| I6-005 | CLOSED only: fees by close reason (76 trades, +7.19 gross, about 4.24 in fees) | Used, with the S2 caveat |
| I7-001 | CLOSED only: entry stage via the signal-ID link (76 trades, all matched) | Used |
| I7-002 | No result file | **Not executed** |
| I7-003 | Positions 36, 79, 80 and 81 spanned restart windows | Used |

---

## LOG EVIDENCE

- **Searched:** `logs/crypto-ai-trader.log`, which starts at 2026-10-11T14:08 UTC (17:08 KSA), after all six trades.
  - Used once: signal 150942 `candleOpenTime=2026-10-11T14:07:00Z`, as the time-zone anchor for I3-001. (PROJECT EVIDENCE)
- **Not searchable:** the archives for the trade period, `crypto-ai-trader.2026-10-10.*.log.gz` and `crypto-ai-trader.2026-10-11.*.log.gz`, are gzipped and I have no decompression tool. **No log evidence supports any trade-level finding.**
- **Searches still needed (coordinator):**
  - 2026-10-10 14:58–15:04 UTC (17:58–18:04 KSA): `Started`, `Historical bootstrap completed`, `[FIX-153][BACKFILL_IMPORT_RETURNED]`, shutdown hooks, `[FIX-131][WS-LIFECYCLE]`. This would prove O1.
  - `[FIX-120][PRODUCTION][TP_GEOMETRY_REBASE] positionId=130` and `REBASE_HARD_FLOOR` around 2026-10-10 23:18–23:22 UTC. This would confirm D1.
  - `[FIX-153][CLOCK_SKEW]` and `CLOCK_SYNC` around 2026-10-11 02:40 UTC.

---

## CODE EVIDENCE (PROJECT EVIDENCE: working tree; key lines re-checked in iteration 8)

- **Exit policy and inputs**
  - `PositionExitPolicy.java:14,25-33` (`HTF_SELL_CONFIRMED`), `:35-44` (`SELL_CONFIRMED`): re-verified.
  - `LivePositionProtectionService.java:95-97` (newest signal, no checks): re-verified.
  - `LivePositionProtectionService.java:100-133,185-199,207-217,235-243`.
- **Higher-timeframe assessment:** `HigherTimeframeBuyAssessmentService.java:33-34,43,46-47`, re-verified.
- **Profit lock:** `DynamicProfitLockService.java:107-111,164-166,217-245`.
- **Entry logic in `ExecutionIntelligenceService.java`**
  - Wake-up path: `:71,73,397-457,427,439,631-633`
  - Entry-quality scoring: `:865-1030`
  - Add-on rules: `:1039-1086`
  - Fill-time check: `:1150-1223`
  - Late-entry deferral: `:1257-1279`
  - R/R calculation: `:1811-1817`
  - Supportive-signal test: `:2214-2219`
  - Breakout-retracement minimum R/R: `:212,1539`
- **Validation and risk:** `TradeExecutionValidationService.java:41-43,81-113`; `AtrRiskService.java:34-45`; `application.yml:13-35,137-144`.
- **Candle import (D2):** `CandleRepository.java:141,173-188,205`, re-verified; `MarketDataService.java:41-55`, re-verified; `MarketDataBootstrapService.java:49-69,105-110`.
- **Fees:** `WalletAutoExecutionService.java:173,260,349,443,540,653`, re-verified.
- **Feed and clock:** `BinanceKlineService.java:95,106-108`; `MarketPriceEventService.java:39-44`; `BinanceWebSocketManager.java:207-209`.
- **Caveat (U6):** the working tree has uncommitted changes. The deployed exit and protection text matches the stored exit reasons exactly, but binary parity is not proven.

---

## EXTERNAL RESEARCH
- Binance exchangeInfo tick sizes: PEPEUSDT and SHIBUSDT 0.00000001; XLMUSDT, ENAUSDT and AEROUSDT 0.0001.
- Binance spot fee: 0.10% maker/taker, or 0.075% paid in BNB.
- Binance WebSocket: a connection is valid for at most 24 h; kline updates arrive every 2000 ms.
- Sources: [Binance exchangeInfo](https://api.binance.com/api/v3/exchangeInfo), [Binance Spot WebSocket Streams](https://developers.binance.com/docs/binance-spot-api-docs/web-socket-streams), [CryptoPotato – Binance fees](https://cryptopotato.com/binance-fees/).

---

## UNKNOWNS / MISSING EVIDENCE
- **U1:** A log line proving the 18:00 KSA restart (O1).
- **U2:** The HTF_SELL "what if we had held" result across all 7 such trades (I7-002 was not executed).
- **U3:** The −0.80 USDT residual between I5-001 and (I6-005 + I2-007).
- **U4:** Why trade 104's 1m `atrImmediateEntryAllowed` flag allowed a two-candle 4×ATR impulse.
- **U5:** Replay reproduction for all six trades. Not run, because running Replay creates state.
- **U6:** Whether the deployed binary matches the working tree.
- **U7:** Whether trade 80's stop (or any other) was breached while protection was offline during a restart (O2).
- **U8:** Whether the stored `stop_loss` is the original or the final, moved value. This affects the I6-004 counterfactual.

### DB_REQUEST (post-freeze follow-up only; not part of the frozen evidence)

### DB_REQUEST
QueryId: DBQ-CLAUDE-I8-001
Purpose: U7/O2. 1m low against the stored stop for UNIUSDT position 80 across the 18:00 KSA restart gap (displayed KSA). Did price breach the stop while protection was offline?
SQL:
SELECT c.open_time, c.low_price, c.close_price, c.closed, c.ingestion_source, p.stop_loss, p.exit_price, p.closed_at FROM candle c JOIN paper_position p ON p.id=80 AND c.symbol=p.symbol WHERE c.interval_code='1m' AND c.open_time BETWEEN '2026-10-10 17:55:00' AND '2026-10-10 18:16:00' ORDER BY c.open_time LIMIT 30;
### END_DB_REQUEST

### DB_REQUEST
QueryId: DBQ-CLAUDE-I8-002
Purpose: U2. Re-issue of I7-002, which did not execute: target-first or stop-first within 240 min after every HTF_SELL_CONFIRMED exit since 2026-10-03.
SQL:
SELECT p.id, p.symbol, p.exit_price, p.stop_loss, p.take_profit, p.closed_at, (SELECT MIN(c.open_time) FROM candle c WHERE c.symbol=p.symbol AND c.interval_code='1m' AND c.open_time >= p.closed_at - INTERVAL 1 MINUTE AND c.open_time < p.closed_at + INTERVAL 240 MINUTE AND c.high_price >= p.take_profit) AS first_tp_touch, (SELECT MIN(c.open_time) FROM candle c WHERE c.symbol=p.symbol AND c.interval_code='1m' AND c.open_time >= p.closed_at - INTERVAL 1 MINUTE AND c.open_time < p.closed_at + INTERVAL 240 MINUTE AND c.low_price <= p.stop_loss) AS first_sl_touch FROM paper_position p WHERE p.close_reason='HTF_SELL_CONFIRMED' AND p.closed_at >= '2026-10-03 00:00:00' ORDER BY p.id LIMIT 20;
### END_DB_REQUEST

---

## SUGGESTED FIXES: CANDIDATES ONLY (NO CHANGE IS AUTHORISED)

Each candidate needs a `FINAL_CHANGE_SPECIFICATION.md`, dual-agent or Jean review, and user approval before anything is built.

| ID | Candidate | Addresses | Regression checks |
|---|---|---|---|
| CF-1 | During a TP-extension rebase, keep the earned lock (or the higher of the earned lock and a fee-aware floor) instead of dropping to entry × 1.0005 | D1, trade 101 | Replay trade 101's AERO price events; run all PROFIT_LOCK_HARD_EXIT history pre- and post-V107 |
| CF-2 | Make BACKFILL/startup import insert-if-absent only, and route open-row finalisation through authoritative reconciliation with a durable terminal outcome and audit | D2 | Restart-at-boundary test; one terminal outcome per identity; no token, dispatch or signal |
| CF-3 | Align the exit's use of the 1h signal with the entry: freshness, `data_quality_valid`, a "generated after entry" check or the persisted entry band. **Do not tighten or remove the HTF exit on the strength of this study** (it reduced 3 of 4 losses) | D3 | I8-002 population result; Replay of 78, 91, 96, 104 |
| CF-4 | A tick-aware and fee-aware minimum R/R at fill time, including the wake-up path | G-a, G-b, G-c | Population R/R buckets via the signal-ID link, including STOPPED |
| CF-5 | Model fees in paper P&L, or at least in the profit-lock floor | G-c | Re-run I6-005 including STOPPED |
| CF-6 | Protection coverage for open positions across restarts (existing release gate) | O2 | I8-001; restart drill with an open position |
| CF-7 | Governance: reconcile the AGENTS.md FIX-154 status and its hard-veto rule with the deployed code; reconcile the DB time-zone rule with how the query wrapper displays times | O3, time convention | Documentation review only |

---

## Labels
- **PROJECT EVIDENCE:** all DB query results, code references, the time-zone proof, D1, D2, D3, G-a to G-e (as facts), and O2–O4.
- **AGENT INFERENCE:**
  - O1 (restart; strong but no log);
  - "weak entry", "chase" and "false breakout" as wording;
  - the trade 91 counterfactual;
  - after-fee R/R for a paper system;
  - the Replay expectations;
  - the approximate population joins (I4-004, I5-001, I5-002).
- **EXTERNAL RESEARCH:** tick sizes, the fee schedule, and Binance WebSocket limits.

**STATUS: FROZEN.** Iteration 8 of 8. Claude independent track. Not reviewed by Codex. Not approved for any implementation.

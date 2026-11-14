I've checked the iteration-7 query results against the code. Here is the frozen final study.

# CLAUDE_FAILED_TRADE_STUDY.md (WINDOW1, final, iteration 8 of 8)

## Summary fields

| Field | Value |
|---|---|
| Agent | CLAUDE (independent track, self-review only; no cross-review) |
| Study window | WINDOW1 |
| Snapshot | 2026-10-11 07:00:03 UTC = 2026-10-11 10:00:01 KSA (on schedule) |
| Authoritative entity | `crypto_ai.paper_position` |
| Frozen trades | 104, 101, 96, 91, 82, 78. All 6 analysed, none substituted. |
| Total realized PnL | −0.2624 USDT (sum of the six `realized_pnl` values, DBQ-FTS-005) |
| Writes performed | None (no code, config, DB, git, doc or deploy writes). The plan file was not written because this session has no Write tool; the study is in this output only. |
| Isolation | I did not read Codex's private or db folders, `LIVE_TEAM_STATUS.md`, `LIVE_EVENTS.log` or Codex memory. |
| Status | **FROZEN.** All suggested fixes are candidates only. **NO CHANGE is authorised.** |

**Time basis** (PROJECT EVIDENCE: I3-001/002, confirmed against Binance klines in iteration 7 SC-1):
- The DB stores UTC in `TIMESTAMP(6)` columns.
- The query wrapper's session runs on "Arab Standard Time", so it **displays** values in KSA and reads SQL literals as KSA.
- Below, **KSA = displayed value** and **UTC = displayed − 3 h**.
- This contradicts the "raw timestamps are UTC" wording in `DATABASE_INVESTIGATION_RULES.md` and the charter, as far as wrapper output is concerned. I'm reporting it, not editing anything.

**Headline (AGENT INFERENCE built on PROJECT EVIDENCE):**
- No loss was caused by stale data or slow execution.
- All six trades followed the code as written.
- **Four losses (78, 91, 96, 104) share one logic-defect candidate, RC-1.** One unchanged hourly SELL counts as a neutral soft conflict at entry and then forces the exit (`HTF_SELL_CONFIRMED`) on the first 5m bar that isn't bullish.
- For 78, 91 and 96, coarse price ticks (F-35) are a second primary cause.
- **82** was a weak entry (reward/risk 0.45, admitted by a score clamp). A confirmed restart defect also hit the data path upstream (F-21), but its causal link to the loss is unproven.
- **101** was a valid entry that reached its target. The FIX-118 profit-lock rebase then turned it into a loss.

---

## Mandatory trade table

Prices are shown in units of 1e-6 for PEPE and SHIB. Exit price is the price of the wallet SELL fill.

| ID | Symbol | Entry KSA (UTC) | Exit KSA (UTC) | Hold | Entry → exit price | PnL (USDT) | Close reason | Primary cause | Rules followed? | Classification | Replay should reproduce? |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **104** | ENAUSDT | 10-11 05:35:07.08 (02:35:07.08) | 05:40:11.25 (02:40:11.25) | 5m04s | 0.2248 → 0.2246 | −0.013345 | HTF_SELL_CONFIRMED | RC-1, plus a chase of an over-extended spike (F-29) | Yes | Logic-defect candidate (RC-1) and a weak entry | Yes (deterministic). Not run. |
| **101** | AEROUSDT | 10-11 02:12:06.56 (10-10 23:12:06.56) | 02:21:27.16 (23:21:27.16) | 9m21s | 0.948789 average (0.9504 first fill, plus a 35% add at 0.9481) → 0.9487 | −0.004681 | PROFIT_LOCK_HARD_EXIT | RC-3: the trade reached its take-profit; the FIX-118 extension/rebase then dropped the executable lock to a hard floor | Yes | Valid entry. Exit-policy weakness (F-13), not an implementation defect | Yes. Not run. |
| **96** | PEPEUSDT | 10-10 22:28:04.36 (19:28:04.36) | 22:45:09.70 (19:45:09.70) | 17m05s | 4.06 → 4.05 | −0.061576 | HTF_SELL_CONFIRMED | RC-1 plus tick geometry (F-35): stop 2.0 ticks away, target 2.7 ticks | Yes | Logic-defect candidate (RC-1). Fair signal, untradeable geometry | Yes. Not run. |
| **91** | SHIBUSDT | 10-10 21:40:07.27 (18:40:07.27) | 21:45:30.85 (18:45:30.85) | 5m23s | 5.48 → 5.47 | −0.045620 | HTF_SELL_CONFIRMED | RC-1 plus F-35 (target 1.5 ticks away; ATR smaller than one tick), on a 1m WATCH exactly at the support threshold of 60 (F-28) | Yes | Logic-defect candidate (RC-1). Low-quality entry | Yes. Not run. |
| **82** | XLMUSDT | 10-10 18:03:06.24 (15:03:06.24) | 19:25:16.12 (16:25:16.12) | 1h22m10s | 0.1984 → 0.1978 | −0.075605 | SELL_CONFIRMED | RC-2: reward/risk 0.45 from the 0.5% stop floor (F-24); the entry-quality clamp held the score at exactly 50 (F-23). 1h context was 63 min old because of the startup import (F-21) | Yes | Valid but weak losing trade. Confirmed upstream data-path defect F-21, causal link unproven | The entry logic, yes. F-21 is a Production restart effect and won't appear in Replay. |
| **78** | PEPEUSDT | 10-10 17:35:06.80 (14:35:06.80) | 17:50:12.87 (14:50:12.87) | 15m05s | 4.06 (signal price 4.05; 1-tick fill drift, F-25) → 4.05 | −0.061576 | HTF_SELL_CONFIRMED | RC-1 plus F-35. Entered on a 1m WATCH at 60 with RVOL 0.40 (F-28); reward/risk at the fill was 0.76 | Yes | Logic-defect candidate (RC-1). Low-quality entry | Yes. Not run. |

**Data freshness was clean for all six** (F-18):
- execution prices were 0.9–3.8 s old;
- signals were generated 2–7 s after their candle closed;
- `data_quality_valid=1` and LIVE mode throughout.

---

## Root causes and findings

### RC-1: one hourly SELL is soft at entry and forces the exit (78, 91, 96, 104)
*CONFIRMED. PROJECT EVIDENCE.*

**Entry side:**
- `HigherTimeframeBuyAssessmentService.java:47` counts any quality-valid 1h SELL as a *soft* conflict, at any confidence.
- Only a `STRONG_SELL` with confidence ≥ 70 is a hard veto (`:46`).
- A soft conflict becomes NEUTRAL with a 25% size cap (`:33-34`).

**Exit side:**
- `PositionExitPolicy.java:25-32` exits `HTF_SELL_CONFIRMED` when the 1h is bearish and the 5m isn't supportive.
- It uses the newest 1h signal with no age check and no comparison to when the position opened (`LivePositionProtectionService.java:95-97`).
- The Javadoc at `:14` promises a "**fresh** 1h SELL".

**Production behaviour:**
- I3-012: for all four trades, `new_1h_during_hold=0`. The same 1h SELL was in force at both entry and exit.
- The 1h signals were 114160 (78), 120186 (91), 121720 (96) and 132367 (104), with confidence 70–71.

**Why it loses by construction:**
- The wake-up entry needs a fresh 5m flip to BUY (`ExecutionIntelligenceService.java:408-412`).
- The first 5m bar that isn't BUY or WATCH then exits the position. Holds lasted 5–17 minutes.

**Prevalence** (I4-008, outcome-based): since V107, 5 positions were entered under a 1h SELL. 4 lost, all `HTF_SELL_CONFIRMED`; net −0.1635 USDT.

### RC-2: weak geometry plus the restart defect (82)
*CONFIRMED in code and DB. Clamp binding is AGENT INFERENCE from arithmetic.*

**Geometry (F-24):**
- The stop is max(2·ATR, 0.5%) and the target is 3·ATR (`AtrRiskService.java:34-45`).
- At ATR 0.0755% that gives reward/risk 0.453.
- Reward/risk is only a score penalty on the direct, accumulated-evidence and wake-up routes. There is no minimum.

**Score clamp (F-23):**
- `ExecutionIntelligenceService.java:995-1003` never lets the −12 recent-rejection penalty push the score below 50.
- 82's stored score is exactly 50 (LATE_ENTRY). That needs a pre-penalty score of 62, which the penalty set can't produce, so the clamp was binding.

**Restart defect (F-21):**
- At about 18:00 KSA (15:00 UTC) on 10-10, the startup import's duplicate-key path closed open RUNTIME candles: `closed=GREATEST(closed,VALUES(closed))` (`CandleRepository.java:173-188`).
- It wrote no close-transition token, no analysis outcome and no signal for 19/21 symbols at 1h and 5m, and 11 at 1m (I4-003/004).
- A second episode followed at 02:42–02:43 KSA on 10-11.
- Effect on 82: the 1h context was signal 114163 (WATCH), 3782 s old.
- This breaches two `AGENTS.md` rules: one durable terminal outcome per candle, and no duplicate-key finalisation of live rows.
- Whether the missing 17:00 KSA 1h analysis would have changed 82's entry is **unknown**.

### RC-3: exit policy, not entry (101)
*CONFIRMED. PROJECT EVIDENCE: I2-002…005; `DynamicProfitLockService.java:107-110,164-166,238-242`.*

| KSA (10-11) | Event |
|---|---|
| 02:18:41.685 | Profit lock activated at 0.954694 |
| 02:19:19.684 | Price 0.9588 ≥ take-profit 0.95863. The continuation check passed and the take-profit was extended to 0.963551. The only `position_management_event` row for this trade, `TAKE_PROFIT_EXTENDED` (I7-001), is from this step. |
| — | Progress against the new target was 67.81%, below the 70% activation threshold. State became `TP_EXTENSION_REBASE` and the hard floor became 0.949263. |
| 02:20:45.709 | Price 0.9541, below the old lock. It did not trigger, by design. |
| 02:21:25.691 | Price 0.9487, below the floor. Exit followed. |

- **Classification:** a policy weakness (a 0.57% give-back band), not an implementation defect.
- **Secondary point (F-22):** the 1h BUY counted as bullish had confidence 49 and `final_entry_allowed=0`. The HTF assessment reads only decision and confidence.

### Contributing and secondary findings

| ID | Finding | Status | Label |
|---|---|---|---|
| F-35 | Coarse ticks on PEPE and SHIB: one tick is 0.18–0.25% of price, SHIB's ATR is smaller than one tick, and stop/target sit 1.5–3.4 ticks away. This is a primary factor for 78, 91 and 96 (it promotes F-26). | Holds | PROJECT EVIDENCE (I1-002, I6-003); tick size inferred from price spacing (AGENT INFERENCE) |
| F-24 | No minimum reward/risk on the direct, accumulated-evidence and wake-up routes | Confirmed in code | PROJECT EVIDENCE |
| F-25 | Fill drift is accepted without re-anchoring the stop and target. In 78, one tick of drift cut reward/risk from 1.50 to 0.76. | Confirmed | PROJECT EVIDENCE (`ExecutionIntelligenceService.java:1150-1168`) |
| F-26 | Fees and tick size are not modelled in entry quality or reward/risk | Confirmed in code | PROJECT EVIDENCE; fee rate is EXTERNAL RESEARCH (unverified) |
| F-28 | A 1m WATCH exactly at the support threshold of 60 carried the order in 78 and 91 | Confirmed | PROJECT EVIDENCE (`:73-74, :2214-2219`) |
| F-29 | 104 chased a spike: RVOL 15.25, 2.91 ATR above SMA20, 87% up the 30-minute range | Holds | PROJECT EVIDENCE (I6-003) |
| F-27 / F-31 | Counter-trend first-bounce entries against a standing 1h SELL, with the 1h SELLs coming from RANGE_MEAN_REVERSION | Folded into RC-1 | AGENT INFERENCE |
| F-19 | HTF exits wait for the next price event. For 91 that was 24.9 s and cost 1 tick. | Confirmed; bias unproven | PROJECT EVIDENCE |
| F-30 | **Outside the frozen set.** UNI (wallet position 109) had a stop of 7.628665. Binance shows lows of 7.624 and 7.628 at 18:00–18:01 KSA, during the 17:59:05–18:02:25 KSA price-event outage. The stop executed only at 18:15:47 KSA. This is a real occurrence of the `AGENTS.md` release gate on protecting open positions during application replacement. | **Confirmed** | PROJECT EVIDENCE plus EXTERNAL RESEARCH (Binance klines) |
| F-30 (AERO) | The AERO stop breach I reported in iteration 6 | **Retracted.** I7-002 shows 0.908416362414 came from the TREND_ADD at 18:10:07 KSA. The stop before that was 0.903046, and it was never breached. | PROJECT EVIDENCE |
| F-34 | When an add ratchets the stop or target (`WalletAutoExecutionService.java:151-157`), no `position_management_event` is written. I7-001 found 0 events for UNI 109 and AERO 110, although I7-002 shows their stops changed (7.62568→7.628665 and 0.903046→0.908416). | **Confirmed** observability defect. It caused no loss. | PROJECT EVIDENCE |
| F-17 | Each exit holds the WebSocket thread and wallet locks for about 1.3 s, delaying protection for other symbols | Confirmed, latent; no effect on the frozen trades | PROJECT EVIDENCE |
| F-16 | Clock step of about −2.8 s at about 02:43 KSA on 10-11, coinciding with an ingestion outage. No `FUTURE_TIMESTAMP` rejections. | Latent risk | PROJECT EVIDENCE; restart cause is AGENT INFERENCE |
| F-32 | The entry-quality score doesn't track outcomes. Band 70–84 had 14 losses out of 16. | **Hypothesis only.** Likely confounded by RC-1 and F-35; n = 72, one day. | PROJECT EVIDENCE plus AGENT INFERENCE |
| F-33 | FIX-154 is live: V107/V109 are applied and the code is in Production. `AGENTS.md`, `claude-docs/ARCHITECTURE.md:19` and `TRADING_RULES.md:124` call it pending, and its code differs from the `AGENTS.md` design (hard veto without 5m confirmation; every SELL treated as soft). | Contradiction reported for dual-agent handling; not edited | PROJECT EVIDENCE |

**Effect of I7-003:** frozen trades 78, 82, 91, 96 and 104 had `last_scale_in_at=NULL`, so the stop at entry stayed the active stop for the whole hold. Only 101 had an add (02:16:04 KSA). F-34 therefore doesn't change any frozen verdict.

---

## DATABASE EVIDENCE (PROJECT EVIDENCE)

| Query | What it established |
|---|---|
| DBQ-FTS-001…005 | Schema, frozen selection, and the six rows with PnL and close reasons (FTS-005) |
| I1-001/002, I1-005 | Entry signals; ATR, stop and target geometry |
| I2-001…010 | Wallet fills, price events, audits (1106, 1318, 1558, 1560), opportunities, missing boundary signals |
| I3-001/002 | Wrapper session is +180 min from UTC; epoch proof of the time basis |
| I3-003/004 | Clock-skew step; no FUTURE/STALE rejections |
| I3-005 | Cross-symbol stall around exits |
| I3-007…010 | Footprint of the boundary outage |
| I3-011/012 | HTF_SELL_CONFIRMED prevalence; `new_1h_during_hold=0` for 78, 91, 96, 104 |
| I4-001 | Last migration was V109 (10-10 01:52:45 KSA); no migration at either outage |
| I4-003/004 | F-21 orphan candles (two episodes); 1h 17:00 KSA closed silently for 19/21 symbols |
| I4-005/006 | Positions open during the outage (UNI 80, AERO 81); outage at 02:39:19–02:43:55 KSA |
| I4-007 | Outcome only: what price did after each exit |
| I4-008 | RC-1 prevalence: 5 SELL-context entries, 4 losses |
| I5-002 | 5m triggers moderate (confidence 60–68); 1h SELLs from RANGE_MEAN_REVERSION |
| I5-003 | Entry-quality and reward/risk outcome bands (F-32) |
| I5-004 | Opportunity anchor and best price (F-23 arithmetic) |
| I5-005 | UNI BACKFILL lows below its stop |
| I5-001 | No result file; superseded by I6-003 |
| I6-001/002 | Wallet stops and price-event minutes; the gap is confirmed |
| I6-003 | 30-minute pre-entry ranges and tick widths |
| I6-004 | Empty result file; superseded by I7-001 |
| **I7-001** | Management-event counts: 0 for wallet positions 107, 109, 110, 111, 120, 125, 133; 1 (`TAKE_PROFIT_EXTENDED`) for 130 (trade 101). Resolves U-22. |
| **I7-002** | UNI add signal 115307 stop 7.628665; AERO TREND_ADD signal 115858 stop 0.908416362414. Confirms the SC-3 retraction and resolves U-20. |
| **I7-003** | Final wallet state of the frozen trades: no adds except 101; 101 ended in `TP_EXTENSION_REBASE`, lock price 0.954694 |

## LOG EVIDENCE (PROJECT EVIDENCE, limited)

- **No study-window logs were read.** The trade windows are in rotated `logs/crypto-ai-trader.2026-10-10.*.log.gz` and `2026-10-11.0-4.log.gz` files, which my tools can't decompress (U-10, open). The missing lines are:
  - `[FIX-118]` lines for 101;
  - restart lines around 17:58–18:03 KSA on 10-10 and 02:39–02:44 KSA on 10-11;
  - the FIX-105 entry-quality component lines.
- **The live `logs/crypto-ai-trader.log` covers only 10-11 06:35–07:19 UTC, after the window.** It shows:
  - `[FIX-153][CLOCK_SKEW]` exchange-minus-JVM of 3106–3159 ms (e.g. line 86);
  - `[FIX-125][TX_COMPLETED] holdMs=1308/1316` on `WebSocketClient-SecureIO-1` (lines 15244–15248, 20058–20062);
  - a negative `[FIX-153][SIGNAL_SAVED] liveCloseToGenerationMs` of −1439 (line 20040), meaning the latency metric uses the uncorrected clock.
- **The restart is AGENT INFERENCE.** It rests on the DB footprint and the code path. No log line confirms it.

## CODE EVIDENCE (PROJECT EVIDENCE; working tree, which may differ from the Production JAR, U-23)

- **Entry HTF assessment:** `HigherTimeframeBuyAssessmentService.java:33-34,46-47` (re-verified this iteration).
- **HTF exit:** `PositionExitPolicy.java:14` (the "fresh 1h SELL" Javadoc), `:29` (`HTF_SELL_CONFIRMED`), `:42` (`SELL_CONFIRMED`) (re-verified); `LivePositionProtectionService.java:95-97,100-133,174,191-198,235-238`.
- **Entry quality and gates:** `ExecutionIntelligenceService.java:73-74,229,236,397-457,991-1003,1150-1180,1756-1764,2214-2219`; `TradeExecutionValidationService.java:93-111,413-433`.
- **Geometry:** `AtrRiskService.java:34-45`; `application.yml:143`.
- **Profit lock (FIX-118):** `DynamicProfitLockService.java:107-110,164-166,238-266`.
- **Ratchet on adds without an audit row:** `WalletAutoExecutionService.java:151-157`.
- **F-21 path:**
  - `CandleRepository.java:173-188` (BACKFILL duplicate clause with `closed=GREATEST(...)` at `:188`, re-verified);
  - `MarketDataBootstrapService.java:107-110`;
  - `BinanceWebSocketManager.java:95-96`;
  - `LocalClosedCandleDetector.java:50`;
  - `LocalCandleReconciliationService.java:131,198-207`.
- **Clock and freshness:** `MarketPriceEventService.java:32,39,43`; `ExchangeClockSynchronizer.java:64-70`; `SignalProcessingFreshness.java:28`.
- **Production behaviour matches this code:** the exit codes and the 1h-SELL-at-entry behaviour seen in the DB (I4-008, I5-002) match it, so RC-1 is supported by what Production did, not only by the source.

## EXTERNAL RESEARCH

- **Binance klines API** (UNIUSDT 1m, 10-10 from 14:59 UTC): lows of 7.624 and 7.628 confirm F-30 and the time basis.
- **Binance WebSocket stream docs:** kline updates are pushed about every 2 s.
- **Spring Boot startup order:** `ApplicationRunner`s finish before `ApplicationReadyEvent` is published, which supports F-21's premise that the WebSocket starts only after the startup import.
- **Binance spot fee:** 0.075–0.10% taker per side. Sources conflict, so this is **unverified** and used only as a sensitivity range.
- **Multi-timeframe practice:** practitioner sources treat the higher timeframe as an entry gate. No peer-reviewed evidence either way.

## Unknowns (open at freeze)

- **U-10:** the rotated `.gz` logs for the study window are unread.
- **U-17:** the exact entry-quality components behind F-23.
- **U-18:** whether `QUALITY_DECAY` changes an opportunity's status and should block a wake-up (91).
- **U-21:** whether the live FIX-154 code is an approved revision of the design.
- **U-23:** whether the Production JAR matches the working tree.
- **Replay:** not run. RC-1, RC-3, F-23–F-29 and F-35 are deterministic and should reproduce. F-21, F-30, F-16 and F-17 are Production-runtime effects. F-30 is also a Replay parity gap, because Replay sees complete candles.
- **Resolved this iteration:** U-20 (I7-002) and U-22 (I7-001).

## Candidate corrections (CANDIDATES ONLY; NO CHANGE authorised; each needs the specification and approval gates)

1. **RC-1:**
   - Option (a): make the HTF exit require a 1h SELL generated *after* the position opened, as the Javadoc promises.
   - Option (b): treat a soft-conflict entry consistently at exit.
   - Regression checks: Replay 78, 91, 96 and 104, and the I4-008 cohort.
2. **F-21:** the startup import's duplicate-key path must not finalise RUNTIME `closed=0` rows, or must route them through recoverable admission with a terminal outcome.
   - Regression check: a restart at an hour boundary produces 21/21 outcomes.
3. **F-30:** after an outage, check backfilled lows and highs against open stops and targets.
   - This sits inside the `AGENTS.md` protection-coverage release gate.
4. **F-24 / F-35 / F-26:** a minimum reward/risk at the fill price, a minimum stop/target distance in ticks, and fee-aware break-even.
   - Validate on another window first, because F-32 shows a reward/risk floor alone wouldn't have cut losses.
5. **F-23:** review whether the clamp should apply when reward/risk < 1.
6. **RC-3:** review the FIX-118 rebase give-back band.
7. **F-34:** write a `position_management_event` row for stop and target ratchets on adds.
8. **F-33:** reconcile the FIX-154 documentation through the dual-agent knowledge process.

## Labels

- **PROJECT EVIDENCE:** the DB queries, log lines and code references listed above.
- **EXTERNAL RESEARCH:** Binance klines and WebSocket docs, Spring startup order, fee rates (unverified), multi-timeframe practice.
- **AGENT INFERENCE:**
  - the per-trade verdicts and ex-ante quality grades;
  - that the F-23 clamp was binding;
  - tick sizes inferred from price spacing;
  - restart as the cause of both outages and the clock step;
  - the F-32 confounding;
  - the Replay reproducibility claims.

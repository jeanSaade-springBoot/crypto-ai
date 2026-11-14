# CLAUDE_FAILED_TRADE_STUDY.md: FTS WINDOW2, iteration 8 (frozen final)

All six frozen losses were rule-following. Only 143, and possibly 162, may involve a code defect, and that is still unproven without a Replay run. The bigger finding is outside the frozen scope: stop-loss exits caused about 92% of losses in USDT, and the selection filter cannot see them.

Nothing was changed: no code, config, DB writes, git or deploy. No plan file was written because this session has no Write or ExitPlanMode tool, so this output is the whole deliverable. No new database requests are made this iteration.

---

## 0. SUMMARY FIELDS

| Field | Value |
|---|---|
| Agent / track | CLAUDE, daily failed-trade study, run `fts-WINDOW2-20261020-180002` |
| Iteration | 8 of 8: final classification (self-review only) |
| Study window | WINDOW2 |
| Snapshot | 2026-10-20 15:00:04 UTC = 2026-10-20 18:00:02 KSA (on schedule) |
| Authoritative entity | `crypto_ai.paper_position` |
| Selection | `status='CLOSED' AND closed_at IS NOT NULL AND realized_pnl<0 ORDER BY closed_at DESC LIMIT 6` |
| Frozen IDs (analysed exactly, none substituted) | **182, 172, 162, 158, 143, 137** |
| Environment | Production, paper trading. Direct OFF-mode kline path; every price event is `BINANCE_KLINE_DIRECT_FIX153` |
| Total realized P&L of the six | −0.2731 USDT gross (paper fees = 0) |
| Classification counts | Valid losing trade or design gap: **5** (137, 158, 162, 172, 182). Logic-defect candidate: **1** (143). Replay-parity candidates: **143 (strong), 162 and 158 (code inference)** |
| Confirmed code defects | **0 confirmed.** Two defect candidates still need an approved Replay run (§4) |
| Stale data, data-quality defect, or execution delay that cost money | **None** in any of the six trades |
| **Critical scope finding** | Stop-loss closes are saved as `status='STOPPED'`. The charter filters on `CLOSED`, so **all 75 stop-loss losers are invisible to this study**. They make up about 92% of all realized loss in USDT (§3) |
| Contradictions to report | FIX-154 is deployed, but AGENTS.md and the claude-docs say it is pending (F34). DB session output is in KSA, but the rules say UTC (F0) |
| Agreed-knowledge status | Nothing written. All findings are candidates for the dual-agent process |

**Times.** UTC values come from `UNIX_TIMESTAMP()` epochs, and KSA = UTC + 3h. Raw `TIMESTAMP` output in the investigator session is already displayed in KSA (`time_zone=SYSTEM`, Arab Standard Time; DBQ-CLAUDE-I1-001). Every time below is labelled.

**Evidence labels.** **[PE]** = PROJECT EVIDENCE, **[ER]** = EXTERNAL RESEARCH, **[AI]** = AGENT INFERENCE.

---

## 1. MANDATORY TABLE: ONE ROW PER FROZEN TRADE

Each entry was judged only on information available at the moment of entry. The outcome is judged separately.

| ID | Symbol | Opened UTC / KSA | Closed UTC / KSA | Entry → exit | P&L (USDT, %) | Close reason | Entry route (code) | Primary cause | Contributing factors | Classification | Replay reproducible? |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **182** | ACEUSDT | 2026-10-12 06:57:06.476 / 09:57:06.476 | 07:05:20.486 / 10:05:20.486 | 0.1884 → 0.1880 | −0.0318, −0.21% | SELL_CONFIRMED | Direct BUY to FIX-11G `BALANCED_NEUTRAL_5M_WATCH_1H`, 15% size [PE] | A 1m bullish flip after 12 bearish 1m signals in the prior 15 min (I7-004). The 5m was a raw STRONG_SELL turned NEUTRAL by the 1h WATCH, and that same 1h WATCH then authorised the entry (F30) [PE] | R:R 0.48 with TP +0.27% after tick rounding. Quality exactly 50, cleared by one tick (F36). TREND_FOLLOWING inside a WEAK_DOWNTREND regime. Filled at the candle high [PE] | **Valid losing trade on current rules; design gap** (F19/F30) | Probably yes. Same-boundary ordering was not material [AI] |
| **172** | SHIBUSDT | 2026-10-12 03:36:05.087 / 06:36:05.087 | 04:15:45.484 / 07:15:45.484 | 0.00000549 → 0.00000548 | −0.0455, −0.18% | HTF_SELL_CONFIRMED | Direct BUY, `BALANCED_STRONG` (5m BUY, 1h NEUTRAL), 25% [PE] | Tick granularity: TP and SL were each 3 ticks (±0.55%) inside a 2-tick 30-minute range. Bought at 100% of that range [PE] | R:R 0.74. Exit came about 15 min after the 1h SELL, once the 5m turned NEUTRAL (F9) [PE] | **Valid losing trade**: the symbol does not suit this target geometry | Yes [AI] |
| **162** | DOGEUSDT | 2026-10-12 01:02:06.415 / 04:02:06.415 | 01:26:04.116 / 04:26:04.116 | 0.08705 → 0.08675 | −0.0517, −0.34% | STRONG_SELL (signal path, `validateSell`) | `accumulatedDecision`: `OPPORTUNITY_CONFIRMED`, BALANCED_EARLY + LATE_ENTRY 50, 15% [PE] | A LATE entry at 90% of the 30-minute range with R:R 0.44 and TP +0.22%, close to fee cost [PE] | Opportunity age was 1259.976 s. `toMinutes()` truncated it to 20, avoiding the age penalty by 24 ms (F36/F47). One adjacent BUY (#167646, 60 s earlier) bypassed FIX-119 (F40). FIX-041 blocks this exact combination only on the direct route (F31) [PE] | **Valid losing trade on Production rules; Replay-parity candidate** (RC3′) | **Probably not**: Replay ages by candle close (00:41 → 01:02 = 21 min), so the score would be 45 → CHASE → blocked [AI, not executed] |
| **158** | XRPUSDT | 2026-10-11 23:35:04.811 / 2026-10-12 02:35:04.811 | 2026-10-12 00:30:06.638 / 03:30:06.638 | 1.4108 → 1.4090 | −0.0319, −0.13% | SELL_CONFIRMED | `evaluateConfirmedSetupWakeup`: `FRESH_5M_CONFIRMATION`, using the 1m WATCH #165412 plan, 25% [PE] | TP +0.17% is below round-trip fees (0.20%, or 0.15% with BNB) inside a 0.25% range. A TP hit would still have lost money after fees [PE + ER] | R:R 0.33. Opportunity was 53 minutes old, 7 minutes from a block (F36). The wakeup route loses 46% of the time (I4-003, CLOSED only) [PE] | **Valid losing trade; design gap** (no fee or R:R gate, F20) | Uncertain: the wakeup sees a 5m signal only 21 ms newer, and Replay's 45 s limit may break the lookup (F22) [AI] |
| **143** | EGLDUSDT | 2026-10-11 19:40:04.858 / 22:40:04.858 | 19:43:36.229 / 22:43:36.229 | 4.033 → 4.027 | −0.0372, −0.15% | PROFIT_LOCK_HARD_EXIT | `setupTimeframeAtrAuthoritySignal`: `REDUCED_POSITION_ALLOWED`, 25%. This route bypasses `validateBuy` and FIX-154 (no audit row) [PE] | **Entry input:** the newer 5m #159407 (WATCH, immediate entry 0) was excluded because it was generated 2 ms after the 1m. The superseded 5m BUY #159285 (100%) became the authority (F1). **Outcome:** price hit the original TP 4.0647 twice (4.067 at 19:40:41 UTC / 22:40:41 KSA), the TP was extended twice, then the hard floor 4.0350165 fired on an 8 s Binance gap, 4.100 → 4.027 (F21) [PE] | Live management read 5m #159407 with no as-of bound, while entry used #159285: two different 5m signals within one position. Entry was a re-entry 22 s after position 142's win. The hard-floor exit was about 0.5% better than the SL would have been (low 3.994, 10 s later) [PE] | **Logic-defect candidate** (entry input plus Replay parity, RC3′). The extension and the exit followed the rules (RC4 policy) | **Probably not**: Replay orders 1h → 5m → 1m at the same close, so the 5m WATCH is visible and the result is `ATR_ENTRY_BLOCKED` [AI, not executed] |
| **137** | EGLDUSDT | 2026-10-11 17:55:04.510 / 20:55:04.510 | 18:00:12.122 / 21:00:12.122 | 3.999 → 3.987 | −0.0750, −0.30% | SELL_CONFIRMED | Direct BUY to FIX-11G `BALANCED_NEUTRAL_5M_WATCH_1H`, 25% [PE] | A 1m flip (WATCH, then STRONG_BUY 91) after 12 bearish 1m signals in the prior 15 min (I7-004). Bought 0.15% under a 30-minute high that had already been rejected (−12) [PE] | R:R 0.64. Quality 58 cleared FIX-11G's ≥50 bar, where the stronger-context exception needs ≥70 (F29). RANGE_MEAN_REVERSION bought at 57% of the range, not near its low [PE] | **Valid losing trade; design gap** (F29) | Probably yes [AI] |

**Exits.** All six exits followed the rules as coded, and none touched its stop loss [PE]. The six-trade SL statement is true here but only because of how trades were selected (§3).

---

## 2. FINAL ROOT-CAUSE RANKING (applies the iteration-7 results)

| Rank | Cause | Final status | Frozen trades | Evidence |
|---|---|---|---|---|
| **RC0 (new, outside frozen scope)** | Negative expectancy, driven by stop-loss exits. 75 STOPPED positions, all losses, −15.8756 USDT, average −0.60%. That is about 0.10% worse than the 0.5% SL on average, because fills happen at the observed price [PE]. They outweigh the gross profit of every other position combined (CLOSED net +11.70) [PE]. **All-time gross P&L across all 193 closed and stopped positions is −4.17 USDT, before fees** [PE, arithmetic] | Population-proven | None (excluded by the selection) | I7-001, I7-002, FTS-002, `PositionStatus.java:2` |
| **RC1″** | Wins too small for the loss sizes and fees. 31 of 88 gross wins in 14 days were below +0.20%, so they lose money at the 0.10%/side fee; 18 were below +0.15% (I7-005). PROFIT_LOCK_EXIT averages +0.27% against STOP_LOSS −0.60%. **Correction:** "R:R < 1 is the discriminator" (F43) is withdrawn. Once STOPPED rows are included, every R:R and entry-quality bucket is net-negative, and the 1–2 R:R bucket is the worst at −3.43 gross / −12.40 net (I7-003) | Population-proven; reframed | 137, 158, 162, 172, 182 (158's TP < fee) | I7-003, I7-005 |
| **RC3′** | Decision inputs keyed on wall-clock `generatedAt` instead of candle-close time: the as-of authority lookup (143) and the truncated opportunity age (162). Production races at shared candle boundaries; Replay orders deterministically | Mechanism proven in code; **outcome not Replay-executed** | 143, 162, possibly 158 | `EIS:2455-2461, 948-951, 2541`; `RegressionTestWorker:557-560, 584` |
| **RC4 (upgraded)** | TP extension. Closing at the price observed at the first extension would have earned **+7.743 USDT** against an actual **+6.928** across 33 extended positions: **−0.815 USDT net cost**. 22 positions did worse, 11 did better (I7-006, my arithmetic). All four PROFIT_LOCK_HARD_EXIT losses (17, 101, 131, 143) followed an extension, which confirms the F46 inference. Iteration-7 called it "about neutral"; that is withdrawn | Population-supported net negative (gross, same fee count in both cases) | 143 | I7-006, I7-001, `LPPS:100-110, 207-216` |
| RC2 | First 1m bounce after a bearish streak. Final membership: 137 = 12, 182 = 12, **143 = 9** (the F45 inference is confirmed; F37's count for 143 is withdrawn). The ≥10 bucket is 4 losses in 5 against a 14-day baseline of 96 in 185 (52%). n is too small to call it | Contributing factor, not systemic | 137, 182 | I7-004 |
| Design gaps | A neutralised bearish 5m is treated as genuinely neutral, and the 1h WATCH is counted twice (F19/F30). FIX-11G's quality bar is ≥50 against ≥70 for the stronger-context exception (F29). FIX-041 is enforced only on the direct route (F31). Quality is computed from close-price samples (F32). Live exits apply no age bound (F26, latent) | Current rules followed | 182, 137, 162 | §5 |

---

## 3. CRITICAL SCOPE LIMITATION (F42, confirmed by I7)

- **[PE] How stops are saved:** every stop-loss close writes `PositionStatus.STOPPED`, at `LivePositionProtectionService.java:178`, `PaperTradingService.java:183` and `PaperTradingService.java:433`. The enum is `{OPEN, CLOSED, STOPPED, CANCELLED}` (`PositionStatus.java:2`; I re-verified all four this iteration).
- **[PE] I7-002, losses the selection cannot see:** 21 STOPPED losers closed after 137 did. Five of them closed **after 182**, making them more recent than any frozen trade:

| Paper ID | Symbol | Closed (UTC) | Closed (KSA) | P&L (USDT) |
|---|---|---|---|---|
| 181 | PEPE | 2026-10-12 08:33:22 | 11:33:22 | −0.584 |
| 185 | ACE | 2026-10-12 09:22:34 | 12:22:34 | −0.158 |
| 186 | ICP | 2026-10-12 12:19:51 | 15:19:51 | −0.080 |
| 191 | AERO | 2026-10-14 23:20:30 | 2026-10-15 02:20:30 | −0.113 |
| 193 | EDU | 2026-10-15 00:15:19 | 03:15:19 | −0.134 |

- **[PE] Overall:** I7-001 shows the most recent close of any kind is 193, at 2026-10-15 00:15:19 UTC (03:15:19 KSA). No position closed in the 5 days before the snapshot; I did not investigate why.
- **[AI] Position 190:** it is a PROFIT_LOCK_HARD_EXIT closed after 182 with `realized_pnl` that is not negative. I7-001 and I7-005 suggest it is exactly zero, so excluding it is correct.
- **Effect:** the six frozen trades are the small-loss tail, averaging −0.22%. The study cannot see the main source of loss. I did not substitute any trades; the charter should add `STOPPED` to future selections.

---

## 4. CONFIRMED DEFECTS AND MISSING EVIDENCE

**Confirmed code defects against current rules: none.**

**Defect candidates (unconfirmed until Replay is run):**
1. **143 (F1/F22/F48):** the same-boundary 5m signal is invisible on the SETUP_TIMEFRAME_ATR route because of 2 ms of generation order. That data was committed 175 ms before the BUY, so using it would not have been look-ahead. Production and Replay would decide differently.
2. **162 (F47):** opportunity age is measured with wall-clock jitter. Production computed 20 min (truncated), while Replay would compute 21 min, block the trade, and the floor would not rescue it.

**Audit-only inconsistency (F8):** the message says 25% but 15% was executed; the ATR multiplier is applied later. No money was lost.

**Missing evidence:**
- **Logs:** no production log access was ever granted. LOG EVIDENCE below is empty.
- **Replay:** no Replay run exists for the 143, 158 or 162 windows (I4-005), so parity is code inference only.
- **F9:** the 1–3 s lag before the paper close is unexplained.
- **STOPPED positions:** these are not analysed per trade, because they are outside the charter.

---

## 5. CODE EVIDENCE [PE]

Citations are to the local working tree. Runtime evidence (V107/V109 applied, FIX-154 audit rows) shows it matches production at least through V109.

| Area | Location |
|---|---|
| Stop-loss saved as STOPPED | `domain/PositionStatus.java:2`; `LivePositionProtectionService.java:174-181`; `PaperTradingService.java:182-184, 433` |
| SL / TP geometry | SL = 0.995 × price, TP = price + 3 × ATR, so R:R = 6 × ATR% (verified arithmetically for 5 trades, F4). No R:R gate on the routes these trades used: thresholds exist only at `EIS:1539, 1631`, and `revalidateAtExecutionPrice` is at `EIS:1150-1168` |
| Entry-quality score | `ExecutionIntelligenceService.java:865-1030`: rolling reference `:871-892`, opportunity anchor/best `:898-933`, age `:948-951` (`toMinutes()` truncates), rejection `:955-974`, floor `:995-1003`, cutoff 50 `:229`, guard `:1171-1223` |
| As-of authority lookup | `EIS:2455-2461` (`latestSignalAtOrBefore`, by `generatedAt`), `:66`, `:1472-1513`; opportunity `started_at` from `generatedAt` `:2541` |
| Wakeup route | `EIS:397-457`, 45 s limit at `:71` |
| Accumulated route | `EIS:1996-2067`; FIX-119 `:1257-1280`; FIX-041 direct route only `:672-684` |
| FIX-11G | `TradeExecutionValidationService.java:49, 218-284, 388-390` |
| SELL validation | `TradeExecutionValidationService.java:165-190` |
| 5m veto uses the final decision | `HigherTimeframeBuyAssessmentService.java:20-26`; `AnalysisService.java:364-411` |
| Live protection | `LivePositionProtectionService.java:95-110` (no as-of or age bound; TP extension), `:165-167` (TP), `:207-216` (hard floor at entry × 1.0005); `PositionContinuationPolicy.java:60-62` |
| Replay ordering | `RegressionTestWorker.java:557-560, 584, 787-793`; `ExecutionReplayScope.java:106-116` |
| Sizing | `PaperTradingService.java:296-389`; `WalletExecutionSizingPolicy.java:17-37` |

---

## 6. DATABASE EVIDENCE [PE]

All queries were read-only and bounded, and run through the Coordinator. Results are in `db/` and `db/CLAUDE/`.

| Query IDs | What they established |
|---|---|
| DBQ-FTS-001…005 | Frozen selection proof; status counts (CLOSED 118 / STOPPED 75); the six frozen rows |
| I1-001 | Session timezone is KSA display (F0) |
| I1-002 | Entry, exit, SL, TP, R:R and hold for the six trades |
| I1-003 | Wallet trades 344…454 and their messages; `fee_usdt=0` |
| I1-004 | Managed-position state for 143's lock (95.35%) |
| I1-005 | Entry and authority signals: ATR, regime, `data_quality_valid=1` |
| I1-006…010 | Exit context, work linkage, price-event coverage, 210/210 RUNTIME candles, previous position |
| I2-001…007 | Tick paths; TP-extension events 51/52; opportunity codes; route codes |
| I3-001…006 | 143's gap was a Binance silence, not an ingestion fault; the low printed after the exit; clock skew of about 150 ms or less; protection transaction 1.2–3.2 s; no protection incidents |
| I4-001…005 | V107 applied 2026-10-09 21:15:21 UTC / 2026-10-10 00:15:21 KSA; FIX-154 audit rows; raw vs final decisions; route outcomes; no Replay coverage |
| I5-001…004 | 1m series; opportunities 35535 / 36524 / 37621 / 37952 / 39062 / 40290; 30-minute ranges; entry-quality buckets |
| I6-001…004 | Bearish buckets, fee buckets, exit reasons, TP extensions (all CLOSED only, superseded by I7) |
| I7-001 | Status × close reason, all time and 14 days |
| I7-002 | STOPPED losers after 137 |
| I7-003 | R:R and entry-quality buckets including STOPPED: 185 positions, gross −3.51, net −15.52 at 0.10%, net −12.52 at 0.075% |
| I7-004 | Bearish counts for 137, 182 and 143 |
| I7-005 | Wins below the fee |
| I7-006 | TP-extension counterfactual (33 positions) |

---

## 7. LOG EVIDENCE

**None.** Production logs were never reachable from this track; the SSH request went through the Coordinator and was not fulfilled.

The application's own messages persisted in the DB (`wallet_trade` messages, the `position_management_event` text, the `execution_opportunity` explanations) served as a substitute.

These outstanding log lines would independently confirm F18 and F35:
- `FIX-11G transitional BUY authority granted` and `FIX-11G post-guard result` for signalId 156723 and 176710
- `[FIX-118][PRODUCTION][TP_EXTENSION] positionId=172`

The window is 2026-10-11 17:50 to 2026-10-12 07:10 UTC (20:50 to 10:10 KSA).

---

## 8. EXTERNAL RESEARCH [ER]

- Binance spot fees: Regular tier 0.100% maker and taker, or 0.075% with the BNB discount, so a round trip costs 0.20% or 0.15%. Source: https://www.binance.com/en/fee/schedule (fetched in iteration 6).
- Lower-reliability corroboration: binance.us support, finhay.com.vn, coinspot.io.

---

## 9. SUGGESTED CORRECTION CANDIDATES (analysis only; none authorised)

These are candidates for the Coordinator's comparison step. Each would need a FINAL_CHANGE_SPECIFICATION, dual-agent approval and user approval. **NO CHANGE** is a legitimate outcome for every item.

| ID | Candidate | Targets | Regression and validation checks |
|---|---|---|---|
| CAND-1 | Key the authority as-of lookup and the opportunity age on candle-close time, not wall-clock `generatedAt` | RC3′ (143, 162) | Replay of 2026-10-11 19:35–19:45 UTC (EGLD), 23:30–23:40 UTC (XRP) and 2026-10-12 00:40–01:05 UTC (DOGE). Production↔Replay parity of the decisions. A same-boundary ordering test with 1m before 5m and with 5m before 1m |
| CAND-2 | An expectancy view that includes STOPPED rows, plus a fee-aware minimum target before entry. Make paper fees non-zero, or add a reporting overlay | RC0, RC1″ (158 and others) | Re-run I7-003 and I7-005 on Replay; confirm no look-ahead; measure how many trades the gate blocks and the net P&L of those blocked |
| CAND-3 | Review the TP-extension policy: a protected exit at or near the original TP once it is reached | RC4 (143) | Replay the 33-position I7-006 set; confirm no regression for the 11 positions where extension helped |
| CAND-4 | Analyse the stop-loss fill shortfall (average −0.60% against the 0.5% SL) | RC0 | Tick-gap analysis on the 75 STOPPED positions (the next study) |
| CAND-5 | Exploratory paths (FIX-11G, confluence) should not treat a downgraded raw bearish 5m as neutral | F19/F30 (182) | **Must stay separate from FIX-154 and must preserve the legacy `5M_BEARISH_VETO` behaviour** (AGENTS.md). Needs its own design |
| CAND-6 | Apply FIX-041's BALANCED_EARLY + LATE rule on the accumulated route as well | F31 (162) | Accumulated-route unit and Replay tests |
| CAND-7 (governance) | Add STOPPED to the charter's failed-trade selection; fix the FIX-154 "pending" documentation; restate the DB timezone rule | §3, F34, F0 | Dual-agent agreement process only |

---

## 10. SELF-REVIEW (iteration 8)

**Final corrections:**
- **F43 / RC1′:** "R:R < 1 is the discriminator" is withdrawn. With STOPPED included, every bucket is net-negative.
- **F46 / RC4:** "about neutral" is withdrawn. The extensions cost −0.815 USDT gross against the first-extension price.
- **F37:** 143's bearish streak was 9, not ≥10.
- **F38:** "no SL hits" is a selection artefact and not population evidence.

**Confirmed:**
- F42: STOPPED rows are excluded from the selection.
- F45: 143 sits in the 5–9 bucket.
- F46: every hard-exit loss followed an extension.

**Uncertainties:**
- The Replay outcomes for 143, 162 and 158 are code inference only.
- No logs were available.
- Counterfactuals use gross P&L with an equal fee count on both sides.
- Population statistics cover 14 days with n ≤ 185.

**Classification frozen:** 143 is a logic-defect candidate. 137, 158, 162, 172 and 182 are valid losing trades under current rules, with the design gaps named above. 162 is additionally a Replay-parity candidate.

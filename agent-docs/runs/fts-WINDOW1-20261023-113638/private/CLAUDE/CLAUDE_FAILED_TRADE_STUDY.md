# CLAUDE_FAILED_TRADE_STUDY.md (FTS WINDOW1, Iteration 8, final and frozen)

**Agent:** CLAUDE · **Run:** `fts-WINDOW1-20261023-113638` · **Iteration:** 8 of 8 (self-review only) · **Status:** FROZEN
**Mode:** Analysis only. I changed no code, config, database rows, git state or deployment, and made no implementation. This document goes to stdout only: no file-write tool is available in this session, so it is not saved under `private/CLAUDE/`.
**Isolation:** I did not read the other agent's private or db folders, its memory, LIVE_TEAM_STATUS.md or LIVE_EVENTS.log.
**Time rule:** Database timestamps are UTC. KSA = UTC+3. Every KSA value below is derived and labelled.

## SUMMARY FIELDS

| Field | Value |
|---|---|
| Study window | WINDOW1 |
| Authoritative entity | `crypto_ai.paper_position` |
| Selection rule | `status='CLOSED' AND closed_at IS NOT NULL AND realized_pnl<0 ORDER BY closed_at DESC LIMIT 6` |
| Frozen trades (none substituted) | 182, 172, 162, 158, 143, 137 |
| Snapshot UTC (from the charter) | 2026-10-25 10:08:57 |
| Snapshot KSA (from the charter) | 2026-10-23 11:36:38. **This is not a valid UTC+3 pair.** |
| Snapshot KSA (derived) | 2026-10-25 13:08:57. `DATABASE_QUERY_LOG.md` shows DBQ-FTS-005 ran at `2026-10-25T13:08:57+03:00`. The charter's KSA value is the run-folder creation time, about 46.5 h earlier. The selection is not affected (DBQ-CLAUDE-I1-002). |
| Total realized P&L of the frozen set | −0.273187 USDT, gross. `fee_usdt=0`, so fees are not counted. |
| Selection bias | The `CLOSED` filter leaves out all 75 `STOPPED` losses: 75 of 101 losing trades, about 74% (DBQ-FTS-002, DBQ-FTS-003). These conclusions cover signal and protection exits only, not stop-loss losses. |
| Frozen-set verdict | 4 valid losing trades (182, 172, 162, 137), 1 valid under the rules but uneconomic (158), and 1 where a logic defect decided the entry route (143). |
| Confirmed defects | (D1) 5-minute boundary race in choosing the 5m signal. (D2) Live price ticks carry a fresh event time but may show the previous minute's close. (D3) The production fill reference uses the raw host clock while ingestion uses the corrected clock; Replay uses `generated_at` instead. (D4) The "consumed" rule for evidence after an exit is not enforced. (D5) Signal-path exits store the signal explanation as the exit reason. |
| Main system-wide cause | The strategy has negative expectancy, and fees are not recorded (see S-02 and S-03). |
| Production logs | **Not available.** I had no SSH access, and the only local log starts on 2026-10-19. |
| Changes made | **NO CHANGE.** |
| Documentation updates | None. This was analysis only, and writing was disabled. Candidate knowledge needs dual-agent agreement first. |

## MANDATORY TABLE: one row per frozen trade

All prices are in USDT. UTC is followed by KSA (= UTC+3).

| Trade | Symbol | Opened UTC / KSA | Closed UTC / KSA | Entry → Exit | Realized P&L | close_reason | Entry route | Entry grade at decision time | Main cause | Defect involvement | Replay | Classification |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **182** | ACEUSDT | 2026-10-12 09:57:06.476 / 12:57:06.476 | 2026-10-12 10:05:20.486 / 13:05:20.486 | 0.1884 → 0.1880 | −0.031847 | SELL_CONFIRMED | IMMEDIATE_VALIDATION / BALANCED_NEUTRAL_5M_WATCH_1H | POOR: entry quality 50, exactly the minimum of 50; reward-to-risk 0.48; trend-following in a WEAK_DOWNTREND; 2.15 ATR overextended | Weak setup; the price never moved in its favour (best move 0.00%) | D2 **confirmed for this fill**: it used tick 1972917, 0.1884 = the 09:56 close, while the final 09:57 range was 0.1881–0.1882. About 2 of the 4 lost price steps. | Exit is reproducible. Entry-price parity is **not proven**: the production tick (09:57:02.437) is after `generated_at` (09:57:01.850), so Replay cannot pick it (D3). | VALID LOSING TRADE, with a contributing price-meaning defect (D2) |
| **172** | SHIBUSDT | 2026-10-12 06:36:05.087 / 09:36:05.087 | 2026-10-12 07:15:45.484 / 10:15:45.484 | 5.49e-6 → 5.48e-6 | −0.045537 | HTF_SELL_CONFIRMED | IMMEDIATE_VALIDATION / BALANCED_STRONG | MARGINAL: the signals agreed, but ATR (6.8e-9) was smaller than one price step (1e-8) | The plan could not be carried out with that price step: the target was 3 steps away (+0.55%) and the loss was 1 step | None. Exit delay of 38.9 s from a 40 s tick gap cost 0. | Reproducible, including exit time (F-I4-07) | VALID LOSING TRADE; design gap (no check that the price step fits the plan) |
| **162** | DOGEUSDT | 2026-10-12 04:02:06.415 / 07:02:06.415 | 2026-10-12 04:26:04.116 / 07:26:04.116 | 0.08705 → 0.08675 | −0.051694 | STRONG_SELL (signal path) | ACCUMULATED_EVIDENCE / OPPORTUNITY_CONFIRMED | POOR: 1m WATCH; breakout bought at the candle top; reward-to-risk 0.44; 2.82 ATR overextended | Chased the breakout, then 1m turned STRONG_SELL | D5 **confirmed**: `exit_reason` holds the full signal explanation ending "Entry allowed" (DBQ-CLAUDE-I7-002, second result set). Exit price = `latest_price` = the tick, so 0 cost. | P&L parity is likely. The signal-exit price matches the tick, so price is not a parity risk. | VALID LOSING TRADE; audit gap only |
| **158** | XRPUSDT | 2026-10-12 02:35:04.811 / 05:35:04.811 | 2026-10-12 03:30:06.638 / 06:30:06.638 | 1.4108 → 1.4090 | −0.031897 | SELL_CONFIRMED | SETUP_CONFIRMATION_WAKEUP / FRESH_5M_CONFIRMATION | POOR: score 64, the lowest of the six; reward-to-risk 0.33; **target +0.164%, below the ~0.15–0.20% round-trip fee** | Uneconomic before fees even counted: a win would still have lost money net of fees | None as defined. No entry gate checks the economics. | P&L matches. Exit time differs: Replay exits at 03:30:02.320, Production at 03:30:04.319. | VALID UNDER THE RULES; design gap (no economic entry gate) |
| **143** | EGLDUSDT | 2026-10-11 22:40:04.858 / 2026-10-12 01:40:04.858 | 2026-10-11 22:43:36.229 / 2026-10-12 01:43:36.229 | 4.033 → 4.027 | −0.037193 | PROFIT_LOCK_HARD_EXIT | SETUP_TIMEFRAME_ATR / REDUCED_POSITION_ALLOWED | POOR: 1m said WAIT_FOR_RETRACEMENT at 0%; RSI 84; 4.35 ATR overextended; re-entered 22 s after the previous profit-lock exit, 0.93% higher | Route admitted only because the 5m signal was one candle stale (D1). Price then reached +1.69%, past the original target; the target was extended twice; an 8 s tick gap filled the exit at 4.027, below the 4.0350 hard floor. | **D1 decided the route**: 5m WATCH 159407 was generated 2 ms after the 1m signal, so the older 5m BUY 159285 was used. D4 present, not decisive. The tick-gap cause is unknown (U-08). | The decision is reproducible: Replay applies the same `generated_at` rule, so it reproduces the stale choice but **cannot detect the race**. Fill-tick parity is not verified. | LOGIC DEFECT (D1) plus an exit-design outcome; U-08 open |
| **137** | EGLDUSDT | 2026-10-11 20:55:04.510 / 23:55:04.510 | 2026-10-11 21:00:12.122 / 2026-10-12 00:00:12.122 | 3.999 → 3.987 | −0.075019 | SELL_CONFIRMED | IMMEDIATE_VALIDATION / BALANCED_NEUTRAL_5M_WATCH_1H | MARGINAL–POOR: RANGE_MEAN_REVERSION buy with target 4.0119 above the range high of 4.005 (the thesis contradicts itself); reward-to-risk 0.64 | Mean-reversion trade that needed a breakout; 1m STRONG_SELL plus 5m SELL came 5 min later | D3 **confirmed**: host clock lag made the newer tick 1759598 look future-dated, so the fill used tick 1759564. Both were 3.999, so 0 cost. | Reproducible: Replay picks the same tick (20:55:00.032 ≤ `generated_at` 20:55:01.576). | VALID LOSING TRADE |

**Entry assessment rule.** Grades use only what was known at decision time. Outcomes were assessed separately. Best move after entry: 182 0.00%, 172 +0.18%, 162 +0.07%, 158 +0.06%, 143 +1.69%, 137 +0.15%. No trade reached its stop. [PROJECT EVIDENCE: I1-009, F-I2-05]

## SYSTEM-WIDE FINDINGS

**S-01: Data freshness was valid for all six entries.** [PROJECT EVIDENCE: I1-004B, I1-012]
- All entry signals were LIVE with `data_quality_valid=1`. Candles were RUNTIME and closed.
- 5m context was 1–122 s old and 1h context 122–3421 s old.
- Every fill tick was within the 15 s limit.
- Across the system: 248 BUY fills, average price age 1.893 s, maximum 14.008 s; 8 were over 5 s (I7-003).
- The one exception is 143. Its 5m context passed the 20-minute age rule while being one candle behind the market.

**S-02: The strategy has negative expectancy.** [PROJECT EVIDENCE: I7-002. Arithmetic: AGENT INFERENCE]
- Trades with targets above 0.30% (155 trades):
  - 72 stop-loss exits: −15.591 in total, average −1.156 R (the fills slip past the stop).
  - 64 winners: PROFIT_LOCK_EXIT 32 at 0.600 R, PROFIT_LOCK 19 at 1.157 R, TAKE_PROFIT 13 at 2.223 R. Weighted average ≈ 1.10 R.
  - Planned reward-to-risk for the stopped trades averaged 1.739.
- Net ≈ −20 R over 155 trades, about −0.13 R per trade, with a 41% win rate.
- Mechanism: profit-lock cuts winners at about 0.6 R while losers take the full stop or more.
- PROFIT_LOCK_HARD_EXIT appears 5 times in the over-0.30% group, all losses (−0.140). So 143's pattern recurs.
- Trend-following in a weak uptrend carries about 87% of the gross loss (I5-003).

**S-03: Fees are not recorded.** [PROJECT EVIDENCE: I5-004, `fee_usdt=0`. Fee rate: EXTERNAL RESEARCH]
- Estimated round-trip fees are 12.39 USDT against a reported gross result of −4.171 USDT.
- 38 trades had targets at or below 0.30%: gross +0.161, but about −1.57 net of fees.
- The live fee tier is unverified (U-12).

**S-04 (D1): The 5-minute boundary race is systemic.** [PROJECT EVIDENCE: I5-002, I6-002]
- On 11–12 Oct, in 8,998 of 10,467 boundary pairs (86.0%), the 5m signal for the just-closed candle was generated *after* the 1m signal.
- Boundary entries are 39% of trades but 86% of the loss: −3.576 over 75 trades, against −0.594 over 118 non-boundary trades.
- Leaving out SETUP_CONFIRMATION_WAKEUP (which is boundary-only by design), boundary trades lose −0.079 per trade and non-boundary trades −0.005 per trade.
- **The race is not proven to cause the losses.** The significance query DBQ-CLAUDE-I7-001 has no result file, so U-25 stays open.

**S-05 (D2): Live ticks may carry the previous minute's close with a fresh event time.** [PROJECT EVIDENCE: I6-001, I7-004]
- In the first 3 seconds of a minute, 3,729 of 25,001 ticks were outside the final 1m candle range. Every one of them equals the previous minute's close.
- **Entries:** 19 BUY fills since 2026-10-06 were outside the candle range. 11 were worse for us and 8 better, so there is no proven net bias.
- **Protection (new in I8):** 7 PROFIT_LOCK exits filled at the previous close, outside the final range.
  - 5 were below the range, meaning a possibly premature trigger and a worse fill: positions 11, 47, 58, 100 and 128.
  - 2 were above the range: positions 77 and 183.
  - All 7 were still wins. No STOP_LOSS or TAKE_PROFIT exits showed this.
- **Coverage limits:** the query left out the PROFIT_LOCK_EXIT and PROFIT_LOCK_HARD_EXIT variants, and it located the candle by the paper `closed_at`, which is 1–3 s after the wallet SELL.
- `market_price_event` has no trade-count column (I7-004 schema). `candle.number_of_trades` does exist.

**S-06 (D3): Fill clock and Replay parity.** [PROJECT EVIDENCE: code, I6-004, I7-003]
- Production fills resolve against `Instant.now()`, the raw host clock (`PaperTradingService.java:238,343`). Ingestion uses the corrected clock.
- On 2026-10-11 the host ran behind Binance: written-minus-Binance time had a minimum of −3,083 ms and an average of −1,049 ms. On 2026-10-14 the minimum was −4,346 ms.
- **U-26 resolved:** Replay has its own entry path and resolves against `signal.getGeneratedAt()` (`ShadowProductionReplayService.java:297-298, 332-333`). Production's reference time is later.
- So Production and Replay can choose different fill ticks (182 is an example). I found no systematic cost: the differences go both ways.

**S-07 (D4): The evidence-boundary rule is not enforced.** [PROJECT EVIDENCE: I4-002, I2-005, code]
- The rule says pre-exit evidence is consumed. But 143's new opportunity (36524) carried `evidence_count=7`, and 6 of those 7 signals came from before the previous exit.
- Re-entries within 5 minutes of a non-stop exit: 31 trades, 45% wins, −1.07 net.

**S-08: Operational context.** [PROJECT EVIDENCE: I3-005, I3-004]
- The system ran only intermittently after 2026-10-13.
- The last position opened at 2026-10-15 03:08:06 UTC (06:08:06 KSA). No opens after that, up to the snapshot.
- Every entry and exit spends 1.2–3.2 s of portfolio work inside the same transaction. This is a latency and lock-hold cost, not an atomicity defect.

## DATABASE EVIDENCE [PROJECT EVIDENCE]

**Coordinator proof queries**

| Query | Content |
|---|---|
| DBQ-FTS-001 | `paper_position` schema |
| DBQ-FTS-002 | 75 STOPPED; 118 CLOSED, 26 of them losses |
| DBQ-FTS-003 | 101 losing wallet SELLs |
| DBQ-FTS-004 | The Replay table `wallet_position_test` is separate |
| DBQ-FTS-005 | The six frozen rows. Signals: 182→176710, 172→171589, 162→167668, 158→165412, 143→159408, 137→156723. |

**Claude queries actually executed (result files exist)**

| Query | Content |
|---|---|
| I1-001…012, I1-004B | Exit audits 181/172/163/159/141/137; wallet trades 451/454, 426/429, 403/407, 392/401, 358/359, 344/346 and 357; managed positions 211/201/191/187/172/166; entry signals; candles; higher-timeframe audit rows 1938/2199/2284/2425 |
| I2-001…006 | STOPPED population; exit context; 143's target extensions (4.033 → 4.0805 → 4.1043); 143's tick gap (22:43:27.038 at 4.100 → 22:43:35.021 at 4.027 UTC, i.e. 01:43:27 → 01:43:35 KSA on 12 Oct); opportunity rows; R:R buckets |
| I3-001…006 | Exit-window ticks; signal commit times; tick gaps; snapshots 452/455/347/360; activity by day; stop slippage (average 0.075%, maximum 0.559%) |
| I4-001…005 | Candle indexes; 143's evidence window; re-entries; 162's exit price = tick |
| I5-001…004 | Fill-tick matches; race counts; outcomes by route and feature; outcomes by target size |
| I6-001…004 | Out-of-range ticks; boundary outcomes; wide-target exits (truncated, see I7); later ticks before fill |
| I7-002 | Outcomes by structured `close_reason` with R-multiples; positions whose exit reason contains "Entry allowed" (2, 3, 27, 32, 49, 62, **162**) |
| I7-003 | Daily host-versus-Binance lag; BUY fill-age statistics |
| I7-004 | `market_price_event` schema; PROFIT_LOCK exits outside the candle range (11, 47, 58, 77, 100, 128, 183) |

**Requested but no result:** DBQ-CLAUDE-I7-001 (boundary significance). There is no result file. I have not assumed any outcome for it.

**No new database requests in Iteration 8.** This was the final self-review.

## LOG EVIDENCE [PROJECT EVIDENCE: absence]

- **No production logs were searched.** I had no SSH access in any iteration.
- The local `logs/crypto-ai-trader.log` starts at 2026-10-19T14:32 on a local development process and has nothing from 11–12 Oct.
- **Missing evidence that would settle U-08:** `[FIX-153][PRICE_NOT_CANONICAL]` or `KLINE_PHASE_FAILED` lines for EGLDUSDT between 2026-10-11 22:43:27 and 22:43:35 UTC (2026-10-12 01:43:27–01:43:35 KSA).
- All timing evidence comes from database timestamps plus code.

## CODE EVIDENCE [PROJECT EVIDENCE]

| Area | Reference |
|---|---|
| Fill-price authority (15 s maximum age; Replay versus Production branches) | `ExecutionPriceAuthorityService.java:25-49` |
| Production fill reference uses `Instant.now()` | `PaperTradingService.java:237-238, 342-343` |
| Replay fill reference uses `signal.getGeneratedAt()` | `ShadowProductionReplayService.java:297-298, 332-333` |
| Ingestion uses the corrected clock; `maxAge` 15 s, `futureSkew` 2 s | `MarketPriceEventService.java:31-46, 52-57, 133-146` |
| Tick price = kline `c`, tick time = event time `E`, one row per message | `BinanceKlineService.java:91-111` |
| 5m visibility by `generatedAt` (D1); 20-minute age limit | `ExecutionIntelligenceService.java:66, 1472-1514, 2455-2462, 2606-2609, 2634-2636` |
| Evidence window and boundary (D4) | `ExecutionIntelligenceService.java:47, 821-839, 2241-2310`; `StopLossEvidenceService.java:94-103` |
| Entry-quality minimum of 50 | `TradeExecutionValidationService.java:49, 263-297` |
| Stop = max(2×ATR, 0.50%), target = 3×ATR, no minimum R:R gate | `AtrRiskService.java:34-45`; `application.yml:137-144` |
| Signal-path exit price = `signal.latestPrice`; explanation stored as the exit reason (D5) | `PaperTradingService.java:442-507` (`:449`, `:457`, `:505`); `WalletAutoExecutionService.java:211-278` |
| Live exits are tick-driven | `LivePositionProtectionService.java:54-97, 191-243, 391-412`; `PositionExitPolicy.java:20-50` |
| Portfolio work inside the transaction | `WalletAutoExecutionService.java:178-195, 474-562`; `WalletService.java:484-490, 592-607` |
| Replay is tick-driven; signals become visible at `generated_at` | `ShadowProductionReplayService.java:91-159, 380-444` |

## EXTERNAL RESEARCH

- **Binance spot kline stream:** updates every 2000 ms. The docs do not describe what an update looks like before the first trade of a minute ([Binance WebSocket Streams](https://developers.binance.com/docs/binance-spot-api-docs/web-socket-streams)). The previous-close pattern in S-05 comes from project data, not from Binance's documentation.
- **Spot fee:** 0.1% maker/taker, or 0.075% when paid in BNB ([Finhay 2026](https://www.finhay.com.vn/en/phi-giao-dich-binance)). A report of cheaper tiers is unverified ([Traders Union](https://tradersunion.com/news/brokers-news/show/2241303-binance-revises-trading-fee-structure/)). Paper fills behave like taker orders, so I estimate the round trip at about 0.15–0.20%.

## UNKNOWNS (final)

| ID | Status |
|---|---|
| U-08 | Open: cause of 143's 8 s EGLD tick gap; needs production logs |
| U-12 | Open: the live fee tier |
| U-14 | Open: why the system ran intermittently or stopped after 13–15 Oct |
| U-18 | Open: exact source of the ~2 s portfolio work (the index is fine) |
| U-25 | Open: statistical significance of the boundary and route differences; I7-001 has no result |
| U-26 | **Resolved:** Replay uses `generated_at` (S-06) |
| U-27 | **Resolved:** 162 is affected; it is an audit-text gap only |
| U-28 | **Partly resolved:** 7 PROFIT_LOCK exits on previous-close ticks; profit-lock variants are not yet covered |
| U-29 | Open: whether another route would have admitted 143 if 159407 had been visible |

## CORRECTION CANDIDATES (candidates only: NO CHANGE) [AGENT INFERENCE]

Each candidate needs an approved `FINAL_CHANGE_SPECIFICATION`, dual-agent approval and user approval before any work.

1. **R2: fee-aware paper accounting plus an economic entry gate.**
   - Reject an entry when the target distance is at or below round-trip cost plus k price steps, or when the price step is at least a set fraction of ATR.
   - Regression checks: 158-like and 172-like cases are rejected; typical trades are unchanged; Replay gives the same verdicts.
2. **R6: strategy expectancy study (a strategy decision, not a code fix).**
   - Covers weak-uptrend trend-following, and profit-lock activation and trail settings against full stop losses (S-02).
   - Run it in Replay with fees included.
3. **R1: choose the 5m signal by candle identity.**
   - Use the latest 5m candle whose close time is at or before the 1m close; if its signal is not ready yet, defer in-process only, with no persisted fence.
   - Replay must use the same rule.
   - Regression checks: the 5m signal arrives 2 ms after the 1m signal; a Replay check on the 143 window; non-boundary minutes unchanged.
4. **R5: mark previous-close ticks as non-tradable for fills and protection.**
   - Use the kline trade count `n` or first-trade id `f` at ingestion.
   - Regression checks: the 182 entry tick and protection-trigger cases like positions 11 and 47.
5. **R3: count only evidence generated after the previous exit.** Regression check: 143's evidence count becomes 1.
6. **R4: one clock for fill references.**
   - Use the exchange-corrected execution clock in Production, and align Replay's reference with it, or record the deliberate difference as a parity exclusion.
   - Measure the delay from price lookup to execution.
7. **R7: store a structured exit rule separately from the signal explanation (D5).** Audit only.
8. **Study process note (not code):** future studies should include `STOPPED` losses, or report them separately.

## SELF-REVIEW (Iteration 8)

- **Kept:** all six frozen trades, with no substitution. Every KSA time is UTC+3, with date rollovers shown. The charter's inconsistent KSA snapshot label is reported, not used.
- **Settled this iteration:** U-26 (code), U-27 (I7-002), and U-28 in part (I7-004). The 162 classification now includes the D5 audit gap. The R5 scope is widened to protection.
- **Not claimed:**
  - That the boundary race causes the loss concentration (I7-001 is missing).
  - Any cause for U-08.
  - Any net bias in entry prices from placeholder ticks.
- **Labels:** each section is marked as PROJECT EVIDENCE, EXTERNAL RESEARCH or AGENT INFERENCE.
- **Documentation check:** this study changes nothing, so no ARCHITECTURE, CHANGELOG or TRADING_RULES update is required. Durable findings are candidates for `AGREED_KNOWLEDGE.md` only after the coordinator comparison and dual-agent agreement.

**NO CHANGE was made to code, configuration, database, services, git or deployment.**

Sources:
- [Binance Spot WebSocket Streams](https://developers.binance.com/docs/binance-spot-api-docs/web-socket-streams)
- [Finhay – Binance trading fees 2026](https://www.finhay.com.vn/en/phi-giao-dich-binance)
- [Traders Union – Binance revises fee structure](https://tradersunion.com/news/brokers-news/show/2241303-binance-revises-trading-fee-structure/)

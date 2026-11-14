# CLAUDE_FAILED_TRADE_STUDY.md: FTS WINDOW2, final (Iteration 8 of 8, frozen)

| Field | Value |
|---|---|
| Agent | CLAUDE (independent track; self-review only, no cross-review) |
| Study window | WINDOW2 |
| Snapshot | 2026-10-17 15:00:03 UTC = 2026-10-17 18:00:02 KSA (on schedule) |
| Authoritative entity | `crypto_ai.paper_position` (Production) |
| Selection | `status=CLOSED AND closed_at IS NOT NULL AND realized_pnl < 0 ORDER BY closed_at DESC LIMIT 6` |
| Frozen IDs | 182, 172, 162, 158, 143, 137. No trade was swapped out. |
| Total frozen loss | −0.273188 USDT gross (paper fee = 0 on all 13 fills) |
| Classification counts | Valid losing trade: 2 (137, 158) · Design property: 1 (143) · Policy-scope gap: 1 (162) · Route inconsistency, causation unproven: 1 (172) · Logic defect, causal under a code counterfactual: 1 (182) |
| Confirmed defects | D1: boundary lookup race and Production/Replay parity divergence. D2: the direct-BUY route trusts only the 5m decision label. |
| Stale data / execution delay / protection gap | Ruled out for all six |
| Replay | Not run. Every parity and counterfactual statement comes from reading the code. |
| Changes made | **NONE.** No code, config, DB writes, git or deploy. Plan mode was active and this session has no ExitPlanMode tool, so this stdout is the deliverable. |
| Isolation | Did not open `private/CODEX/`, `LIVE_TEAM_STATUS.md`, `LIVE_EVENTS.log` or Codex memory. In I7 one Grep over the run folder returned path and annotation lines from the CODEX stderr files. I did not open or use them. |

**Labels:** **[PE]** project evidence · **[ER]** external research · **[AI]** agent inference.

**Time convention [PE]:**
- Columns are stored as UTC in `timestamp(6)` (I2-001). The investigator session is +03:00 (I1-001), so displayed values are KSA.
- This was checked against an embedded value: wallet 344 shows `priceObservedAt=…17:55:00.032Z` in its message and 20:55:00.032 in its column.
- All times below are **KSA (UTC)**.
- Exceptions: `btc_context_evaluated_at` and `btc_signal_generated_at` are `datetime(6)` and display raw UTC. No conclusion here depends on them.

---

## 1. MANDATORY TABLE: one row per frozen ID

Entry time is `paper_position.opened_at`. Exit is the wallet SELL `executed_at`, which is the decision/fill time, with paper `closed_at` (the commit-ordered row write) in brackets.

| ID | Symbol | Entry KSA (UTC) | Exit fill KSA (UTC) [paper closed_at KSA] | Entry → exit | P&L USDT (%) | Size | Entry route / HTF band | Exit reason | Primary classification | Confidence | Main evidence |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 137 | EGLDUSDT | 10-11 20:55:04.510 (17:55:04.510) | 10-11 21:00:08.935 (18:00:08.935) [21:00:12.122] | 3.999 → 3.987 | −0.075019 (−0.30%) | 25% | IMMEDIATE_VALIDATION / BALANCED_NEUTRAL_5M_WATCH_1H | SELL_CONFIRMED (5m SELL 156852) | **VALID LOSING TRADE.** Near-zero edge (R:R 0.64, TP +0.32%), bought just under a twice-rejected high; the exit was correct. | High | I1-002, I1-004 (344/346), I2-002, I5-002, F6-3 EQ 58 reproduced |
| 143 | EGLDUSDT | 10-11 22:40:04.858 (19:40:04.858) | 10-11 22:43:35.046 (19:43:35.046) [22:43:36.229] | 4.033 → 4.027 | −0.037193 (−0.15%) | 25% | SETUP_TIMEFRAME_ATR (5m 159285 authority) | PROFIT_LOCK_HARD_EXIT (floor 4.0350) | **DESIGN PROPERTY** (loss mechanism). Price reached 95.35% of the TP distance and the TP was extended twice; the hard floor then filled at the next tick, 4.027, after an 8 s gap. **The entry exists only because of confirmed defect D1** (a 2 ms boundary race). | High (mechanism); Medium (D1 counterfactual, no Replay) | I1-007, I1-009, I2-004, I3-003, I4-001 (no audit row), I7-002 |
| 158 | XRPUSDT | 10-12 02:35:04.811 (10-11 23:35:04.811) | 10-12 03:30:04.217 (00:30:04.217) [03:30:06.638] | 1.4108 → 1.409 | −0.031897 (−0.13%) | 25% | SETUP_CONFIRMATION_WAKEUP (5m 165415 trigger, 1m 165412 plan) / BALANCED_STRONG | SELL_CONFIRMED (5m SELL 166821) | **VALID LOSING TRADE.** Target +0.17% (R:R 0.33) in a bucket that is break-even gross. The route depended on D1 (21 ms). Using the 1m plan is intended design. | High (classification); Medium (D1 dependency) | I3-004, I5-001, I7-001 (165415 original BUY, 165412 original WATCH) |
| 162 | DOGEUSDT | 10-12 04:02:06.415 (01:02:06.415) | 10-12 04:26:04.134 (01:26:04.134) [04:26:04.116] | 0.08705 → 0.08675 | −0.051694 (−0.34%) | 15% | ACCUMULATED_EVIDENCE / BALANCED_EARLY | SIGNAL_SELL / paper STRONG_SELL (signal 168277) | **POLICY-SCOPE GAP.** A price the system itself rated LATE_ENTRY (EQ 50) was admitted as BALANCED_EARLY on a route that FIX-041 and FIX-119 exclude by design. | High (mechanism); Low (that it is a system-level problem; I7-003) | I1-004 (403/407), I2-002, I4-001 (audit 2199), I7-003 |
| 172 | SHIBUSDT | 10-12 06:36:05.087 (03:36:05.087) | 10-12 07:15:43.492 (04:15:43.492) [07:15:45.484] | 5.49e-6 → 5.48e-6 (−1 tick) | −0.045537 (−0.18%) | 25% | IMMEDIATE_VALIDATION / BALANCED_STRONG ("5m is bullish") | HTF_SELL_CONFIRMED (1h SELL 172201 + 5m NEUTRAL 172588) | **ROUTE INCONSISTENCY CONFIRMED (D2); CAUSATION UNPROVEN.** The 5m BUY 171559 had its own entry blocked (RANGE_ENTRY_LOCATION), yet it still authorised the entry. ATR was below one tick, so the setup had no edge. | High (D2 present); Low (D2 caused the loss) | I1-004 (426/429), I4-001 (audit 2284), I7-001 (171559 `final_entry_allowed=0`) |
| 182 | ACEUSDT | 10-12 09:57:06.476 (06:57:06.476) | 10-12 10:05:18.504 (07:05:18.504) [10:05:20.486] | 0.1884 → 0.1880 | −0.031847 (−0.21%) | 15% | IMMEDIATE_VALIDATION / BALANCED_NEUTRAL_5M_WATCH_1H (FIX-11G) | SELL_CONFIRMED (5m STRONG_SELL 176934) | **LOGIC DEFECT (D2, design-level route inconsistency).** The MULTI_TIMEFRAME stage relabelled the 5m STRONG_SELL 176655 to NEUTRAL because the 1h was WATCH. The direct route then read only the label, skipped 5M_BEARISH_VETO, and used that same 1h WATCH to grant entry. The trade bought the top; price never ticked above entry. | High (mechanism); Medium-High (causal by code counterfactual, no Replay) | I7-001 decision_path, I4-001 (audit 2425), I5-002, code at `HigherTimeframeBuyAssessmentService.java:20-24,50` |

**Ruled out for all six [PE]:**
- **Stale data:** every input was LIVE and `data_quality_valid=1`, with BTC context 1–9 s old. Fill-price observations were 0.06–1.6 s old.
- **Execution delay or slippage at entry:** fills came 2.9–4.2 s after candle close, at the 1m close price or within 1 tick.
- **Protection gap:** the wallet BUY, managed position and paper open are one transaction under the symbol lock.
- **Wrong stop/TP mechanics, Replay contamination, P&L arithmetic error.**

**None of the six hit the 0.5% stop.** Every loss is a signal exit or a floor exit.

---

## 2. FINAL FINDINGS (supersedes I1–I7 where they differ)

### FF-1 [PE code + DB]: confirmed defect D1, the boundary lookup race and Production/Replay parity divergence

**Production:**
- Higher-timeframe lookups use `generatedAt <= current.generatedAt`, with `generatedAt = Instant.now()` (`AnalysisService.java:150`; `ExecutionIntelligenceService.java:2455-2461`, `:2606-2609`; `TradeExecutionValidationService.java:367-379`).
- On a 5-minute boundary, whether the same-boundary 5m is visible depends on which analysis read the clock first.

**Replay:**
- Replay orders 1h → 5m → 1m with identical boundary times (`RegressionTestWorker.java:557-560`, `:584`, `:787-793`; `ExecutionReplayScope.java:106-120`).
- So Replay always sees the same-boundary 5m.

**Effect on the frozen trades:**
- **143:** the 1m 159408 was generated 2 ms *before* the 5m WATCH 159407. EI therefore used 5m BUY 159285 and SETUP_TIMEFRAME_ATR fired. With 159407 visible, that route returns null (`:1486-1487`).
- **158:** the 1m path saw no 5m BUY. The entry came from processing the 5m 165415 through a wake-up that bound to 1m 165412, which was generated 21 ms earlier.

**Not yet measured:** how often this happens system-wide (I4-003 never returned).

### FF-2 [PE code + DB]: confirmed defect D2, the direct-BUY route trusts only the 5m label

**Code:**
- `HigherTimeframeBuyAssessmentService.assess` uses `decision(fiveMinute)`, i.e. `getDecision()` only (`:20`, `:50`), for `5M_BEARISH_VETO` (`:22-26`).
- Neither `original_decision` nor `final_entry_allowed` of the 5m is consulted.

**The codebase's prevailing convention is the opposite:**
- Other EI routes check the 5m with `strongerDecision(decision, original)` or `isBearish(five.getOriginalDecision())`: `ExecutionIntelligenceService.java:1368`, `:1425`, `:1542`, `:1634`, `:1865`, `:1915`.
- The pressure route says explicitly: "A fresh 5m STRONG_SELL is too hostile even for a probe" (`:1918-1921`).
- The wake-up and SETUP_TIMEFRAME_ATR routes require `five.isFinalEntryAllowed()` (`:296`, `:402`, `:1487`).

**182 (I7-001 resolves U7-1):**
- The decision path for 176655 is: BASE STRONG_SELL → DATA_QUALITY PASS → ATR PASS → RANGE N/A → **MULTI_TIMEFRAME DOWNGRADE → NEUTRAL** ("Bearish signal conflicts with bullish higher-timeframe structure … 1h=WATCH (trend 16/25, regime WEAK_UPTREND)") → BTC, derivatives and order book PASS.
- So the neutralisation was a deliberate context downgrade, not `safeNeutral`.
- **[AI]** The same weak 1h WATCH was counted twice: once to neutralise the bearish 5m, and again (FIX-11G, `TradeExecutionValidationService.java:263-284`) to authorise the BUY.
- **Code counterfactual:** with `strongerDecision` applied in `assess`, the 5m reads STRONG_SELL and `5M_BEARISH_VETO` rejects.
- **Status:** a design-level logic defect (route inconsistency), causal under that counterfactual.
- **Remaining uncertainty:** another route (for example ACCUMULATED_EVIDENCE) might still have entered. Replay is needed to rule that out.
- Under the AGENTS.md FIX-154 notes, any remedy is a design decision that must keep the legacy `5M_BEARISH_VETO`.

**172:**
- 171559 was `original_decision=BUY`, `decision=BUY`, `final_entry_allowed=0`, `primary_blocking_stage=RANGE_ENTRY_LOCATION` ("too high inside the Bollinger range at 59.09%").
- The direct route granted BALANCED_STRONG anyway.
- It is a location veto on the 5m's own entry, not bearish evidence, and the 1h was NEUTRAL, so it is unproven that the entry would have been refused. **Causation unproven.**

**Same blind spot outside the frozen six:** 13 positions system-wide entered on a blocked or neutralised 5m, net about −0.56 USDT, including 181 PEPE (STOPPED, −0.584) one minute before 182 (I4-004). The sample is too small to size an effect.

### FF-3 [PE code + DB]: the 158 wake-up gap had no effect

- `evaluateConfirmedSetupWakeup` does not check `getOriginalDecision()` (contrast `:327`, `:355`).
- I7-001 shows 165415 original = BUY and 165412 original = WATCH, so the gap was inert for 158.
- Taking the plan from the latest 1m signal is intended (FIX-023 comment, `:381-387`). The I6 claim that "158 lost the better geometry" stays **withdrawn as a defect**.

### FF-4 [PE DB I6-001, I7-003, I7-004]: where the system's money is actually lost

- **193 closed positions, gross −4.171 USDT:**
  - CLOSED: 118 positions, +11.705.
  - STOPPED: 75 positions, −15.876.
- **About 75% of the gross loss** (−3.109) is in ATR-based stops with ATR 0.25–0.50%. Only 143 among the frozen six is in that bucket, and it exited via the floor.
- **The bucket holding 158, 162 and 182** (floored stop, ATR < 0.10%) is +0.161 gross: break-even.
- **I7-004:**
  - 62 of the 75 STOPPED positions kept the original signal stop.
  - The average STOPPED exit is 0.075% *below* the stop, which is the cost of filling at the next tick.
  - Only 1 position moved entry by more than 0.3%.
  - So the I6-001 bucket edges hold approximately.
- **By route (I7-003; the sums reconcile: n=193, −4.1707):**

| Route | n | Gross | Frozen trades |
|---|---|---|---|
| IMMEDIATE_VALIDATION | 63 | −2.326 | 137, 172, 182 |
| SETUP_CONFIRMATION_WAKEUP | 41 | −0.905 | 158 |
| SETUP_TIMEFRAME_ATR | 24 | −0.773 | 143 |
| ACCUMULATED_EVIDENCE | 44 | **+0.199** | 162 |

  - **[AI]** The direct route that carries D2 holds the largest share of the loss. Most of it is still unrelated to D2: 13 D2-exposed positions account for about −0.56.

### FF-5 [PE DB I7-002]: TP extension is net positive system-wide

- 37 positions reached the original signal TP, and all 37 had the TP extended.
- 32 of them won. 5 ended as losers (4 CLOSED, including 143; 1 STOPPED at −0.698). Net **+6.79**.
- **[AI]** 143's mechanism is real but a minority outcome (5/37). The extension policy is not a correction priority. The dependency of the floor on tick gaps (8 s for 143) is the smaller, specific risk.

### FF-6 [PE + ER + AI]: geometry and fees

- The 0.5% stop floor combined with a 3× ATR target gives R:R = 6×ATR% whenever ATR is below 0.25% (`AtrRiskService.java:34-45`; `application.yml:137-143`).
- No R:R or fee gate exists. R:R < 1 only costs −30 EQ (`ExecutionIntelligenceService.java:991-993`, cutoff 50 at `:229`).
- Paper P&L has no fee, so **no recorded loss is a fee loss**.
- [ER] An assumed 0.10% per side (third-party guides; Binance's own fee page was not obtained) would take the system from −4.17 to −16.56. That matters for **live** viability only.
- **Entry Quality (EQ)** does not rank outcomes. The relationship is confounded by the ATR/stop bucket.
- Three entries scored exactly 50: 158, 162 and 182. 182 cleared the cutoff by less than one tick of reference price.

### FF-7 [PE]: other observations about the frozen trades

| Observation | Status | Evidence |
|---|---|---|
| 162 admitted as BALANCED_EARLY at a LATE_ENTRY price | Scope gap, by design | FIX-041 applies only to IMMEDIATE_VALIDATION (`:672-684`); FIX-119 skips when there is ≥1 BUY (`:1275-1280`). Its route is net positive system-wide (FF-4), so tightening it lacks support. |
| 172 SHIB ATR (0.0068e-6) is smaller than the 1e-8 tick | ATR-based EQ and R:R are meaningless here | `assessEntryQualityAtPrice` has no tick-awareness check |
| 143 persisted EQ 70 (1m ATR), but it was admitted at EQ 60 (5m ATR) | Audit gap | `:631-635`, `:439` vs `PaperTradingService.java:377-385` |
| SETUP_TIMEFRAME_ATR writes no FIX-154 audit row (143) | Audit gap | No `buy_htf_assessment_audit` row for 159408 |
| The audit's `five_minute_raw_decision` holds the label, not the original decision (182) | Audit gap | Audit 2425 = NEUTRAL; signal original = STRONG_SELL |
| SELL fills have NULL `decision_price_usdt` and `execution_price_observed_at` (all six) | Audit gap | I1-004 |
| HTF exits are evaluated only when a price update arrives (172: 37.6 s; 182: 12.7 s; 137: 4.7 s) | Design property, no P&L effect here | `LivePositionProtectionService.java:235-244`, `:95-97` |
| A 0% ATR position plan is treated as 100% when sizing (143) | Semantic risk | `PaperTradingService.java:328-333` |
| The 1m "BUY after a sell run" pattern (137, 182) | Anecdotal only | System-wide, the 0-sell group holds −3.30 (I6-002) |

### FF-8 [PE]: outside the frozen six (operational)

- **Host clock:** the app and DB share a host clock that ran 35–556 ms behind Binance (10-17 log). Price-age checks correct for this, but audit timestamps do not.
- **10-17 session:**
  - Run 1 applied Flyway **V111** at 03:11:55 KSA (00:11:55 UTC), then failed to start because no `ReplaySizingPolicy` bean existed.
  - Run 2 ended without a shutdown line at 03:26:34 KSA (00:26:34 UTC). Nothing traded afterwards, so the frozen set is stable.
- **Working-tree code:** the session ran from `target\classes` with modified tracked files, so it is not proven that the 10-11/12 runtime equals the current working tree (U4-1). Runtime audit rows match the traced paths for 137, 158, 162, 172 and 182.
- **Contradiction with AGENTS.md:**
  - AGENTS.md calls FIX-154 "not-yet-implemented".
  - But V107/V109 were applied on 10-10 and `buy_htf_assessment_audit` holds runtime rows.
  - Reported only; not resolved here.
- **Study-design note for the Coordinator:**
  - `status=CLOSED` leaves out all 75 STOPPED positions (−15.876 gross), which is where the system loses money.
  - The frozen six are near-zero-edge "scratch" exits averaging −0.046 USDT.

---

## 3. DATABASE EVIDENCE [PE]

All queries were read-only and bounded, run by the Coordinator wrapper; the password was never shown.

| Query | What it established |
|---|---|
| DBQ-FTS-001..005 (Coordinator) | `paper_position` schema and status values; wallet SELL P&L comparison; the Replay table is separate (`wallet_position_test`); the frozen six |
| I1-001 | Session tz SYSTEM = Arab Standard Time (+03:00), so displayed values are KSA |
| I1-002 | Frozen rows: prices, P&L = qty × (exit − entry), close reasons, CRC baselines |
| I1-003 | Entry signals LIVE, `data_quality_valid=1`, BTC context age 1–9 s |
| I1-004 | Wallet fills 344/346, 358/359, 392/401, 403/407, 426/429, 451/454; `fee_usdt=0`; routes and messages |
| I1-007/008/009 | 143 TP extensions; tick and price-event status `APPLIED / PROTECTION_CALLBACK_COMPLETED`; highest prices |
| I2-001 | Timestamp columns are `timestamp(6)`; the BTC-context times are `datetime(6)` |
| I2-002/003 | 1m/5m/1h candles before entry: 182 thin drift to the high; 162 breakout candle; 172 pinned between ticks |
| I2-004 | 143 ordered ticks: 4.100 → (8 s) → 4.027 |
| I2-005/006/007/009 | Signals during each position's life; FIX-154 schema applied (V107/V109); stoppage map; opportunity rows |
| I3-001..004, I3-008 | Confluence reads; 5m commit timings; work completion (`paper_position_id`); ingest lag |
| I4-001 | FIX-154 audit rows: 1938 (137), 2199 (162), 2284 (172), 2425 (182); none for 159408 (143) |
| I4-002 | Ingest-lag spikes, none at an entry minute; about 2 s stall after the 182 exit transaction |
| I4-004 | 13 positions entered on a blocked or neutralised 5m |
| I5-001..004 | Stored ATR and flags; EQ reference prices; 143/162 messages; R:R and EQ buckets with an assumed fee |
| I6-001/002 | Status × stop-type × ATR buckets; sell-run buckets |
| I6-003 | **Failed:** `paper_position.highest_price` does not exist (corrected in I7-002) |
| I7-001 | Decision paths: 176655 MULTI_TIMEFRAME downgrade STRONG_SELL → NEUTRAL; 171559 RANGE_ENTRY_LOCATION veto; 165415 original BUY; 165412 original WATCH; 159285 BUY; 159407 WATCH |
| I7-002 | 37 positions reached the original TP: 36 CLOSED (4 losers), 1 STOPPED; all had extended TPs |
| I7-003 | Gross P&L by route (FF-4) |
| I7-004 | Stop changed: 12/118 CLOSED and 13/75 STOPPED; STOPPED average exit −0.075% versus the stop |
| **Never returned** | I3-005, I3-006, I3-007, I4-003 (system-wide boundary-race size) |

No new DB request is issued in this iteration.

---

## 4. LOG EVIDENCE [PE]

- **Readable:** only `logs/crypto-ai-trader.log` for the 10-17 session, 03:11:41–03:26:34 KSA (00:11:41–00:26:34 UTC). It gave:
  - Startup from `target\classes` with no active profile (`:62`).
  - `jdbc:mysql://localhost:3307/crypto_ai` (`:73`).
  - Clock-sync deltas of 35–556 ms (`:36`, `:96`, `:165`, `:293`, `:1040`, `:8905`).
  - Run 1 startup failure, `ReplaySizingPolicy` (`:38`, `:47-60`).
  - A single WebSocket thread, `WebSocketClient-SecureIO-1`.
- **Not readable:** the logs for the six trades exist only as `crypto-ai-trader.2026-10-11.*.log.gz` and `2026-10-12.*.log.gz`. **No log evidence is claimed for any frozen trade.** Every trade-level statement comes from DB rows or code.
- **Still needed (Coordinator extraction):** FIX-11G, FIX-119, `SIGNAL_SAVED`, `WORKER_STAGE` and `CLOCK_SYNC` lines for the six symbols and windows; and the cause of the 8–38 s tick gaps (U6-1).

---

## 5. CODE EVIDENCE [PE]

The working tree was read; equality with the 10-11/12 runtime is partly confirmed by audit rows (U4-1).

| Topic | References |
|---|---|
| D1 boundary race | `AnalysisService.java:150`; `ExecutionIntelligenceService.java:2455-2461`, `:2606-2609`, `:1485-1487`; `TradeExecutionValidationService.java:367-379` |
| D1 Replay ordering | `RegressionTestWorker.java:557-560`, `:584`, `:616`, `:787-793`; `ExecutionReplayScope.java:106-120` |
| D2 label-only veto | `HigherTimeframeBuyAssessmentService.java:20-26`, `:46-51`; `TradeExecutionValidationService.java:93-109`, `:263-284`, `:300-321` |
| D2 contrasting convention | `ExecutionIntelligenceService.java:296`, `:355`, `:402`, `:1368-1369`, `:1425-1426`, `:1478`, `:1487`, `:1542-1544`, `:1634`, `:1865-1867`, `:1915-1921` |
| Final-decision stages | `FinalDecisionService.decide :122-148`; `safeNeutral :54-57`, `:296-297`; `original_decision` from `AnalysisService.java:303`, `:411` |
| Wake-up plan source | `ExecutionIntelligenceService.java:315-316`, `:377`, `:381-387`, `:420-421`, `:456`; `PaperTradingService.java:296-311`, `:328-330`, `:367`, `:408-409` |
| EQ model | `ExecutionIntelligenceService.java:900-926`, `:976-1017`, `:229`, `:1171-1183`; `PaperTradingService.java:377-385` |
| FIX-041 / FIX-119 scope | `ExecutionIntelligenceService.java:672-684`, `:1275-1280` |
| Risk geometry | `AtrRiskService.java:34-45`; `application.yml:137-150` |
| Protection | `LivePositionProtectionService.java:54-58`, `:95-97`, `:108-110`, `:144-150`, `:174-184`, `:210-216`, `:235-244`, `:391-411`; `WalletTransactionCoordination.java:38-47` |
| Transaction scope | `SignalProcessingCoordinator.java:36-37`, `:64-103` |
| Price ingest | `BinanceKlineService.java:94-111`; `MarketPriceEventService.java:39-44`, `:66-72` |
| STOPPED semantics and progressive adds | `PaperTradingService.java:182-184`, `:263-275`, `:433` |

---

## 6. EXTERNAL RESEARCH [ER]

- **Binance spot WebSocket docs:** kline streams update every 2000 ms for intervals other than 1s. There is a conflicting "pushes every second" phrasing, and nothing documents skipping updates when there are no trades. Source: https://developers.binance.com/docs/binance-spot-api-docs/web-socket-streams
- **Binance spot fees:** 0.10% maker / 0.10% taker (0.075% with BNB), from third-party guides only (Unger Academy, CryptoPotato, Analytics Insight). The tier is unverified against Binance's own page and was used only for the live-viability arithmetic.

---

## 7. CANDIDATE CORRECTIONS AND REGRESSION CHECKS

**These are candidates only.** NO CHANGE was made or authorised. Each would need FINAL_CHANGE_SPECIFICATION, dual-agent approval and Jean's approval.

| ID | Candidate | Basis | Regression / validation |
|---|---|---|---|
| C1 | Bind same-boundary HTF lookups to candle-close identity, not wall-clock `generatedAt` (or make Production use Replay's 1h→5m→1m order) | D1 (143, 158) | Run I4-003 first to size it; Replay 143 and 158 and show identical routes in Production and Replay; boundary tests at ±1 ms ordering |
| C2 | In direct-BUY validation, apply `strongerDecision(decision, original)` to the 5m bearish veto and decide policy for a 5m with `final_entry_allowed=0`. This is a FIX-154-scope design decision that must keep legacy `5M_BEARISH_VETO`. | D2 (182 causal; 172 unproven) | Replay 182 and 181 (expect veto) and 172 (outcome to be observed); rerun I4-004; negative test where a genuinely NEUTRAL 5m (137) is unchanged |
| C3 | Audit completeness: 5m `original_decision` and `final_entry_allowed` in `buy_htf_assessment_audit`; an audit row for SETUP_TIMEFRAME_ATR; persist the EQ that admitted the trade; persist the price observation on SELL fills | FF-7 | Schema/insert tests; Replay parity of audit rows |
| C4 | Fee-aware minimum target or R:R, or a target that scales when the stop is floored | FF-6, live viability only (gross bucket is break-even) | System-wide replay by R:R bucket, before and after an assumed fee |
| C5 | Tick-aware ATR/EQ (ATR smaller than one tick → not-assessable) | 172 | Unit tests on SHIB-scale prices |
| C6 | **Not recommended:** widening FIX-041 to ACCUMULATED_EVIDENCE (route is +0.20 net) or changing TP extension (+6.79 net) | FF-4, FF-5 | — |
| C7 | Study design: add a STOPPED-loss window to the charter | FF-4, FF-8 | Coordinator decision |

---

## 8. UNKNOWNS AND MISSING EVIDENCE

| ID | Unknown |
|---|---|
| R1 | **Replay not run.** The D1 counterfactuals (143, 158) and D2 counterfactuals (172, 182) are inferred from code. |
| R2 | System-wide size of D1 (I4-003) and the I3-005/006/007 results were never returned. |
| R3 | Log lines for the six trades (`.gz` only); cause of the 8–38 s tick gaps (U6-1). |
| R4 | Equality of the working tree with the deployed build on 10-11/12 (U4-1); partly confirmed. |
| R5 | Whether an alternative entry route would have admitted 182 if D2 had been fixed. |

---

## 9. SELF-REVIEW CORRECTIONS APPLIED IN THIS FREEZE

- **I7 exit-time label fixed.** I7 labelled wallet SELL times as "paper `closed_at`". Both are now given separately:

| ID | Paper `closed_at`, KSA |
|---|---|
| 137 | 21:00:12.122 |
| 143 | 22:43:36.229 |
| 158 | 03:30:06.638 |
| 162 | 04:26:04.116 |
| 172 | 07:15:45.484 |
| 182 | 10:05:20.486 |

  162 is the only case where the paper `closed_at` precedes the wallet SELL (signal path).
- **182 resolved by I7-001.** The neutralisation was the intentional MULTI_TIMEFRAME downgrade. 182 is now classified as a design-level logic defect, causal under a code counterfactual.
- **158 resolved by I7-001.** The wake-up gap in the original-decision check was inert.
- **Earlier I6 claims, kept withdrawn or downgraded:** "fees are the biggest cost" (scoped to live trading), "negative EV by construction" for 158/162/182 (gross bucket is break-even), the sell-run flip cause (anecdotal), and "158 lost the better geometry" (intended design).

**FROZEN. Iteration 8 of 8. Claude independent track complete.**

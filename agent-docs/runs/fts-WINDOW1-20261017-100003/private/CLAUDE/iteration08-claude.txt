# CLAUDE_FAILED_TRADE_STUDY.md: WINDOW1, final frozen classification (iteration 8 of 8)

## SUMMARY

| Field | Value |
|---|---|
| Agent / track | CLAUDE, independent track (no cross-review) |
| Study window | WINDOW1 |
| Snapshot | 2026-10-17 07:00:04 UTC = 2026-10-17 10:00:03 KSA (on schedule) |
| Authoritative entity | `crypto_ai.paper_position` |
| Selection | `status=CLOSED AND closed_at IS NOT NULL AND realized_pnl<0 ORDER BY closed_at DESC LIMIT 6` |
| Frozen trades | 182, 172, 162, 158, 143, 137. All analysed, none substituted. |
| Status | **FROZEN.** Iteration 8 self-review is complete. |
| Changes made | **NONE.** No code, config, DB write, git, deploy or documentation change. Analysis only. |
| Combined loss of the six | **−0.2732 USDT gross.** Fees are not modelled. |
| Classifications | 2 valid losing trades (137, 158). 1 valid under Production rules but a likely Replay-parity defect (162). 2 candidate entry defects or design gaps (143, 172). 1 confirmed code inconsistency (182). |
| Confirmed defect that caused a loss across the system | **NONE PROVEN.** |
| Stale data / latency / exit-logic defect | **Ruled out for all six.** |
| Log evidence | **MISSING.** The logs are gzipped and could not be read (U-18). |
| Replay evidence | **NOT RUN** (U-21). All parity statements are code-trace inferences. |

**Labels used:** **[PE]** = PROJECT EVIDENCE (DB, code or migrations). **[ER]** = EXTERNAL RESEARCH. **[AI]** = AGENT INFERENCE.

**Time convention (Finding 0) [PE]:**
- The database stores UTC.
- The investigator's query tool runs at +03:00 and **displays `TIMESTAMP` columns already converted to KSA**. Proof:
  - I2-001: `TIMEDIFF(NOW(),UTC_TIMESTAMP())=03:00`, and `UNIX_TIMESTAMP(generated_at)` of signal 156723 = 17:55:01.576 UTC, displayed as 20:55:01.576.
  - [ER] A Binance `aggTrades` cross-check (iteration 4) agrees.
- Only `trade_signal.btc_context_evaluated_at` and `btc_signal_generated_at` are `DATETIME`, so they display as UTC (I3-004).
- Every time below is written **KSA (UTC)**.
- This contradicts the line "Raw MySQL timestamps are UTC" in `DATABASE_INVESTIGATION_RULES.md`, as it applies to this tool's output. Reported, not edited.

---

## MANDATORY TABLE: one row per frozen ID

`paper_position` times trail the wallet fill by 1.2–3.2 s, because the paper row is a mirror written after the fill (I3 timing chain).

| ID | Symbol | Opened KSA (UTC) | Closed KSA (UTC) | Entry → exit | Move | P&L USDT | Close reason | Entry route / HTF authority | EQ (decision / persisted) | R/R | Size | Primary cause (decision-time info only) | Classification | Reproduces in Replay? | Evidence |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **182** | ACEUSDT | 10-12 09:57:06.476 (06:57:06.476) | 10-12 10:05:20.486 (07:05:20.486) | 0.1884 → 0.1880 | −0.21% | −0.031847 | SELL_CONFIRMED | IMMEDIATE_VALIDATION / FIX-11G `BALANCED_NEUTRAL_5M_WATCH_1H` | 50 / 50 (LATE_ENTRY, exactly at the cutoff) | 0.48 | 15% (≈15 USDT) | The 5m 176655 was **STRONG_SELL, downgraded to NEUTRAL** by the 1h WATCH. The same 1h WATCH then served as the bullish half of the FIX-11G authority, so it was counted twice. `validateBuy` sees only the effective label. Counter-trend entry (WEAK_DOWNTREND). ATR extension 5.95 against a block threshold of 6.0. | **CONFIRMED CODE INCONSISTENCY (F-38); candidate defect. No proven P&L cost across the system (F-46).** | Likely yes: the logic does not depend on timing. Not run. | FTS-005, I1-001, I1-006, I4-002, I4-004, I5-005, I6-002, I7-003 |
| **172** | SHIBUSDT | 10-12 06:36:05.087 (03:36:05.087) | 10-12 07:15:45.484 (04:15:45.484) | 5.49e-6 → 5.48e-6 | −0.18% (−1 tick) | −0.045537 | HTF_SELL_CONFIRMED | IMMEDIATE_VALIDATION / `BALANCED_STRONG` 75%, capped to 25%; opportunity 39062 | 60 / 60 | 0.74 (≈1.0 after tick rounding) | 25% | The 5m 171559 was BUY but its **own entry was blocked** (`final_entry_allowed=0`, stage `RANGE_ENTRY_LOCATION`, confidence 49). It was still used as BUY confirmation. The range guard was sidestepped when the regime was relabelled to WEAK_UPTREND. One SHIB tick is 0.18% of price. | **CANDIDATE DESIGN GAP (F-25).** | Likely yes. Not run. | FTS-005, I1-001, I2-005, I4-002, I6-001 |
| **162** | DOGEUSDT | 10-12 04:02:06.415 (01:02:06.415) | 10-12 04:26:04.116 (01:26:04.116) | 0.08705 → 0.08675 | −0.34% | −0.051694 | STRONG_SELL (exit signal 168277, SIGNAL_SELL at the signal price) | ACCUMULATED_EVIDENCE / `BALANCED_EARLY` | 50 / 50 (LATE_ENTRY) | 0.44 | 15% | A late entry at or above the upper Bollinger band (RSI 68, 2.82 ATR above SMA20). Released by a single BUY (FIX-119). Passed the chase cutoff only because the opportunity age was **20 min 59.976 s**; 24 ms more would have scored EQ 45, a block. | **VALID UNDER PRODUCTION RULES; CANDIDATE REPLAY-PARITY DEFECT (F-40).** | **Likely NO.** Replay uses the exact candle close, giving age 21:00, EQ 45 and a block. Not run. | FTS-005, I1-001, I5-002, I5-003 |
| **158** | XRPUSDT | 10-12 02:35:04.811 (10-11 23:35:04.811) | 10-12 03:30:06.638 (10-12 00:30:06.638) | 1.4108 → 1.4090 | −0.12% | −0.031897 | SELL_CONFIRMED | SETUP_CONFIRMATION_WAKEUP / `BALANCED_STRONG`; opportunity 37621, 53 min old | 50 (rebuilt as 60 − 10 age penalty) / 50 | **0.33** | 25% | The plan's target (+0.171%) is below a 0.15–0.20% round-trip fee. Volume did not confirm (6/20). | **VALID LOSING TRADE (by design).** EQ fully explained (U-32 closed). | Possibly not: the wake-up path looks for "1m at or before the 5m's `generatedAt`", which orders differently in Replay (F-18). Not run. | FTS-005, I1-001, I5-001, I6-001 |
| **143** | EGLDUSDT | 10-11 22:40:04.858 (19:40:04.858) | 10-11 22:43:36.229 (19:43:36.229) | 4.033 → 4.027 | −0.15% | −0.037193 | PROFIT_LOCK_HARD_EXIT | SETUP_TIMEFRAME_ATR (requires a bullish 5m) | 60 (5m ATR) / 70 (1m ATR at fill) | 1.50 | 25% | Entry authority came from a **superseded** 5m BUY 159285. The newer 5m, WATCH 159407, was generated 2 ms after the 1m but inserted 175 ms *before* the fill, and the wall-clock cap excluded it. Chase context: RSI 84; re-entry 22 s after a profit-lock exit, 0.93% higher. Exit: target extensions, then a 177 ms sweep from 4.0758 through the 4.035 floor, between kline pushes. | **CANDIDATE ENTRY DEFECT (F-11/F-18, boundary race). Exit valid; no protection defect (F-22).** | **Likely NO.** In Replay the 22:40 1m sees the WATCH 5m, so the route returns null. Not run. | FTS-005, I1-001, I2-002, I2-005, I3-003, I3-005, I4-001 |
| **137** | EGLDUSDT | 10-11 20:55:04.510 (17:55:04.510) | 10-11 21:00:12.122 (18:00:12.122) | 3.999 → 3.987 | −0.30% | −0.075019 | SELL_CONFIRMED | IMMEDIATE_VALIDATION / FIX-11G, with a **genuinely NEUTRAL** 5m 156721 | 58 / 58 | 0.64 | 25% | A STRONG_BUY reversal (RVOL 8.26×) right after 12 STRONG_SELLs. The target (4.0119) sat at an ask wall (4.012). Net R/R after fees ≈0.17. | **VALID LOSING TRADE; rules followed.** | Likely yes. Not run. | FTS-005, I1-001, I4-002, I4-004, I5-002 |

Notes:
- 137 and 143 are on 2026-10-11 KSA. The other four are on 2026-10-12 KSA.
- Combined gross: −0.273187 USDT. **[PE]** `wallet_trade.fee_usdt = 0` on all 445 rows (I7-001).

---

## FINAL FINDINGS REGISTRY

### Confirmed (project evidence)

- **F-38 / F-46: 182 confirmed code inconsistency [PE].**
  - `TradeExecutionValidationService.validateBuy` passes `assessment.effectiveFiveMinute()` as a bare label (`TradeExecutionValidationService.java:102-108`), so the FIX-11G branch (`:263-284`, allow at `:278`) cannot see `original_decision=STRONG_SELL`.
  - Other EIS routes do read `getOriginalDecision()`.
  - The FIX-11G comment's premise, "neither timeframe is bearish", is false for 176655.
  - **I7-003 [PE]:** all 8 BUYs in the system whose 5m was a downgraded bearish signal went through FIX-11G.
    - Losers: 37 (−0.1447, STOPPED), 181 (−0.5841, STOPPED, 50 USDT notional) and 182 (−0.0318).
    - Winners: 95, 97, 113, 138, 139 (+0.3294 in total).
    - Net −0.4313. **Without trade 181 the class is net +0.153.**
  - **[AI]** The P&L cost rests on a single large stop. The logic inconsistency is confirmed; a loss driver across the system is not.
- **F-22: 143 exit [PE + ER].**
  - The Binance trades show the move from lock to below the floor took 177 ms, inside an 8 s gap with no EGLD trades (other symbols kept streaming).
  - The wallet SELL ran 25 ms after the next kline push.
  - There is no protection defect.
- **F-23 [PE + ER]:** all exits were triggered by price ticks.
  - `executed_at` is the uncorrected JVM `Instant.now()`; `observed_at` is Binance event time E.
  - The JVM clock trails Binance by about 0.1–0.2 s. This is an audit-clock mismatch only.
- **F-15 / F-16 / I3 timing chain [PE]:**
  - Decisions landed 2.9–4.2 s after the candle close.
  - Fill prices were 1.5–3.0 s old, inside `price-max-age-seconds: 15`.
  - There was no stale data and no latency effect on any of the six.
- **U-27 / F-49 [PE]:** FIX-154 (V107/V109) was installed on 2026-10-10 and wrote `NO_CONFLICT` audit rows for 4 of the 6 entries. **This contradicts AGENTS.md**, which says FIX-154 is "not-yet-implemented". Reported only.
- **F-45 / F-21 [PE]: the selection is structurally biased.**
  - The 75 STOPPED positions lost −15.88 USDT, against −4.17 for all 193 closed or stopped positions. The CLOSED losers total about −1.3 USDT across 26 trades.
  - The six frozen trades come from a class that explains under 10% of gross losses.

### Rejected or withdrawn (with evidence)

- **F-37** ("fresh reversal after a STRONG_SELL streak drives losses"): rejected by I6-002. The 5+ STRONG_SELL bucket: n=34, 41% losers, +0.212 gross. This is the best bucket, and it contains 137, 143 and 182.
- **"Raise the Entry Quality cutoff":** rejected by I5-004 / F-39. The EQ 50–54 band has the lowest loss rate (41%) and is the only positive band.
- **"Tighten FIX-11G / FIX-119":** no support across the system (F-41: FIX-11G 46% losers, −0.288 over 35 trades).
- **Latency as a cause:** rejected (I3).
- **"Floor-stop positions almost never reach the stop":** withdrawn. 25% are stopped out (I6-003).
- **"172 had no opportunity":** withdrawn. Opportunity 39062 executed it (I6-001).

### Observations across the system (suggestive, not significance-tested)

- **F-44 [PE + AI]:**
  - Floor-stop winners average about 0.20–0.22% of position value, roughly one round-trip fee.
  - At an assumed 0.2% fee, estimated net P&L is −5.43 (floor-stop) and −11.13 (ATR-stop).
  - Fees are not modelled anywhere in paper P&L (I7-001: 445 / 445 fills have zero fee).
- **I7-002 [PE]: daily breakdown (KSA days, `DATE(opened_at)` in the +03:00 session).**

  | Day | Positions | Loss rate | Gross P&L (USDT) |
  |---|---|---|---|
  | 10-10 | 63 | 56% | −1.646 |
  | 10-11 | 50 | **42%** | **+0.750** |
  | 10-12 | 38 | **61%** | **−2.370** (−0.18% of notional) |
  | All 7 days | 193 | — | −4.171 (matches I6-003) |

  - **[AI]** Four of the six frozen trades fall on the worst day. Day-level variance is large, and code version cannot be separated from market regime without deployment history (F-47).
- **[PE]** The last `wallet_trade` was at 2026-10-15 03:15:14 KSA (10-15 00:15:14 UTC). There are no fills from then until the snapshot, 10-17 10:00 KSA. Out of scope. Combined with the 1m analysis-latency drift on 10-12 (F-29, max 74.2 s), it warrants an operational check.

---

## DATABASE EVIDENCE [PE]

All queries were read-only and run by the wrapper. Result files are in `agent-docs/runs/fts-WINDOW1-20261017-100003/db/` and `db/CLAUDE/`.

| Query ID | Tables | Established |
|---|---|---|
| DBQ-FTS-001 | `paper_position` schema | Columns: `opened_at` / `closed_at` are `timestamp(6)`; no fee column |
| DBQ-FTS-002 | `paper_position` | CLOSED 118 (26 negative); STOPPED 75 (all negative) |
| DBQ-FTS-003 | `wallet_trade` | 445 EXECUTED, 101 negative |
| DBQ-FTS-004 | information_schema | `wallet_position_test` (Replay) exists and is excluded |
| DBQ-FTS-005 | `paper_position` | Frozen six: IDs, P&L, times, close reasons, signal IDs |
| I1-001 / I1-006 / I1-007 | `paper_position`, `wallet_trade`, `wallet_managed_position` | Entry and exit reasons, routes, EQ, fill messages, `fee_usdt=0` |
| I2-001 | session / `trade_signal` | Time-zone proof (Finding 0) |
| I2-002, I3-005 | `market_price_event` | 143: 8 s EGLD silence, then 4.027 below the floor; stream-wide check |
| I2-005, I3-003, I4-001 | `trade_signal`, `wallet_trade` | 1m/5m boundary race: 86% of the 5m signals generated after the 1m; 70 boundary BUYs, 17 with a newer 5m that was not bullish; P&L impact ≈0 |
| I3-004 | information_schema | TIMESTAMP vs DATETIME column map |
| I3-006, I4-005 | `trade_signal`, `wallet_trade` | 1m latency per day and hour; activity after 10-12 |
| I4-002 | `trade_signal` | 171559 `final_entry_allowed=0`, stage `RANGE_ENTRY_LOCATION`; 176655 NEUTRAL with original STRONG_SELL; 156721 genuinely NEUTRAL |
| I4-003 | `market_price_event` | `created_at` 45–156 ms before Binance E (clock offset) |
| I4-004 | `flyway_schema_history`, `buy_htf_assessment_audit` | V107/V109 installed 10-10; FIX-154 `NO_CONFLICT` on 4 of 6 entries |
| I5-001 … I5-005 | `trade_signal`, `execution_opportunity`, `wallet_managed_position` | EQ inputs; EQ-band and target-band outcomes; why 176655 was downgraded (`MULTI_TIMEFRAME` step 5); route outcomes |
| I6-001 | `execution_opportunity`, `wallet_trade` | Opportunities 37621 (158, 53 min old) and 39062 (172) |
| I6-002 | `paper_position`, `trade_signal` | Outcome by STRONG_SELL bucket (F-37 rejected) |
| I6-003 | `paper_position` | Floor-stop vs ATR-stop classes; estimated fees |
| **I7-001** | `wallet_trade` | 445 rows, **0 non-zero fees**; first fill 10-03 20:02 KSA, last 10-15 03:15 KSA |
| **I7-002** | `paper_position` | Daily breakdown (above); totals reconcile to 193 / −4.171 |
| **I7-003** | `paper_position`, `buy_htf_assessment_audit`, `trade_signal` | The 8 FIX-11G BUYs with a downgraded bearish 5m; net −0.431, but +0.153 without trade 181 |

No new DB_REQUEST is issued in iteration 8; the study is frozen.

---

## LOG EVIDENCE

- **Status: MISSING (U-18) [PE].**
  - `logs/crypto-ai-trader.2026-10-11.*.log.gz` and `…2026-10-12.*.log.gz` exist, but my read-only tools cannot decompress gzip.
  - The current `logs/crypto-ai-trader.log` starts on 2026-10-17 and has no match for any frozen signal ID.
  - It is also unknown whether the log file names use KSA or UTC dates.
- **No log content has been invented.** Every conclusion above rests on DB rows, code and Binance public data.
- **Greps recommended for the coordinator:**
  - Signal IDs 176710, 176655, 171589, 171559, 167668, 165412, 165415, 159408, 159407, 159285, 156723, 156721.
  - The text `BALANCED_NEUTRAL_5M_WATCH_1H` / FIX-11G post-guard lines (`TradeExecutionValidationService.java:273-279`).
  - FIX-105 chase-reference debug lines.
  - `[FIX-153]` between 22:43:20 and 22:43:40 KSA on 10-11.

---

## CODE EVIDENCE [PE]

`ExecutionIntelligenceService` is shortened to EIS and `TradeExecutionValidationService` to TEVS. Line numbers are from the current working tree; I re-checked the two key anchors this iteration (`TEVS:102-108`, `:278`; `EIS:2274`).

| Reference | Behaviour | Relevant to |
|---|---|---|
| `TradeExecutionValidationService.java:102-108` | `validateBuy` passes only `assessment.effectiveFiveMinute()` / `effectiveOneHour()` labels | 182 (F-38) |
| `TradeExecutionValidationService.java:263-284` | FIX-11G: 5m NEUTRAL + 1h WATCH grants 25%; no confidence floor; EQ ≥ 50 | 137, 182 |
| `TradeExecutionValidationService.java:234-236` | Stricter `BALANCED_NEUTRAL_5M` requires confidence ≥ 72 and EQ ≥ 70 (182 failed both) | 182 |
| `TradeExecutionValidationService.java:306-308` (`balancedBuy`) | Counts a 5m BUY as bullish without reading `finalEntryAllowed` (grep: no reader in TEVS or the HTF assessment) | 172 (F-25) |
| EIS `:1542, :1634, :1865, :1915, :2100` | Other routes do use `getOriginalDecision()` / `strongerDecision` | 182 (inconsistency) |
| EIS `:2455-2461`, `:2606-2608`; `AnalysisService.java:150` | 5m and 1h lookups capped at the 1m's wall-clock `generatedAt` (`Instant.now()`) | 143, 158 (F-18) |
| `RegressionTestWorker.java:584`, `:557-560`, `:787-791`; `ExecutionReplayScope.java:112`, `:122-137` | Replay uses the exact candle close, with ties ordered 1h > 5m > 1m. FIX-141 already moved the BTC lookup to candle ordering; the 5m and 1h lookups were not moved. | 143, 158, 162 (parity) |
| EIS `:935-1022` (`:988` age penalty, `:991` flat R/R −30, `:1000-1002` rejection-zone floor at 50) | Entry Quality scoring: all six scores rebuild exactly | all |
| EIS `:420-443` | The wake-up path finds the 1m at or before the 5m's `generatedAt` | 158 |
| EIS `:1271-1280` (FIX-119) | A single BUY skips the LATE_ENTRY deferral on the accumulated route | 162 |
| EIS `:672-673` (FIX-041) | Blocks BALANCED_EARLY + LATE_ENTRY on the direct path only | 162, 182 (asymmetry) |
| EIS `:2270-2275` | Opportunity memory `break`s at a 1m STRONG_SELL (real, but rejected as a loss driver) | 137, 143, 182 |
| `PaperTradingService.java:328-333`, `:377-385` | Size = cap × ATR %; a 0% ATR recommendation is treated as 100%; persisted EQ is re-scored at the fill price with the 1m ATR | 143 (F-34/F-35), 162 and 182 sizes |
| `LivePositionProtectionService.java:95-97`, `:207-216`, `:235`; `PositionExitPolicy.java:25-43` | Live exits are evaluated on ticks with the newest signals uncapped; hard-floor exit | all exits (F-24) |
| `ShadowProductionReplayService.java:629-633` | Replay evaluates the normal exit on signal arrival | exit-timing parity (F-24) |
| `BinanceKlineService.java:94`, `:108`; `WalletAutoExecutionService.java:178` etc. | `observed_at` = Binance E; `executed_at` = JVM clock | F-23 |
| `V107__fix_154_trusted_higher_timeframe_buy.sql`, `V109__fix_154_forward_replay_and_audit_columns.sql` | FIX-154 schema exists and is applied | F-49 |

**[ER] External research (context only):**
- Binance spot fees for a regular account are 0.10% per side, or 0.075% if paid in BNB. These come from third-party sources; the account's actual tier is unverified (U-35).
- Tick sizes come from the Binance `exchangeInfo` API: SHIB 1e-8 = 0.18% of price; ACE 0.0001 = 0.053%.
- Binance 1m kline streams push at most every 2 s.
- EGLD `aggTrades` for 10-11 17:59–18:00 UTC and 19:43 UTC.

---

## SUGGESTED FIXES: CANDIDATES ONLY

None is authorised. Each needs `FINAL_CHANGE_SPECIFICATION.md`, approval from both agents and approval from Jean.

1. **C-1 (182):** pass the 5m's original decision, or `strongerDecision`, into `validateBuy`, and stop the 1h WATCH counting twice. Regression check: 182 is rejected and the other 27 FIX-11G grants are unchanged. Re-measure the I7-003 class first, because its cost rests on trade 181.
2. **C-2 (143, 158, 162):** order the 5m and 1h execution lookups by candle close time, extending the FIX-141 pattern. Compute opportunity age on candle time. Regression check: identical Production and Replay decisions for 143, 158 and 162.
3. **C-3 (172):** make the BUY authorities respect the 5m's `final_entry_allowed`. Regression check: 172 is rejected.
4. **C-4 (across the system):** make R/R and plan geometry aware of fees and tick size, and make paper P&L record fees. This is the highest-value candidate for the system, given F-44 and I7-001.
5. **Withdrawn:** opportunity memory of STRONG_SELL streaks (F-37 was rejected).

**Mandatory before any fix:** a Replay run over the 143, 158 and 162 windows (U-21), and the log greps (U-18).

---

## UNKNOWNS (open at freeze)

- **U-18:** log evidence is missing.
- **U-21:** no Replay run. F-18 and F-40 are unconfirmed.
- **U-35:** the account's live fee tier.
- **U-37:** effects of code version versus market regime per day. Needs the deployment timeline.
- **U-39 (new):** why trade 181 (FIX-11G route, 25% exploratory) has a 50 USDT notional, double the 25 USDT of its peers.
- **U-40 (new):** no fills after 2026-10-15 03:15 KSA through the snapshot. Operational status unknown.

**Contradictions reported, not resolved** (both need dual-agent review under "Do Not Start From Zero"):
- Finding 0, time display vs `DATABASE_INVESTIGATION_RULES.md`.
- F-49, FIX-154 deployed vs AGENTS.md.

---

## REPORT (AGENTS.md trade-investigation format)

1. **What happened.** Six small paper BUYs on 2026-10-11 and 10-12 KSA, all closed by signal, HTF or profit-lock exits for −0.12% to −0.34%, −0.273 USDT in total.
2. **Why they entered and exited.**
   - Five entries were low-R/R plans (0.33–0.74). One was 1.50 (143).
   - Each was admitted by a timeframe authority. Three of those authorities read labels rather than evidence (143 superseded 5m, 172 blocked 5m, 182 downgraded STRONG_SELL 5m).
   - Exits followed `PositionExitPolicy` and the hard-floor rules, triggered by ticks.
3. **Did they follow the intended rules?**
   - 137 and 158: yes.
   - 162: yes under Production timing, but probably not under Replay timing.
   - 143, 172 and 182 depended on authority logic that is inconsistent or racy.
4. **Defects and missing evidence.**
   - Confirmed code inconsistency: F-38 (182).
   - Candidate defects: F-18 (143), F-25 (172), F-40 (162 parity).
   - None proven to cost money across the system.
   - Logs and Replay evidence are missing.
5. **Correction and regression checks.** See C-1 to C-4 above.

**Documentation check:** "Does this change require documentation updates?" No change was made, so none is required. The candidate knowledge items (Finding 0, F-49, F-45, I7-001) are offered to the coordinator for review by both agents; they are not written to `AGREED_KNOWLEDGE.md` or `claude-docs/` in this unattended analysis-only run.

**Isolation statement:** I did not open the Codex private or db folders, `LIVE_TEAM_STATUS.md`, `LIVE_EVENTS.log` or Codex memory.

**END OF FROZEN CLAUDE_FAILED_TRADE_STUDY.md (WINDOW1, iteration 8)**

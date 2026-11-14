# DAILY FAILED-TRADE INDEPENDENT FORENSIC STUDY

Window: WINDOW2
Run: fts-WINDOW2-20261029-204757
Implementation authorization: NOT AUTHORIZED
Suggested fixes are candidates only and were not implemented.

STUDY WINDOW: WINDOW2
SNAPSHOT TIME UTC: 2026-10-30 12:24:16
SNAPSHOT TIME KSA: 2026-10-29 20:47:57
INTENDED WINDOW CLOCK KSA: 2026-10-29 18:00:00
SNAPSHOT NOTE: SNAPSHOT CLOCK DRIFT: intended 2026-10-29 18:00:00 KSA; actual 2026-10-29 20:47:57 KSA. Exact window clock was not preserved.
AUTHORITATIVE ENTITY: crypto_ai.paper_position
SELECTION: status=CLOSED AND closed_at IS NOT NULL AND realized_pnl < 0 ORDER BY closed_at DESC LIMIT 6
EXCLUDED: OPEN/incomplete rows; NULL closed_at; non-negative realized_pnl; wallet_position_test (Replay)

SELECTED TRADE IDS:
1. 182
2. 172
3. 162
4. 158
5. 143
6. 137

PROOF QUERIES: DBQ-FTS-001 .. DBQ-FTS-005

## SECTION 1 - CODEX ANALYSIS

# CODEX_FAILED_TRADE_STUDY.md

## Summary

- **Study:** WINDOW2 daily failed-trade study, iteration 8/8 final classification.
- **Frozen scope:** `paper_position` IDs **182, 172, 162, 158, 143, 137** only.
- **Authority and selection:** `crypto_ai.paper_position`; stated selection is `CLOSED`, non-null `closed_at`, negative `realized_pnl`, newest six by `closed_at`. Replay table `wallet_position_test` is excluded.
- **Final classification:** **INSUFFICIENT DURABLE EVIDENCE — no individual loss classification is justified.**
- **Database evidence:** No Codex-permitted DB result exists in `db/CODEX/`; no DB facts are asserted.
- **Log evidence:** No exact frozen paper-position ID match was found in the current uncompressed `crypto-ai-trader.log` or `database-timing.log`. This is not evidence that a trade did not occur.
- **External research:** None used.
- **Change status:** Analysis only. **NO CHANGE** to code, configuration, database, Git, deployment, or documentation.

The supplied snapshot clocks conflict: `2026-10-30 12:24:16 UTC` converts to `2026-10-30 15:24:16 KSA`, not the supplied snapshot value `2026-10-29 20:47:57 KSA`. The intended clock, `2026-10-29 18:00:00 KSA`, was not preserved. The six IDs remain frozen scope, but no exact clock-window conclusion is valid.

## Mandatory frozen-trade classification

| Frozen ID | Lifecycle / P&L | Entry quality | Exit / protection | Final classification |
|---:|---|---|---|---|
| 182 | Not returned in permitted DB evidence | Unverifiable | Unverifiable | Insufficient durable evidence |
| 172 | Not returned in permitted DB evidence | Unverifiable | Unverifiable | Insufficient durable evidence |
| 162 | Not returned in permitted DB evidence | Unverifiable | Unverifiable | Insufficient durable evidence |
| 158 | Not returned in permitted DB evidence | Unverifiable | Unverifiable | Insufficient durable evidence |
| 143 | Not returned in permitted DB evidence | Unverifiable | Unverifiable | Insufficient durable evidence |
| 137 | Not returned in permitted DB evidence | Unverifiable | Unverifiable | Insufficient durable evidence |

No symbol, UTC lifecycle timestamps, KSA conversions, entry/exit signal IDs, close trigger, accepted protection observation, wallet execution, fee data, or historical configuration/version has been returned for any frozen ID. Consequently, none may be called a valid strategy loss, stop-loss defect, execution-delay failure, protection failure, or accounting discrepancy.

## DATABASE EVIDENCE

**PROJECT EVIDENCE:** The permitted directory `agent-docs/runs/fts-WINDOW2-20261029-204757/db/CODEX/` contains no result files. I did not read coordinator or other-agent database folders under the isolation rule.

**AGENT INFERENCE:** The charter’s frozen selection defines scope, but without the preserved snapshot state or returned proof-query results it does not independently prove that these were the six newest qualifying rows at the stated time.

Required trade-level proof remains:

1. `paper_position` lifecycle, entry/exit prices, P&L, signal links, stop/target, and close fields.
2. `production_exit_audit` terminal trigger.
3. Managed-position and `wallet_trade` BUY/SELL records, quantities, prices, fees, and execution messages.
4. Accepted `market_price_event` records (`LEGACY`/`APPLIED`) around the holding period.
5. Signal/work/candle evidence and historical deployment/configuration evidence.

### DB_REQUEST

QueryId: DBQ-CODEX-I8-001  
Purpose: Establish actual schema and the authoritative lifecycle/selection facts for exactly the six frozen positions before any loss classification. Raw timestamps returned by this query are UTC.

SQL:
```sql
SELECT
    table_name,
    ordinal_position,
    column_name,
    column_type,
    is_nullable,
    column_key,
    extra
FROM information_schema.columns
WHERE table_schema = 'crypto_ai'
  AND table_name IN (
      'paper_position',
      'production_exit_audit',
      'wallet_managed_position',
      'wallet_trade',
      'market_price_event',
      'trade_signal',
      'signal_processing_work',
      'position_analysis',
      'position_management_event'
  )
ORDER BY table_name, ordinal_position;

SELECT id, status, closed_at, realized_pnl
FROM crypto_ai.paper_position
WHERE id IN (182, 172, 162, 158, 143, 137)
ORDER BY FIELD(id, 182, 172, 162, 158, 143, 137);

SELECT id, status, closed_at, realized_pnl
FROM crypto_ai.paper_position
WHERE status = 'CLOSED'
  AND closed_at IS NOT NULL
  AND realized_pnl < 0
ORDER BY closed_at DESC
LIMIT 6;
```

### END_DB_REQUEST

## LOG EVIDENCE

**PROJECT EVIDENCE:** A bounded exact-ID search of the uncompressed current `logs/crypto-ai-trader.log` and `logs/database-timing.log` found no `positionId=` or `paperPositionId=` entry for IDs `182, 172, 162, 158, 143, 137`.

This does not establish absence of execution or failure. The local logs do not establish the relevant dates, symbols, managed-wallet position IDs, or archive coverage. The compressed archives cannot be attributed safely before database linkage supplies those facts.

## CODE EVIDENCE

**PROJECT EVIDENCE — current source only; historical deployment applicability is unknown:**

- `paper_position` is the authoritative paper lifecycle record and is closed with price, P&L, reason, and signal context in `PaperTradingService`. Current-source inspection shows `completeClose(...)` persists the paper close before calling `executeSignalLinkedExit(...)`.  
  [PaperTradingService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/service/PaperTradingService.java:487)

- The current profit-lock path similarly saves the paper close before calling `executeProfitLock(...)`.  
  [PaperTradingService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/service/PaperTradingService.java:452)

- The signal-linked wallet exit method has normal early-return paths; therefore source order alone cannot prove that a closed paper row has a matching SELL. Actual `wallet_trade` and managed-position records are required.  
  [WalletAutoExecutionService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/wallet/service/WalletAutoExecutionService.java:211)

- The direct live-price route marks an event `APPLIED` after the protection callback completes. `APPLIED` is useful protection-path evidence, but alone does not prove that a particular paper/wallet position existed or closed.  
  [LivePositionProtectionService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/position/service/LivePositionProtectionService.java:71)  
  [MarketPriceEventService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/market/service/MarketPriceEventService.java:123)

- `production_exit_audit` is the strongest intended terminal-cause record because it records a distinct `closeTrigger`; it is diagnostic evidence, not settlement proof.  
  [ProductionExitAuditService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/audit/service/ProductionExitAuditService.java:33)

**AGENT INFERENCE:** The current code exposes a reconciliation risk for a trade that proves to have used a signal-linked or profit-lock close and lacks a corresponding wallet SELL. It is not evidence that any frozen trade has that defect.

## Candidate follow-up only — NO CHANGE

If returned DB evidence proves a paper-only close on the signal/profit-lock route, a separately governed change candidate would be to make the wallet-execution outcome explicit and reconcile it atomically with the paper close. Regression coverage would need normal no-op returns, duplicate execution keys, exceptions/rollback, and direct-price-route parity.

Until the requested durable evidence is returned, no trade-specific fix is justified.


## SECTION 2 - CLAUDE ANALYSIS

# CLAUDE_FAILED_TRADE_STUDY.md: WINDOW2, final (Iteration 8 of 8)

**Run:** `fts-WINDOW2-20261029-204757` · **Agent:** CLAUDE · **Status:** FROZEN
**Mode:** analysis only. I changed no code, config, database or git state, and deployed nothing. Plan mode is on and this session has no Write tool, so the whole study is in this response and no file was written.
**Isolation:** I did not read the Codex folders, LIVE_TEAM_STATUS.md, LIVE_EVENTS.log or the other agent's memory.
**Labels:** **[PE]** = PROJECT EVIDENCE, **[ER]** = EXTERNAL RESEARCH, **[AI]** = AGENT INFERENCE.

---

## 1. Summary fields

| Field | Value |
|---|---|
| Study window | WINDOW2 |
| Authoritative entity | `crypto_ai.paper_position` |
| Selection | `status=CLOSED AND closed_at IS NOT NULL AND realized_pnl < 0 ORDER BY closed_at DESC LIMIT 6` |
| Frozen trade IDs | 182, 172, 162, 158, 143, 137 (none substituted) |
| Charter snapshot | UTC 2026-10-30 12:24:16 / "KSA" 2026-10-29 20:47:57. **These two values contradict each other** [PE]. DBQ-FTS-005 ran at UTC 2026-10-30 12:24:16, which is KSA 15:24:16 (I3-F01). The charter's KSA value is not UTC+3 of its UTC value, and the drift note is recorded. |
| Time basis | The database stores UTC. The result files show **KSA** values because the query session time zone was +03:00, proven by epoch check I3-001 / I4-F01 [PE]. Below, displayed DB values are labelled KSA, and UTC is KSA − 3 h. My iteration 1 "KSA" column was converted twice and is void. |
| Total frozen P&L | −0.273 USDT (paper; `fee_usdt = 0`) [PE] |
| Confirmed logic defects | **1**: trade 143, Defect A (an outdated 5m signal was reused at entry) |
| Confirmed parity defect (mechanism) | **1**: wall-clock vs candle-close opportunity age. Affects trade 162; the Replay outcome is unproven. |
| Valid losses under the coded rules | 137, 158, 162 (Production rules), 172, 182 |
| Stale-data cause | NONE found in any of the six |
| Execution-delay cause | NONE found. Fills came 1.4–2.4 s after the signal, using the newest tick. |
| Logs searched | NONE. No SSH access, and the local logs are compressed. |
| Replay executed | NO (analysis only) |
| Scope warning | The selection excludes all 75 `STOPPED` losses (−15.886 USDT), roughly 58× the frozen loss. |

---

## 2. Mandatory per-trade table

Times are paper-row open → close, with KSA first and UTC in brackets. Wallet fills are 1.2–3.2 s earlier (I3-F06).

| ID | Symbol | Open → close, KSA [UTC] | Entry → exit price | P&L (USDT) | Exit reason | Entry route | Planned R/R | Entry Quality | Classification | Primary finding | Replay reproducible? | Suggested fix candidate |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **137** | EGLDUSDT | 2026-10-11 20:55:04.510 → 21:00:12.122 [17:55:04 → 18:00:12] | 3.999 → 3.987 | −0.075019 | SELL_CONFIRMED | IMMEDIATE_VALIDATION | 0.644 | 58 | **VALID LOSS** under the coded rules | One-candle reversal after about 10 SELL minutes. The range guard vetoed #156695 one bar earlier, then passed #156723 at a higher price through the expansion exception (score 91 ≥ 90). Design observation only. | Yes (5m inputs did not change the decision) [AI] | **NO CHANGE** to this trade's rules. Design candidate C3 needs population evidence first. |
| **143** | EGLDUSDT | 2026-10-11 22:40:04.858 → 22:43:36.229 [19:40:04 → 19:43:36] | 4.033 → 4.027 | −0.037193 | PROFIT_LOCK_HARD_EXIT | SETUP_TIMEFRAME_ATR | 1.50 | 60 at approval, 70 at fill | **LOGIC DEFECT at entry (Defect A)**; exit valid | The 5m lookup `latestAtOrBefore` excluded the new 5m #159407 (WATCH, `atr_immediate_entry_allowed=0`). It had been saved 175 ms before the BUY but generated 2 ms after the 1m signal, so the route used the replaced 5m #159285 (BUY). The exit came from a real one-second Binance sweep (4.100 → 4.025). | Entry: **most likely no**, because Replay orders 5m before 1m [AI, code-backed]. Exit: yes. | **C1**: check how old the 5m is and where it came from before a SETUP_TIMEFRAME_ATR approval. |
| **158** | XRPUSDT | 2026-10-12 02:35:04.811 → 03:30:06.638 [10-11 23:35:04 → 10-12 00:30:06] | 1.4108 → 1.4090 | −0.031897 | SELL_CONFIRMED | SETUP_CONFIRMATION_WAKEUP | 0.327 (0.346 at fill) | 50 | **VALID LOSS** | Low-ATR stop floor: stop 0.50%, target 0.1636%. The target is below a real 0.2% round-trip fee, so this matters for live trading only. | Yes [AI] | **NO CHANGE** in paper trading. C4 (target-vs-fee check) applies before any live trading. |
| **162** | DOGEUSDT | 2026-10-12 04:02:06.415 → 04:26:04.116 [01:02:06 → 01:26:04] | 0.08705 → 0.08675 | −0.051694 | STRONG_SELL | ACCUMULATED_EVIDENCE | 0.438 | 50 | **VALID under Production rules + PARITY DEFECT (mechanism confirmed)** | Opportunity age was 20 min 59.976 s, which `toMinutes()` rounds down to 20, so no penalty. Replay timing at candle close gives 21 min, a −5 penalty and EQ 45 < 50, which would block if Replay forms the same opportunity. | **UNPROVEN.** I5-004 and I7-003 both returned empty files. | **C2**: compute opportunity age from candle close times in both Production and Replay. |
| **172** | SHIBUSDT | 2026-10-12 06:36:05.087 → 07:15:45.484 [03:36:05 → 04:15:45] | 0.00000549 → 0.00000548 | −0.045537 | HTF_SELL_CONFIRMED | IMMEDIATE_VALIDATION | 0.744 | 60 | **VALID LOSS** under the coded rules | The range guard vetoed #171462 and #171484 (RANGE_MEAN_REVERSION, at 94.5% and 90.3% of the Bollinger range). #171589 at the same price was relabelled TREND_FOLLOWING, which bypassed the guard. The 5m check reads only the final label: the 5m BUY #171559 had `final_entry_allowed=0` and still counted. | Yes [AI] | **NO CHANGE** without population evidence. Design candidates C3 and C5. |
| **182** | ACEUSDT | 2026-10-12 09:57:06.476 → 10:05:20.486 [06:57:06 → 07:05:20] | 0.1884 → 0.1880 | −0.031847 | SELL_CONFIRMED | IMMEDIATE_VALIDATION | 0.482 | 50 (38 before the FIX-091 clamp) | **VALID LOSS** (weakest entry) | Counter-trend: 17 STRONG_SELL signals in a row, WEAK_DOWNTREND. The 5m #176655 was originally STRONG_SELL, demoted to NEUTRAL, and counted as acceptable. Price never went above entry. | Yes [AI] | **NO CHANGE** without population evidence. Design candidate C5. |

---

## 3. Final findings (iteration 8 self-review)

**I8-F01: what causes these six losses (final).** Only trade 143 has a decisive logic defect. Trade 162 has a code-confirmed parity mechanism. The other four are valid losses where coded rules were followed and the market reversed. [PE]

**I8-F02: correction to I7-F02. The Entry Quality inversion is only partly explained by ATR% / reward-risk confounding** [PE: DBQ-CLAUDE-I7-002].

| R/R band | EQ band | n | Losses | Stopped | P&L (USDT) |
|---|---|---|---|---|---|
| < 1 | 50–54 | 47 | 20 (43%) | 10 | −0.254 |
| < 1 | 55–69 | 19 | 7 (37%) | 2 | +0.109 |
| < 1 | 70+ | 3 | 1 | 0 | +0.025 |
| ≥ 1 | 50–54 | 14 | 5 (36%) | 4 | **+0.941** |
| ≥ 1 | 55–69 | 38 | 21 (55%) | 19 | −1.392 |
| ≥ 1 | 70+ | 72 | 47 (65%) | 40 | **−3.599** |

- Below R/R 1, a higher score does go with fewer losses, but the groups are tiny.
- At R/R 1 and above, the inversion **persists**: 70+ loses 65% of the time, versus 36% for 50–54. So confounding does not fully explain it.
- [AI] In this period, a high score at R/R ≥ 1 coincided with stop-outs. These are outside the frozen sample.

**I8-F03: fees answer U7-01** [PE: DBQ-CLAUDE-I7-001 notional; ER: 0.10% per side, from secondary sources only].

| R/R band | n | Paper P&L | Notional | After a 0.2% round trip | Per trade after fees |
|---|---|---|---|---|---|
| < 0.75 | 54 | +0.234 | 1215 | −2.196 | −0.041 |
| 0.75–1 | 15 | −0.354 | 345 | −1.044 | −0.070 |
| 1–1.5 | 61 | +0.079 | 1904 | −3.729 | −0.061 |
| ≥ 1.5 | 63 | −4.130 | 2731 | −9.592 | **−0.152** |

- Every band turns negative after fees.
- The low-R/R band is still the **least bad** per trade. A blunt reward/risk gate would therefore remove the best-performing group in paper trading, while the worst losses sit in the high-R/R, high-EQ, stopped group.
- I6-F01 stays demoted. It is a fee-margin risk (for example, trade 158's target of 0.164% is below 0.2%), not a cause of the paper losses.

**I8-F04: U7-03 remains open.** DBQ-CLAUDE-I7-003 returned an **empty file** with no header, like I5-004. I cannot tell zero Replay rows from a query failure. Whether Replay would have blocked trade 162 stays UNPROVEN. [PE of absence]

**I8-F05: the entry-route spread holds** (I7-F03). Every route with more than five trades is net negative before fees. No route-specific rule observed on 137, 172 or 182 is shown to drive population losses. [PE: I6-002]

---

## 4. Ranked root causes (frozen)

| Rank | Item | Type | Trades | Strength |
|---|---|---|---|---|
| 1 | Defect A: replaced 5m used by `latestAtOrBefore` at the 5-minute boundary | **Logic defect** (+ Production/Replay ordering gap) | 143 | Confirmed and decisive [PE] |
| 2 | Opportunity age uses wall-clock time and rounds down with `toMinutes()` | **Parity defect** | 162 (also TAO 35305 / paper 127) | Mechanism code-confirmed; Replay outcome unproven |
| 3 | 5m check reads the final label only; range guard depends on the strategy label; expansion exception | Design observations | 137, 172, 182 | Per-trade code + data; not population-validated |
| 4 | Entry Quality does not predict outcomes at R/R ≥ 1 | Design | Population | [PE] I7-002 |
| 5 | 0.50% stop floor + 3-ATR target, so R/R = 6 × ATR% | Configuration | 137, 158, 162, 172, 182 | Mechanism exact (I6-004); **not** a paper loss cause; a fee-margin risk |
| — | Defect B: no evidence boundary after a profit-lock exit | Intent mismatch | 143 | Confirmed, **not decisive** (I6-F03) |

---

## 5. DATABASE EVIDENCE

All queries were read-only, run by the coordinator, with results in `db/` and `db/CLAUDE/`.

| QueryId | What it proved |
|---|---|
| DBQ-FTS-001 … 005 | Frozen selection. FTS-002: 118 CLOSED (26 negative) and 75 STOPPED (75 negative). |
| DBQ-CLAUDE-I1-001 | The six `paper_position` rows: prices, quantity, P&L, close reason, `signal_id`, and the entry/exit reasons quoted above |
| DBQ-CLAUDE-I1-002 | Column types: `timestamp` vs `datetime` |
| DBQ-CLAUDE-I1-004 / -005 / -008 | Wallet fills, `fee_usdt = 0`, stops, price ticks |
| DBQ-CLAUDE-I2-001 / -002 / -003 | Generated/saved order of 1m and 5m signals (143: #159408, #159407, #159285); opportunity #36524 `five_minute_decision=BUY` |
| DBQ-CLAUDE-I2-004 / -005 | EGLD candles and ticks; no ingestion gap |
| DBQ-CLAUDE-I2-006 | 86 boundary BUYs; about 40 of 45 used the previous 5m |
| DBQ-CLAUDE-I3-001 | Session time zone is +03:00. Epoch 1791747601.634 = 2026-10-11 19:40:01.634 UTC = 22:40:01.634 KSA. |
| DBQ-CLAUDE-I5-001 / -002 / -003 | Entry Quality inputs, liquidity/regime, ATR% |
| DBQ-CLAUDE-I5-004 | **EMPTY FILE**, unresolved |
| DBQ-CLAUDE-I5-005 / -006 | Population of 193; 8 age-boundary opportunities (14674, 24105, 35305, 37952, …) |
| DBQ-CLAUDE-I6-001 / -002 | Outcomes by R/R band and by entry route |
| DBQ-CLAUDE-I6-003 | `fix122_evaluation` has **0** Replay rows; Replay table schemas |
| DBQ-CLAUDE-I6-004 | Stored plans: stop 0.5000% (143: 0.5406%); `risk_reward_ratio` = 6 × ATR% for five trades |
| DBQ-CLAUDE-I7-001 | `paper_position` schema; notional and fee model by R/R band |
| DBQ-CLAUDE-I7-002 | Entry Quality × R/R outcome grid |
| DBQ-CLAUDE-I7-003 | **EMPTY FILE**: Replay coverage for DOGE 162 is unknown |

No new DB requests this iteration (final).

---

## 6. LOG EVIDENCE

**NONE.** No log was searched in any iteration [PE of absence, I3-F07]: there is no SSH access, the local `logs/crypto-ai-trader.2026-10-12.*.log.gz` files are compressed, and I have no decompression tool. No claim in this study depends on logs.

Searches still outstanding:
- `FIX-11G transitional BUY authority granted` for signals 156723 and 176710
- `[FIX-153][PENDING_REPLACED]`, `LANE_OVERFLOW`, `PRICE_NOT_CANONICAL` and `ANALYSIS_TASK_FAILED` for EGLDUSDT, 2026-10-11 22:40 / 22:43 KSA (19:40 / 19:43 UTC)
- WebSocket reconnect lines from 2026-10-11 20:50 to 2026-10-12 10:10 KSA

---

## 7. CODE EVIDENCE

Current tree, re-checked this iteration. The build that actually ran on 10-11/12 is not proven (U3-03).

- `AnalysisService.java:150`: `buildSignal(snapshot, Instant.now(), …)`, so Production signal time is the wall clock. ✔ re-verified
- `RegressionTestWorker.java:584`: `evaluationTime = candle.openTime + interval`, so Replay signal time is the candle close. ✔ re-verified. Interval order 1h → 5m → 1m at `:557-560, 787-793`.
- `ExecutionIntelligenceService.java:950`: opportunity age is `Duration.between(startedAt, current.generatedAt).toMinutes()`, which rounds down. ✔ re-verified. The penalty starts above 20 minutes (`:987-989`); `startedAt = signal.generatedAt` (`:2541`).
- `ExecutionIntelligenceService.java:1485`: the SETUP_TIMEFRAME_ATR route looks up the 5m with `latestAtOrBefore(current, …)` (✔). `:2455`: the lookup is bounded by `generatedAt ≤ 1m.generatedAt` and has no lineage filter. `:1486-1487`: `isBullish` and the ATR-immediate checks that would have rejected #159407.
- `ExecutionIntelligenceService.java:73-74, 2214-2219`: a WATCH signal with score and confidence ≥ 60 counts as supportive (Defect B not decisive).
- `CandleAnalysisDispatcher.java:34-35, 102-110`: each symbol and interval has its own lane, with no 1m/5m ordering in Production.
- `TradeExecutionValidationService.java:101-107, 218-221, 263-283, 300-317`: the 5m check reads only the decision enum (172, 182).
- `AtrRiskService.java:35-45`: `stopDistance = max(2·ATR, minStop%·price)` and `rewardDistance = 3·ATR` with no floor; R/R = reward / stop. ✔ re-verified. `application.yml:137-143` sets multipliers 2.0 / 3.0 and minimum stop 0.50%.
- `ExecutionPriceAuthorityService.java:25, 46-48`; `PaperTradingService.java:342-343, 412`; `LivePositionProtectionService.java:405`: fill freshness and the paper-row time lag.

---

## 8. EXTERNAL RESEARCH

- **Binance public market data** (`data-api.binance.vision` klines and aggTrades, read-only, read through a summarising fetch tool):
  - Stored candles match Binance exactly when read as KSA.
  - EGLD had no trades for 7 s, then 86 trades from 4.097 to 4.025 in one second, at 2026-10-11 19:43:34 UTC (22:43:34 KSA).
  - These figures still need a raw re-pull.
- **Binance spot fee:** 0.10% per side at VIP 0, from **secondary** sources only.
- **Binance kline stream:** 2000 ms update speed (official WebSocket docs).

---

## 9. UNKNOWNS (final)

- **U-01:** Whether Replay forms trade 162's opportunity from the same start candle. I5-004 and I7-003 were both empty.
- **U-02:** Why the I5-004 and I7-003 result files are empty: zero rows, or a failed run.
- **U-03:** Which build and migrations were running on 2026-10-11/12.
- **U-04:** All log evidence (section 6).
- **U-05:** An official Binance fee source (only secondary sources so far).

**Open contradictions** for dual-agent review only; I edited no shared document:
- FIX-154 is active in Production, contrary to AGENTS.md (I4-F08).
- `DATABASE_INVESTIGATION_RULES.md:35` says raw timestamps are UTC, but the result files display KSA because the session was +03:00.
- The charter's KSA snapshot value is inconsistent with its UTC value.

---

## 10. Suggested fixes: CANDIDATES ONLY, not authorised

| ID | Candidate | Trades | Regression check |
|---|---|---|---|
| C1 | Before a SETUP_TIMEFRAME_ATR approval, require the 5m signal for the latest **closed** 5m candle, or wait for it, instead of `generatedAt ≤ 1m.generatedAt` | 143 | Replay or unit-test the 143 race (new 5m WATCH generated 2 ms after the 1m) and expect a rejection |
| C2 | Compute opportunity age from candle open/close times, the same way in Production and Replay | 162 | An age of 20 min 59.976 s must count as 21 minutes; Replay DOGE over 2026-10-12 00:35–01:05 UTC (03:35–04:05 KSA) |
| C3 | Apply the range-location guard regardless of strategy label | 172 (137) | Population back-test before adoption |
| C4 | Before live trading, require the target distance to exceed the round-trip fee | 158 | A fee-aware model on the I7-001 population |
| C5 | Make the 5m check consult `final_entry_allowed` / `original_decision` | 172, 182 | Population back-test |
| — | **NO CHANGE** to the stop floor or a reward/risk gate for now | 137, 158, 162, 172, 182 | I8-F03 shows the low-R/R group is the least-bad group |

---

## 11. Self-review (iteration 8)

- **Frozen set:** unchanged with no substitutions; one table row per ID.
- **Corrections this iteration:**
  - I7-F02 is narrowed. Confounding does not explain the Entry Quality inversion at R/R ≥ 1 (I7-002).
  - U7-01 is closed (I8-F03).
  - U7-03 is still open: an empty result again.
- **Labels:**
  - **PROJECT EVIDENCE:** DB results and code lines.
  - **EXTERNAL RESEARCH:** Binance data and fees.
  - **AGENT INFERENCE:** the Replay reproducibility calls, the 143 counterfactual, and the fee-model interpretation.
- **Not claimed:** any log-based finding, Replay execution, or deployment-readiness.

**FROZEN. CLAUDE, WINDOW2, iteration 8.**


## SECTION 3 - COORDINATOR COMMON FINDINGS

The Coordinator is a comparator, not a third trading analyst. It does not choose a winner.

Both agents were required to analyze the same frozen IDs: 182, 172, 162, 158, 143, 137

Read sections 1 and 2 for root causes, entry verdicts, proof IDs, and suggested fixes.
Agreement is FULL only when both primary classifications match; otherwise STRONG/PARTIAL/NONE as stated by the two frozen reports. The Coordinator does not invent a third classification.

CANDIDATE IMPROVEMENT - NOT APPROVED. No implementation.
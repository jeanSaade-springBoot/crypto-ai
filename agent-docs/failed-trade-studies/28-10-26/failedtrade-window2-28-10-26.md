# DAILY FAILED-TRADE INDEPENDENT FORENSIC STUDY

Window: WINDOW2
Run: fts-WINDOW2-20261028-180002
Implementation authorization: NOT AUTHORIZED
Suggested fixes are candidates only and were not implemented.

STUDY WINDOW: WINDOW2
SNAPSHOT TIME UTC: 2026-10-28 15:00:05
SNAPSHOT TIME KSA: 2026-10-28 18:00:02
INTENDED WINDOW CLOCK KSA: 2026-10-28 18:00:00
SNAPSHOT NOTE: On-schedule snapshot.
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

## WINDOW2 — Final Classification (Iteration 8 of 8)

### Summary

| Field | Value |
|---|---|
| Scope | Frozen `crypto_ai.paper_position` IDs only: `182, 172, 162, 158, 143, 137` |
| Selection | Charter-defined: closed, non-null `closed_at`, negative `realized_pnl`, newest six |
| Snapshot UTC | `2026-10-28 15:00:05` |
| Snapshot KSA supplied by charter | `2026-10-28 18:00:02` |
| Runtime environment/version/mode | Unverified |
| Final classification | **INSUFFICIENT EVIDENCE — no per-trade causal classification is supportable** |
| DB proof results | Not available in permitted `db/CODEX/` evidence folder |
| Replay | Not run; parity cannot be assessed |
| Changes made | None — analysis only |

The supplied snapshot fields have a three-second arithmetic inconsistency: `2026-10-28 15:00:05 UTC` converts to `2026-10-28 18:00:05 KSA`, not the charter’s supplied `18:00:02 KSA`. This does not alter the frozen cohort because its selection SQL has no snapshot-time predicate.

## Mandatory frozen-trade table

| Paper position ID | Frozen-cohort basis | Final classification | Entry assessment | Exit / protection assessment | P&L reconciliation | Confidence |
|---:|---|---|---|---|---|---|
| 182 | Charter-selected negative closed position; not independently DB-verified | Insufficient evidence | Unverifiable | Unverifiable | Unverifiable | High that evidence is insufficient |
| 172 | Charter-selected negative closed position; not independently DB-verified | Insufficient evidence | Unverifiable | Unverifiable | Unverifiable | High that evidence is insufficient |
| 162 | Charter-selected negative closed position; not independently DB-verified | Insufficient evidence | Unverifiable | Unverifiable | Unverifiable | High that evidence is insufficient |
| 158 | Charter-selected negative closed position; not independently DB-verified | Insufficient evidence | Unverifiable | Unverifiable | Unverifiable | High that evidence is insufficient |
| 143 | Charter-selected negative closed position; not independently DB-verified | Insufficient evidence | Unverifiable | Unverifiable | Unverifiable | High that evidence is insufficient |
| 137 | Charter-selected negative closed position; not independently DB-verified | Insufficient evidence | Unverifiable | Unverifiable | Unverifiable | High that evidence is insufficient |

No symbol, side, quantity, entry/exit timestamp, price, realized-P&L amount, close reason, signal link, wallet execution, or accepted protection observation is available for any frozen ID. Therefore none can be classified as a valid strategy loss, stale entry, stop-loss/protection failure, execution failure, accounting mismatch, or logic defect.

## Database evidence

### PROJECT EVIDENCE

- The authoritative entity and selection rule are supplied by the WINDOW2 charter.
- The permitted directory `agent-docs/runs/fts-WINDOW2-20261028-180002/db/CODEX/` is empty.
- No results for `DBQ-FTS-001` through `DBQ-FTS-005` are available to this Codex track.
- No actual schema result is available. Table columns and joins remain unverified.
- Consequently, the charter’s cohort is preserved as frozen scope but has not been independently re-proven from `crypto_ai.paper_position`.

### Required missing DB evidence

For every frozen ID:

1. `paper_position` lifecycle and entry/exit linkage.
2. All related wallet BUY/SELL executions and fees.
3. Entry and exit `trade_signal` rows plus processing-work status.
4. Managed-position protection state and exit audit, if present.
5. Accepted `market_price_event` observations around the exit.
6. Candle/indicator context only after the linked signal and timestamps are known.

## Log evidence

### PROJECT EVIDENCE

- The locally retained active application-log fragment previously inspected covered only `2026-10-19 14:32:18.902` through `14:32:36.235`; its timestamp timezone was not stated.
- That fragment had no match for the frozen IDs in position-ID-style fields.
- Available compressed local application archives were dated 12–17 October; no local 28 October application log was available.
- This absence does **not** establish that no relevant runtime events occurred.
- Current source persists `fix129_stage_timing` only for failed or at-least-one-second stages; it is asynchronous and may be dropped. Absence of a timing record cannot prove non-execution or a fast execution.

### AGENT INFERENCE

A broad log search without first establishing each trade’s symbol, signal IDs, and UTC lifecycle interval would risk falsely attributing unrelated events. No such attribution was made.

## Code evidence

### PROJECT EVIDENCE

- Current source maps a paper position to lifecycle, stop/target, realized P&L, close reason, and entry/exit-signal evidence. Current source is an evidence map only; it does not prove the historical deployed behavior for these trades. [PaperPosition.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/domain/PaperPosition.java)
- Current BUY flow revalidates an entry against a fresh canonical execution price before wallet persistence. A negative realized P&L alone is therefore not proof of stale or poor entry quality. [PaperTradingService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/service/PaperTradingService.java)
- Current signal-path close code can persist `paper_position` before attempting wallet exit and catches wallet exceptions. Thus a closed paper position alone cannot prove a corresponding wallet SELL committed on that route. [PaperTradingService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/service/PaperTradingService.java)
- Current mechanical protection calls wallet mechanical exit before closing the paper position. This is stronger evidence if the actual records identify that path, but it still requires the wallet execution and accepted price-event evidence. [LivePositionProtectionService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/position/service/LivePositionProtectionService.java)
- Current price-event consumers accept `LEGACY` or `APPLIED` evidence, but such a row alone does not link a protection decision to a particular paper position. Attribution requires symbol/time/open-position and committed-exit evidence. [MarketPriceEventService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/market/service/MarketPriceEventService.java)
- Candle OHLC must not be substituted for absent intra-candle protection evidence. [TRADE_INVESTIGATION_AGENT.md](/C:/workspace/crypto-ai-trader-mysql/codex-docs/TRADE_INVESTIGATION_AGENT.md)

### EXTERNAL RESEARCH

- Prior iteration review noted Binance kline payloads provide event/start/close times and a closed-candle flag. This supports using persisted observation evidence for protection timing; it establishes no fact about any frozen trade. [Binance WebSocket kline documentation](https://developers.binance.com/en/docs/catalog/core-trading-spot-trading/api/ws-streams/)

### AGENT INFERENCE

Current source cannot establish the historical application version, configuration, runtime mode, or route taken by any selected trade. It is therefore not valid to attribute any project-wide known issue to this cohort.

## Final findings

1. **What happened:** the charter defines six closed negative-P&L paper positions, but no durable row evidence is available in this track to reconstruct any one of them.
2. **Why the system entered and exited:** unknown for all six IDs.
3. **Whether behavior followed intended rules:** unverifiable for all six IDs.
4. **Confirmed defect:** none attributable to a frozen trade.
5. **Confirmed missing evidence:** all trade-specific DB results, historical runtime/version evidence, relevant logs, and Replay evidence.
6. **Suggested-fix candidates only:** no product/code/configuration fix is justified. The evidence-collection process should provide schema, lifecycle, wallet, signal, and accepted-price-event query outputs before another causal classification attempt.

No code, configuration, database, deployment, or documentation change was made.

### DB_REQUEST

QueryId: DBQ-CODEX-I8-001  
Purpose: Obtain schema and declared key relationships before issuing any trade-specific reconstruction query for frozen IDs `182, 172, 162, 158, 143, 137`.  
SQL:

```sql
SELECT
    c.table_name,
    c.ordinal_position,
    c.column_name,
    c.column_type,
    c.is_nullable,
    c.column_key,
    k.constraint_name,
    k.referenced_table_name,
    k.referenced_column_name
FROM information_schema.columns AS c
LEFT JOIN information_schema.key_column_usage AS k
    ON k.table_schema = c.table_schema
   AND k.table_name = c.table_name
   AND k.column_name = c.column_name
WHERE c.table_schema = 'crypto_ai'
  AND c.table_name IN (
      'paper_position',
      'trade_signal',
      'wallet_trade',
      'wallet_managed_position',
      'signal_processing_work',
      'market_price_event',
      'production_exit_audit',
      'fix129_stage_timing'
  )
ORDER BY c.table_name, c.ordinal_position, k.constraint_name;
```

### END_DB_REQUEST


## SECTION 2 - CLAUDE ANALYSIS

# CLAUDE_FAILED_TRADE_STUDY.md: FTS WINDOW2, iteration 8 of 8 (final, frozen)

## Summary

None of the six losing trades is a confirmed defect. Two are candidate defects, and the arithmetic shows each one decided its trade:

- **143 (CD-1):** the trade was approved on a 5-minute BUY that had already been replaced by a 5-minute WATCH. With the WATCH visible, no entry route in the code would have opened it.
- **162 (CD-2):** the opportunity-age penalty was avoided by 24 ms of processing delay. If age is measured from candle close, the trade is blocked.

The other four losses (137, 158, 172, 182) are valid losses under the coded rules. They come from the entry policy, which lets in setups with very little edge.

| Field | Value |
|---|---|
| Agent / track | CLAUDE, independent track. Self-review only, no cross-review. |
| Run | `fts-WINDOW2-20261028-180002` |
| Study window | WINDOW2 |
| Snapshot | 2026-10-28 15:00:05 UTC → 2026-10-28 18:00:05 KSA. The run records 18:00:02 KSA; that 3 s difference is unexplained (U-11). Scheduled clock: 18:00:00 KSA, on schedule. |
| Authoritative entity | `crypto_ai.paper_position` |
| Selection | `status=CLOSED AND closed_at IS NOT NULL AND realized_pnl < 0 ORDER BY closed_at DESC LIMIT 6` |
| Frozen trade IDs | 182, 172, 162, 158, 143, 137. All six are covered and none was substituted. |
| Excluded | OPEN or incomplete rows, NULL `closed_at`, non-negative `realized_pnl`, `wallet_position_test` (Replay) |
| Proof queries | DBQ-FTS-001 .. DBQ-FTS-005 (coordinator) |
| Total realized loss | −0.27319 USDT across 6 trades. Per trade: −0.0319 to −0.0750, i.e. −0.13% to −0.34% of each position's cost |
| Stop-loss hits | 0 of 6 |
| Confirmed defects | **0** |
| Candidate defects | **2**: CD-1 (143), CD-2 (162) |
| Confirmed code inconsistency with no effect on these outcomes | **1**: CI-1, the entry-quality score is recalculated at fill with a different ATR, and the stored score is not the one that made the decision (143, 158) |
| Valid losing trades (policy and design) | 137, 158, 172, 182, plus the exit leg of 143 |
| Stale data / execution delay at entry | None. Every trade went from candle close to paper open in 4.5–6.5 s, with at most one price tick of slippage. |
| Log evidence | **None searched.** This is an evidence gap (see LOG EVIDENCE). |
| Implementation | **NO CHANGE.** Analysis only. Every fix below is a candidate only. |
| Writes | None. This file exists only as stdout because the session is read-only. |

**How times are shown:** every time comes from a `UNIX_TIMESTAMP` epoch and is shown as UTC → KSA (UTC+3).

**Database clock warning (PROJECT EVIDENCE, DBQ-CLAUDE-I2-001):** the query wrapper's MySQL session runs in `SYSTEM` = Arab Standard Time (offset 10800 s). Raw DATETIME values shown by the wrapper are therefore **KSA, not UTC**. This contradicts `DATABASE_INVESTIGATION_RULES.md` ("raw timestamps are UTC") for wrapper output. I am reporting the contradiction and have not edited the shared document.

---

## Mandatory table: one row per frozen trade

| ID | Symbol | Open (UTC → KSA) | Close (UTC → KSA) | Entry → exit price | Realized P&L, USDT (% of cost) | Entry route / signal | Exit reason | Entry score: decision / stored | Classification | Key evidence |
|---|---|---|---|---|---|---|---|---|---|---|
| **182** | ACEUSDT | 10-12 06:57:06.476 → 09:57:06 | 07:05:20.486 → 10:05:20 | 0.1884 → 0.1880 | −0.03185 (−0.212%) | IMMEDIATE_VALIDATION, `BALANCED_NEUTRAL_5M_WATCH_1H`. 1m BUY #176710, CONFLICT, WEAK_DOWNTREND, R:R 0.48, size 15% | SELL_CONFIRMED (5m STRONG_SELL #176934) | 50 / 50 | **VALID LOSING TRADE**: low-edge entry that sat on a scoring boundary | Distance from the reference price was 5.952 ATR, so it took −20 instead of −30. The rejection-zone penalty (−12) was absorbed by the score floor of 50. A 17-minute run of SELL signals came just before the opportunity opened. |
| **172** | SHIBUSDT | 10-12 03:36:05.087 → 06:36:05 | 04:15:45.484 → 07:15:45 | 0.00000549 → 0.00000548 | −0.04554 (−0.182%) | IMMEDIATE_VALIDATION, `BALANCED_STRONG`. 1m BUY #171589, CONFLICT, R:R 0.74, size 25% | HTF_SELL_CONFIRMED | 60 / 60 | **VALID LOSING TRADE**: the price geometry was quantised to whole price ticks | The ATR (0.0000000068) was smaller than one price tick (0.00000001). The loss was exactly one tick. |
| **162** | DOGEUSDT | 10-12 01:02:06.415 → 04:02:06 | 01:26:04.116 → 04:26:04 | 0.08705 → 0.08675 | −0.05169 (−0.345%) | ACCUMULATED_EVIDENCE. 1m WATCH #167668, opportunity 37952 (buy_count 1), R:R 0.44, size 15% | SIGNAL_SELL (1m STRONG_SELL #168277) | 50 / 50 | **CANDIDATE DEFECT CD-2.** Valid under the coded rule, but measuring age from candle close would have blocked it. | Age 1259.976 s → 20 min → no penalty. From candle close it is 1260 s → 21 min → −5 → score 45 → CHASE_ENTRY_BLOCKED. Margin: 24 ms. |
| **158** | XRPUSDT | 10-11 23:35:04.811 → 10-12 02:35:04 | 10-12 00:30:06.638 → 03:30:06 | 1.4108 → 1.4090 | −0.03190 (−0.128%) | SETUP_CONFIRMATION_WAKEUP. 1m WATCH #165412, 5m trigger BUY #165415, R:R 0.33, size 25% | SELL_CONFIRMED (5m SELL #166821) | **60 / 50** | **VALID LOSING TRADE**: no edge after fees (inference); passed the fill check at exactly 50 | The decision used the 5m ATR (1.44 ATR → 0). The fill check used the 1m ATR (3.38 ATR → −10). Opportunity age 53 min → −10. The target was 0.17% away, below an estimated ~0.2% round-trip fee. |
| **143** | EGLDUSDT | 10-11 19:40:04.858 → 22:40:04 | 19:43:36.229 → 22:43:36 | 4.033 → 4.027 | −0.03719 (−0.149%) | SETUP_TIMEFRAME_ATR. 1m WATCH #159408 (RSI 84.3, 1m ATR plan 0%) with 5m authority from BUY #159285, which was 300 s old. Re-entered 22 s after trade 142 exited. Size 25%. | PROFIT_LOCK_HARD_EXIT: price gapped from 4.100 to 4.027 in an 8 s tick gap | **60 / 70** | **CANDIDATE DEFECT CD-1** at entry; the exit was a valid gap loss | The same-boundary 5m WATCH #159407 was generated 2 ms after the 1m signal, so it was invisible to `latestAtOrBefore`. With it visible, the route returns nothing and the trade is rejected as `ATR_ENTRY_BLOCKED`. |
| **137** | EGLDUSDT | 10-11 17:55:04.510 → 20:55:04 | 18:00:12.122 → 21:00:12 | 3.999 → 3.987 | −0.07502 (−0.300%) | IMMEDIATE_VALIDATION, `BALANCED_NEUTRAL_5M_WATCH_1H`. 1m STRONG_BUY #156723, AGREEMENT, RANGE, R:R 0.64, size 25% | SELL_CONFIRMED (5m SELL #156852) | 58 / 58 | **VALID LOSING TRADE**: low-edge entry just below a rejected high | Score 58 = 100 − 30 (R:R) − 12 (rejected high 4.005, entry 0.15% below it) |

---

## FINDINGS (final)

### FC-1. Shared cause for 137, 158, 172, 182 (PROJECT EVIDENCE + AGENT INFERENCE)

**How these losses happen (PROJECT EVIDENCE):**
1. The four direct entry routes have no reward-to-risk (R:R) floor (`ExecutionIntelligenceService.java:628-694`; route table in iteration-4 R1).
2. The only guard is the entry-quality score, which blocks only below 50 (`:229`, `:1173`).
3. An R:R below 1 costs only 30 points (`:991`), so on its own it can never block an entry.
4. The signals' R:R was 0.33–0.74, and the targets were 0.17–0.37% away.
5. Price never reached the 70% level that arms the profit lock, so each trade was closed by a bearish signal exit (`PositionExitPolicy.java:20-50`).

**October context (PROJECT EVIDENCE, I5-003):** signal exits won 0 of 23 trades, for −1.27 USDT in total.

**Classification:** these trades followed the design; this is a policy issue, not a code defect.

**AGENT INFERENCE:** across the whole system, low R:R is **not** the main loss driver. In I4-003, trades with R:R < 1 won 58% and were roughly flat, while the R:R ≥ 1.5 group had October stop-loss losses of −10.19. The six frozen trades are not where most of the money was lost (scope note FC-7).

### FC-2 (CD-1). 143 was approved on a 5m BUY that the same-boundary 5m signal had already replaced (PROJECT EVIDENCE + AGENT INFERENCE)

**The two signals (PROJECT EVIDENCE):**
- 1m #159408 was generated at 19:40:01.633945 UTC (22:40:01 KSA).
- 5m #159407 (WATCH, `atr_immediate_entry_allowed=0`, plan 60%) was generated at 19:40:01.635944 UTC, which is 2 ms later, yet it has the lower id (I6-003, I7-002).

**What the code did:**
- `latestAtOrBefore` (`:2455-2462`) only accepts signals generated at or before the current one, so it chose 5m BUY #159285 instead. That signal was generated at 19:35:01.553 UTC (22:35:01 KSA), 300 s earlier, which is inside the 20-minute freshness limit.
- Opportunity 36524 saved `five_minute_decision=BUY`, so the outdated value is also what is stored (I6-001).

**What would have happened with #159407 visible (U-I7-4 is now closed):**
1. The setup route requires a bullish 5m signal (`:1486`), so it returns null.
2. `deferredContinuationDecision` would also return null. It needs a WATCH or bullish 1h (`:1751`), and the 1h was NEUTRAL (#158388, generated 1791745203; opportunity `one_hour_decision=NEUTRAL`). This does not depend on the 5m.
3. `higherTimeframeTransitionDecision` needs a fresh 1h BUY according to the code comment at `:608-611`, and the 1h was NEUTRAL. This is from the comment; I did not trace the method body.
4. The 1m signal has `atr_immediate_entry_allowed=0`, so the next check rejects with `ATR_ENTRY_BLOCKED` (`:644-648`).

**AGENT INFERENCE (strong):** 143 would not have opened.

**AGENT INFERENCE (moderate):** the lower id means #159407 was inserted first, so it was probably already in the database when 143 was decided. It was excluded only by the generation-time filter. Whether it had been committed by then is not proven (U-5).

**How often this happens (PROJECT EVIDENCE, I7-003):**
- 70 October closed positions had a 1m entry signal generated before the same-boundary 5m signal. Same-boundary ordering is routine.
- Of these, 8 had a 1m WATCH and a hidden 5m that was WATCH: positions 191, 163, 160, 143, 100, 42, 20 and 6. Their outcomes:
  - 4 losses: −0.8325 USDT, including 6 at −0.518 and 163 at −0.165.
  - 4 wins: +0.3488 USDT.
- **AGENT INFERENCE:** I did not check which entry route each of these used, so how much of this pattern actually caused trades is unproven (DBQ-CLAUDE-I8-001).

**Classification:** CANDIDATE DEFECT. The coded freshness rule was met, but the evidence used was already out of date. A dual-agent review is needed before it can be confirmed.

### FC-3 (CD-2). 162: whether the age penalty applied depended on processing delay (PROJECT EVIDENCE)

**The opening signal (U-I7-1 is now closed, I7-001):**
- Opportunity 37952 was opened by 1m WATCH #167123.
- Its candle closed at 00:41:00 UTC (03:41:00 KSA).
- The signal was generated at 00:41:02.836972 UTC, which equals `started_at` exactly.

**Age at the entry signal #167668:**
- That signal's candle closed at 01:02:00 UTC and it was generated at 01:02:02.813 UTC.
- Measured by generation times (the coded rule, `:907`, `:948-951`): 1259.976 s → `toMinutes()` = 20 → no penalty (`:987-989`).
- Measured by candle-close times: 1260 s → 21 min → −5 → score 45 → `CHASE_ENTRY_BLOCKED` at both the decision and the fill check.

**What the code comment claims:** FIX-105 (`:894-897`) calls this "candle-time evidence … never wall-clock". However, `generated_at` includes 1.3–14.75 s of analysis delay (I3-006).

**Classification:** CANDIDATE DEFECT. Production behaviour is not deterministic here, and the outcome hinged on a 24 ms margin. The parenthetical in the comment does say `startedAt -> generatedAt`, so whether this is a defect or intended behaviour needs a ruling on what was meant.

### FC-4 (CI-1). The entry-quality score is recalculated at fill with a different ATR (PROJECT EVIDENCE)

**What the code does:**
- At decision time, the setup and wake-up routes score entry quality with the **5m** ATR (`:634`, `:439`).
- `revalidateAtExecutionPrice` and the stored `entry_quality_score` use the **1m** ATR (`:857-858`; `PaperTradingService.java:377-378`).

**Effect on these trades:**
- **143:** decision 60 (6.61 ATR → −30), stored 70 (5.60 ATR → −20).
- **158:** decision 60 (1.44 ATR → 0), stored 50 (3.38 ATR → −10). The fill check passed with zero margin.

**Classification:** confirmed code inconsistency and audit gap. It did not change any outcome: sizing was 25% in both cases. It contradicts the stated intent in the comment at `:631-633`.

### FC-5. 143's exit was a valid gap loss (PROJECT EVIDENCE)

- The lock armed at 19:40:23 UTC (22:40:23 KSA).
- The take-profit was extended twice, exactly as coded at `LivePositionProtectionService.java:108-110`.
- The tick at 19:43:27.038 UTC was 4.100. The next tick, at 19:43:35.021 UTC (22:43:35 KSA), was 4.027, below both the lock level (4.0758) and the hard floor (4.0350).
- The wallet SELL followed 24 ms later.
- The EGLD-only silence (I3-002 showed other symbols still ticking) is most likely on Binance's side. This is unproven because there is no 10-11 log (U-3).
- Contributing gaps: no re-entry cooldown (22 s after 142) and no momentum input to entry quality (RSI 84.3).

### FC-6. Timing and data freshness: clean (PROJECT EVIDENCE)

- **Entries:** 4.5–6.5 s from candle close to paper open. Every signal was LIVE with `data_quality_valid=1`. Slippage was at most one tick.
- **Exits:** they fire on the first canonical price tick after the exit signal exists. Delay was 1.3–38.9 s, caused by sparse ticks rather than processing (I3-003). The P&L effect was 0 to −1 tick.

### FC-7. Fees and scope

**Fees (PROJECT EVIDENCE):** automatic trades set `fee_usdt=0` (`WalletAutoExecutionService.java:173,260,349,443,540,653`). The fee column exists in V28.

**Fees (EXTERNAL RESEARCH, secondary sources only):** Binance spot VIP-0 is about 0.1% per side.

**AGENT INFERENCE:** 158's 0.17% target and 172's one-tick target could not pay their fees even if hit, so paper P&L understates live losses.

**Scope note (PROJECT EVIDENCE, I5-003):** October net was about −4.17. Stop-loss exits account for −15.88 across 75 trades, all outside the frozen set.

---

## DATABASE EVIDENCE (PROJECT EVIDENCE)

| Query | Tables (queried successfully) | Used for |
|---|---|---|
| DBQ-FTS-001..005 (coordinator) | `paper_position` schema and selection proof | Frozen-set validity |
| DBQ-CLAUDE-I2-001 | session variables, `paper_position` | Database clock is KSA; open/close epochs for all six trades |
| DBQ-CLAUDE-I2-002 | `trade_signal` | Entry and exit signal prices, stop-loss/take-profit, R:R, ATR, plan, freshness, data quality |
| DBQ-CLAUDE-I2-003 | `wallet_trade` | Fills, routes, P&L, `fee_usdt=0` |
| DBQ-CLAUDE-I2-004..009 | `position_management_event`, `production_exit_audit`, `market_price_event`, `trade_signal`, `candle`, `paper_position` | Exit triggers, tick coverage, 143 ticks, trade 142 |
| DBQ-CLAUDE-I3-001..006 | `market_price_event`, `wallet_managed_position`, `wallet_snapshot`, `wallet_asset`, `trade_signal` | Latency, EGLD silence, tick between rule and exit, lock activation, analysis latency |
| DBQ-CLAUDE-I4-001..003 | `wallet_managed_position`, `trade_signal`, `paper_position` | Stored scores and allocation; confluence downgrade; outcomes by R:R |
| DBQ-CLAUDE-I5-001..004 | `wallet_trade` (messages), `execution_opportunity`, `paper_position` + `trade_signal`, `trade_signal` | Validation codes, opportunity evidence, October breakdown, oscillators |
| DBQ-CLAUDE-I6-001..003 | `execution_opportunity`, `trade_signal` | Opportunities 36524, 37621, 39062, 35535; 20-signal price windows; EGLD 5m signals |
| DBQ-CLAUDE-I7-001 | `trade_signal` | #167123 opened opportunity 37952 (CD-2) |
| DBQ-CLAUDE-I7-002 | `trade_signal` | #159407 WATCH and #159285 BUY ATR; #165415 ATR 0.0018787; EGLD and XRP 1h NEUTRAL |
| DBQ-CLAUDE-I7-003 | `paper_position`, `trade_signal` | Same-boundary ordering across 70 October positions (CD-1 frequency) |

**Key record IDs:**
- Signals: 156723, 159408, 159407, 159285, 165412, 165415, 167668, 167123, 168277, 171589, 176710, 176934, 166821, 156852.
- Opportunities: 35535, 36524, 37621, 37952, 39062, 40290.
- Wallet trades: 344/346, 358/359, 392/401, 403/407, 426/429, 451/454.

## LOG EVIDENCE

**None.** No log line was searched in any iteration.

- `logback-spring.xml:27-30`: logs are UTC-stamped and kept for 7 days (`maxHistory 7`).
- Only `.gz` archives exist locally for 10-12 and later. My tools cannot decompress them.
- **There is no 2026-10-11 archive**, so 137 and 143 have no log coverage.
- The iteration-3 LOG_REQUEST (FIX-153 `PRICE_NOT_CANONICAL`, reconnects, `Live signal-driven exit` for XRP, DOGE, SHIB and ACE) got no reply in my evidence folder.
- Effects: U-3 (the EGLD silence) cannot be proven, and the FIX-105 debug line (`:931`) that would show which reference price won is unavailable.

## CODE EVIDENCE (PROJECT EVIDENCE)

| Reference | What it shows |
|---|---|
| `ExecutionIntelligenceService.java:439-451` | The wake-up route scores the 1m signal with the 5m trigger's ATR (158) |
| `:598-606`, `:1725-1765` | Deferred continuation requires a WATCH or bullish 1h (`:1751`). This closes U-I7-4. |
| `:612-622` | The HTF transition route is evaluated before the setup-ATR route |
| `:628-641` | Setup-ATR route uses the 5m ATR for entry quality (143) |
| `:644-648` | `ATR_ENTRY_BLOCKED` fallback (143 counterfactual) |
| `:672-684` | FIX-041 applies to BALANCED_EARLY only |
| `:857-858` | The fill-time rescore uses the signal's own 1m ATR (CI-1) |
| `:871-892`, `:2601-2604` | Rolling reference price over the 20 most recent signals (`findTop20`) |
| `:894-926`, `:948-951` | Opportunity age = `startedAt` → `generatedAt` with `toMinutes()` truncation (CD-2) |
| `:976-1022` | Score bands; rejection penalty floored at 50 (`:995-1002`) |
| `:1173`, `:229` | Blocks only below 50 |
| `:1257-1280` | FIX-119 deferral lifted by one BUY (162) |
| `:1486`, `:2455-2462` | Setup route requires a bullish 5m; `latestAtOrBefore` filters by `generatedAt` (CD-1) |
| `PaperTradingService.java:328-385` | 0% ATR plan treated as 100%; stored score is the fill-time rescore |
| `PositionExitPolicy.java:20-50,95-97` | Signal exits; exit lookup has no age bound |
| `LivePositionProtectionService.java:75-244,108-110,210` | Tick-driven exits; take-profit extension; hard floor |
| `MarketPriceEventService.java:40-58` | Rejected price observations are never stored |
| `MultiTimeframeConfluenceService.java:120-138` | A BUY against a higher-timeframe SELL is kept; a SELL against a bullish higher timeframe becomes NEUTRAL |
| `WalletAutoExecutionService.java:173…653` | Automatic trades set `feeUsdt(ZERO)` |
| `ShadowProductionReplayService.java:300,335` | Replay shares entry quality and revalidation |

## EXTERNAL RESEARCH

- **Binance spot WebSocket:** 1m kline updates arrive every 2000 ms (developers.binance.com, Web Socket Streams).
- **Binance VIP-0 spot fees:** about 0.1% maker and taker, from secondary sources only (CryptoPotato, BitDegree, TradersUnion). Binance.US is excluded.

---

## Replay reproducibility (PROJECT EVIDENCE + AGENT INFERENCE)

- FC-1, FC-4, FC-5 and the 158, 172, 182 and 137 scores use shared code and stored prices. They should reproduce in Replay.
- CD-1 and CD-2 depend on the order and gaps of the stored `generated_at` values. They reproduce only if Replay uses the stored production timestamps (U-1, not verified).

## UNKNOWNS

- **U-1:** whether Replay uses stored `generated_at` values or regenerates them. This decides whether CD-1 and CD-2 reproduce.
- **U-2:** which entry route each of the 8 CD-1-pattern positions used (DBQ-CLAUDE-I8-001).
- **U-3:** whether the EGLD silence at 19:43 UTC was on Binance's side or the app's. Needs a 10-11 log, and none exists.
- **U-4:** the intent behind the 0.50% stop floor (U-New3), and whether "0% ATR plan becomes 100%" is intended (U-I5-3).
- **U-5:** whether #159407 had been committed when 143 was decided (lower id, later `generated_at`).
- **U-6:** which build was deployed on 10-11 and 10-12 (U8).
- **U-7:** whether `higherTimeframeTransitionDecision` needs a 1h BUY. This comes from the comment only; the method body was not traced.
- **U-8:** what the FIX-105 comment meant by "candle-time" (CD-2).
- **U-9:** the paper `close_reason` for 162. The wallet `execution_reason` is SIGNAL_SELL.
- **U-10:** whether any log lines exist for 158, 162, 172 or 182 (`.gz` files were not searched).
- **U-11:** the 3 s difference between the snapshot's UTC and KSA stamps.

## Candidate corrections (candidates only; NO CHANGE)

1. **CD-1:** make same-boundary higher-timeframe signals visible to the decision, either by ordering or by waiting for the boundary set. Regression test: 1m and 5m signals on the same boundary, in both orders.
2. **CD-2:** measure opportunity age from candle close, or document the current semantics. Regression test: an age of 1259.976 s vs 1260 s at the 20-minute boundary, in Production and Replay.
3. **CI-1:** recalculate at fill with the route's own ATR, and store both the decision-time and fill-time scores.
4. **Policy, for Jean:**
   - Require the target distance to exceed estimated round-trip fees plus one tick, and use a tick-aware ATR.
   - Apply one R:R floor across the direct routes, or block a score of exactly 50.
   - Let the rejection penalty go below the floor.
   - Add a momentum/overbought input to entry quality.
   - Add a re-entry cooldown.
5. **Regression checks:**
   - Replay 143 and 162 on the stored signals and assert the decision codes.
   - A unit test for the score-50 floor combined with a rejection zone.

## Follow-up database request (post-freeze; does not change the classifications above)

### DB_REQUEST
QueryId: DBQ-CLAUDE-I8-001
Purpose: Entry route for the 8 positions matching the CD-1 pattern (1m WATCH entry, later same-boundary 5m WATCH), to measure how often the outdated 5m BUY actually decided an entry (U-2)
SQL:
SELECT p.id AS position_id, p.symbol, p.signal_id, w.id AS wallet_trade_id, w.execution_reason,
       UNIX_TIMESTAMP(w.executed_at) AS exec_epoch, p.close_reason, p.realized_pnl
FROM paper_position p
JOIN wallet_trade w ON w.signal_id = p.signal_id AND w.side = 'BUY'
WHERE p.id IN (191,163,160,143,100,42,20,6)
ORDER BY p.id DESC
LIMIT 20;
### END_DB_REQUEST

## SELF-REVIEW (iteration 8, final)

**Coverage:** all 6 frozen trades have one table row and a classification. None was substituted.

**What iteration 8 resolved:**
- **U-I7-1 closed:** #167123 opened opportunity 37952; CD-2 arithmetic confirmed.
- **U-I7-2 closed:** 158's decision-time score was 60.
- **U-I7-4 closed:** deferred continuation is ruled out by the 1h NEUTRAL, which strengthens CD-1.

**Corrections to my earlier output:**
- Iteration 5 called the percentages "wallet loss %". They are `realized_pnl_percent` on the position's cost (0.128–0.345%), not a share of the wallet.
- 158's stored score of 50 is the fill-time rescore. Its decision-time score was 60.

**Defects:** 0 confirmed; 2 candidates (CD-1, CD-2); 1 confirmed code inconsistency with no outcome impact (CI-1).

**Labels:** every claim is tagged PROJECT EVIDENCE, EXTERNAL RESEARCH or AGENT INFERENCE.

**Evidence gaps:** no logs were searched. External fee figures come from secondary sources.

**Isolation:** I did not read any Codex folder, `LIVE_TEAM_STATUS.md`, `LIVE_EVENTS.log` or Codex memory.

**Status: FROZEN (Claude track, iteration 8 of 8).** Ready for the coordinator's comparison; nothing implemented.

Sources:
- [Binance Spot WebSocket Streams](https://developers.binance.com/docs/binance-spot-api-docs/web-socket-streams)
- [CryptoPotato – Binance fees](https://cryptopotato.com/binance-fees/)
- [BitDegree – Binance fees](https://www.bitdegree.org/crypto/tutorials/binance-fees)
- [TradersUnion – Binance fees](https://tradersunion.com/brokers/crypto/view/binance/fees/)


## SECTION 3 - COORDINATOR COMMON FINDINGS

The Coordinator is a comparator, not a third trading analyst. It does not choose a winner.

Both agents were required to analyze the same frozen IDs: 182, 172, 162, 158, 143, 137

Read sections 1 and 2 for root causes, entry verdicts, proof IDs, and suggested fixes.
Agreement is FULL only when both primary classifications match; otherwise STRONG/PARTIAL/NONE as stated by the two frozen reports. The Coordinator does not invent a third classification.

CANDIDATE IMPROVEMENT - NOT APPROVED. No implementation.
# DAILY FAILED-TRADE INDEPENDENT FORENSIC STUDY

Window: WINDOW2
Run: fts-WINDOW2-20261101-111736
Implementation authorization: NOT AUTHORIZED
Suggested fixes are candidates only and were not implemented.

STUDY WINDOW: WINDOW2
SNAPSHOT TIME UTC: 2026-11-01 08:17:41
SNAPSHOT TIME KSA: 2026-11-01 11:17:36
INTENDED WINDOW CLOCK KSA: 2026-11-01 18:00:00
SNAPSHOT NOTE: SNAPSHOT CLOCK DRIFT: intended 2026-11-01 18:00:00 KSA; actual 2026-11-01 11:17:36 KSA. Exact window clock was not preserved.
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

- Study window: `WINDOW2`
- Snapshot: UTC `2026-11-01 08:17:41`; KSA `2026-11-01 11:17:36`
- Intended window clock: KSA `2026-11-01 18:00:00` — not preserved; snapshot-clock drift recorded.
- Authoritative entity: `crypto_ai.paper_position`
- Frozen IDs: `182, 172, 162, 158, 143, 137`
- Final classification: **INSUFFICIENT_EVIDENCE / UNCLASSIFIABLE for all six**
- Confirmed trade-specific defects: `0`
- Valid-loss determinations: `0`
- Confirmed protection failures: `0`
- Database result artifacts available to CODEX: `0`
- Runtime application-log artifacts correlated to a frozen trade: `0`
- External research used in this final classification: `None`
- Changes made: `None` — analysis only.

The charter fixes these IDs as the selected closed negative-P&L positions, but the permitted CODEX DB-evidence folder contains no executed query result. Consequently, neither the selection nor any individual lifecycle fact can be independently proven here.

## Mandatory trade table

| Paper position ID | Frozen scope status | Trade-specific DB / log evidence | Final classification | Confidence |
|---:|---|---|---|---|
| 182 | Charter-frozen; not reselected | None available to CODEX | `INSUFFICIENT_EVIDENCE / UNCLASSIFIABLE` | High |
| 172 | Charter-frozen; not reselected | None available to CODEX | `INSUFFICIENT_EVIDENCE / UNCLASSIFIABLE` | High |
| 162 | Charter-frozen; not reselected | None available to CODEX | `INSUFFICIENT_EVIDENCE / UNCLASSIFIABLE` | High |
| 158 | Charter-frozen; not reselected | None available to CODEX | `INSUFFICIENT_EVIDENCE / UNCLASSIFIABLE` | High |
| 143 | Charter-frozen; not reselected | None available to CODEX | `INSUFFICIENT_EVIDENCE / UNCLASSIFIABLE` | High |
| 137 | Charter-frozen; not reselected | None available to CODEX | `INSUFFICIENT_EVIDENCE / UNCLASSIFIABLE` | High |

“High” expresses confidence in the evidence limitation, not confidence about each trade’s cause.

## Final findings

- **PROJECT EVIDENCE:** The frozen population is exactly the six IDs above. No substitute position, current re-selection, or Replay/test entity was used.
- **PROJECT EVIDENCE:** The permitted `db/CODEX/` directory has no result artifacts. Prior SQL text and DB requests are not executed database evidence.
- **PROJECT EVIDENCE:** No permitted runtime application log correlates a frozen position ID, symbol, signal, wallet execution, price event, or close.
- **AGENT INFERENCE:** It would be unsound to classify any loss as a valid stop, strategy defect, stale-data event, execution delay, protection failure, or accounting mismatch without the missing records.
- **EXTERNAL RESEARCH:** None used.

## Database evidence

No DBQ result was available for `DBQ-FTS-001` through `DBQ-FTS-005`, nor for prior CODEX requests. Therefore none of the following can be established for any frozen ID:

- symbol, entry and exit signals, UTC/KSA lifecycle timestamps;
- close reason, prices, quantity, fees, or realized-P&L reconciliation;
- signal freshness, data quality, risk/reward, BTC/HTF context, or entry approval;
- wallet/managed-position linkage or partial-exit/add history;
- accepted `market_price_event`, exit audit, or protection timing.

### DB_REQUEST

QueryId: DBQ-CODEX-I8-001  
Purpose: Obtain the minimum bounded, read-only evidence needed to replace the six unclassifiable outcomes with factual entry, exit, and P&L reconstruction. All returned timestamps must be interpreted as UTC.

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
      'trade_signal',
      'signal_processing_work',
      'wallet_trade',
      'wallet_managed_position',
      'production_exit_audit',
      'market_price_event'
  )
ORDER BY table_name, ordinal_position;

SELECT *
FROM paper_position
WHERE id IN (182, 172, 162, 158, 143, 137)
ORDER BY FIELD(id, 182, 172, 162, 158, 143, 137);

SELECT id, status, closed_at, realized_pnl
FROM paper_position
WHERE status = 'CLOSED'
  AND closed_at IS NOT NULL
  AND realized_pnl < 0
ORDER BY closed_at DESC
LIMIT 6;
```

### END_DB_REQUEST

## Log evidence

- **PROJECT EVIDENCE:** The permitted CODEX stderr traces contain agent/tooling cache and command errors, not trader runtime logs.
- **PROJECT EVIDENCE:** Those traces contain no reliable trade-level correlation for IDs `182, 172, 162, 158, 143, 137`.
- **AGENT INFERENCE:** No latency, outage, reconnect, stale-input, or protection claim can be derived from the available log material.

## Code evidence

This is current source-route evidence only; it does not establish the historical version or route for any frozen trade.

- **PROJECT EVIDENCE:** `PaperPosition` maps realized P&L, optional exit signal, and `closed_at`. [PaperPosition.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/domain/PaperPosition.java:10)
- **PROJECT EVIDENCE:** Current signal processing registers/claims work, applies business logic in a transaction, and records completion. [SignalProcessingCoordinator.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/execution/processing/SignalProcessingCoordinator.java:39)
- **PROJECT EVIDENCE:** The direct-price path records `PENDING` evidence and conditionally resolves it to `APPLIED` or `NON_CANONICAL`; unresolved records are separately handled. [MarketPriceEventService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/market/service/MarketPriceEventService.java:50)
- **PROJECT EVIDENCE:** Current live protection calls the mechanical wallet exit before its paper-position close for TP, stop-loss, profit-lock, and normal-exit branches. [LivePositionProtectionService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/position/service/LivePositionProtectionService.java:165)
- **PROJECT EVIDENCE:** A production exit audit is keyed to `paper_position_id` and can retain a wallet-position and source-signal reference. [ProductionExitAudit.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/audit/domain/ProductionExitAudit.java:29)

## Suggested correction candidates

No code or configuration correction is justified from this evidence set. The only justified next action is the bounded DB evidence request above, followed by per-trade reconciliation of position, signal, wallet, audit, and accepted price-event records. No change is authorized or proposed.


## SECTION 2 - CLAUDE ANALYSIS

# CLAUDE_FAILED_TRADE_STUDY.md — FTS WINDOW2, Iteration 8 (final, frozen)

## SUMMARY FIELDS

| Field | Value |
|---|---|
| Agent | CLAUDE (independent track, self-review only, no cross-review) |
| Run | `agent-docs/runs/fts-WINDOW2-20261101-111736` |
| Study window | WINDOW2 |
| Snapshot | 2026-11-01 08:17:41 UTC = **2026-11-01 11:17:41 KSA**. The charter says 11:17:36 KSA, which is a 5 s difference; I report it and do not resolve it. |
| Snapshot note | Clock drift: the window was meant for 18:00:00 KSA, but the snapshot ran at about 11:17 KSA. The exact window clock was not preserved. |
| Authoritative entity | `crypto_ai.paper_position` (Production). No Replay rows are included. |
| Selection | `status=CLOSED AND closed_at IS NOT NULL AND realized_pnl<0 ORDER BY closed_at DESC LIMIT 6` |
| Frozen trade IDs | 182, 172, 162, 158, 143, 137. All analyzed; none substituted. |
| Iteration | 8 of 8: final classification, frozen |
| Classification counts | Valid loss: 4 (137, 158, 162, 143\*). Logic defect: 2 (182 primary F15 plus F28; 172 F28 counterfactual). \*143 is a valid loss caused by a market gap, with timing-defect candidate F22 contributing. |
| Confirmed defects | **F15**: a relabelled bearish 5m is read as neutral. **F28**: the FIX-055 stop-exposure penalty is hidden by liquidity-status precedence. |
| Defect candidates | **F22**: 1m decisions at the 5-minute boundary read the previous bar's 5m. **F17**: ATR inconsistency at execution time. **F18**: Replay signal-visibility parity. |
| Rule questions (not defects) | F13 (target extension), F19 (longs in a downtrend regime), F21 (late-entry deferral skipped by one BUY), F23 (fee viability, live only) |
| Systemic finding | **F27**: the population's gross losses are concentrated in the ATR% ≥ 0.25 group (35 of 95 entries, 31% win rate, −0.150% gross average). |
| Stale data / execution delay | **None found** in any frozen trade (E4, E13) |
| Log evidence | **None available.** Production logs are unreachable and local `logs/` has 0 matches for 2026-10-11/12. |
| Replay verification | **Not verified.** The I7-005 result file is empty. Reproducibility is inferred from code only. |
| Fees | Paper P&L is **gross**: `fee_usdt=0` on automatic trades |
| Writes / git / deploy / code changes | NONE. No plan file was created because no Write or ExitPlanMode tool exists in this session. |
| Isolation | No Codex files, LIVE_TEAM_STATUS.md or LIVE_EVENTS.log were read |

Time convention: DB timestamps are UTC and are shown here as KSA (UTC+3) unless marked UTC. Exit times use `wallet_trade.executed_at`, because `paper_position.closed_at` lags it by 1.2–3.2 s on the live path (E7).

---

## MANDATORY TABLE (one row per frozen ID)

| id | Symbol | Opened KSA (paper) | Exit KSA (wallet SELL) | Entry → exit price | P&L USDT (gross) / % | close_reason | Entry route (`execution_reason`) | Primary cause | Contributing factors | Classification | Confidence | Replay reproducible? | Key evidence |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **182** | ACEUSDT | 10-12 12:57:06 | 10-12 13:05:18.504 | 0.1884 → 0.1880 | −0.031847 / −0.212% | SELL_CONFIRMED (1m SELL #176927) | IMMEDIATE_VALIDATION → BALANCED_NEUTRAL_5M_WATCH_1H | **F15**: 5m #176655 STRONG_SELL (score 25) was relabelled NEUTRAL. The 1m hard veto and the FIX-11G gate read the relabelled value. | **F28** (stop exposed under BULLISH_SUPPORT, so the −15 was skipped: 50 would have been 35); rejection floor cancelled a real rejection; F19 (WEAK_DOWNTREND); illiquid (0-trade candles); entry quality exactly 50; R:R 0.48 | **LOGIC DEFECT** (two independent misses) | High (F15), medium (F28 counterfactual) | Expected yes: deterministic from stored signals (inference) | E12, E16–E19, E28, E39, I3-001, I6-002, I7-001 |
| **172** | SHIBUSDT | 10-12 09:36:05 | 10-12 10:15:43.492 | 549e-8 → 548e-8 | −0.045537 / −0.182% | HTF_SELL_CONFIRMED (1h SELL #172587, 5m NEUTRAL #172588) | IMMEDIATE_VALIDATION (validateBuy) | Illiquid single-tick market (1 tick ≈ 0.18%). One-tick drop on the 1h SELL. | **F28**: `order_book_stop_exposed=1` under BULLISH_SUPPORT, so no −15 (60 would have been 45, below the cutoff of 50); confluence CONFLICT; 5m confidence 49; R:R 0.74 | **LOGIC DEFECT (F28, counterfactual block)**. Otherwise a valid loss under the code as written. | Medium | Expected yes (inference) | E21, E24, E32, E39, I6-002, I7-001 |
| **162** | DOGEUSDT | 10-12 07:02:06 | 10-12 07:26:04.134 | 0.08705 → 0.08675 | −0.051694 / −0.345% | STRONG_SELL (signal path #168277) | ACCUMULATED_EVIDENCE | Late WATCH-only entry at the top of a burst (4.72 ATR above the low) | F21 (FIX-119 deferral skipped because of 1 old BUY); entry quality exactly 50 (−30 R:R, −20 extension); R:R 0.44 | **VALID LOSS** under the coded rules (rule question F21) | High | Expected yes; exit avoided a stop hit at 07:29:22 | E14, E24, E32, I5-003 |
| **158** | XRPUSDT | 10-12 05:35:04 | 10-12 06:30:04.217 | ≈1.4108 (derived from the gross %) → 1.4090 | −0.031896 / −0.128% | SELL_CONFIRMED (#166819 STRONG_SELL, #166821 5m SELL) | SETUP_CONFIRMATION_WAKEUP (5m #165415 BUY) | No follow-through; best +0.06% | R:R 0.33 (target +0.165% is negative after fees, F23); opportunity 53 minutes old; one of only 2 losses among 10 in its bucket | **VALID LOSS** | High | Yes for P&L. Replay exit timing differs (F18/E37). | E5, E27, E37, E38, E42, I4-001, I6-002 |
| **143** | EGLDUSDT | 10-12 01:40:04 | 10-12 01:43:35.046 | 4.033 → 4.027 | −0.037193 / −0.149% | PROFIT_LOCK_HARD_EXIT (ACTIVE-lock branch) | SETUP_TIMEFRAME_ATR | Market gap through the profit lock: 4.100 → 4.027 (−1.78%) between ticks 7.98 s apart, after a +1.69% peak | **F22**: a one-bar-old 5m BUY #159285 authorized the entry; the current 5m #159407 was WATCH with immediate entry not allowed. F13: target extended twice instead of taking +0.84%. F17: score 60 vs stored 70. High-ATR group (F27). | **VALID LOSS (gap)** with a contributing timing-defect candidate (F22) | High (gap), medium (F22 counterfactual) | Gap fill reproduces from price events. F22 matches only by chance (5m generated 2 ms after the 1m). | E10, E11, E20, E30, E33, E37, E40, E41, I7-004 |
| **137** | EGLDUSDT | 10-11 23:55:04 | 10-12 00:00:08.935 | 3.999 → 3.987 | −0.075019 / −0.300% | SELL_CONFIRMED (src #156850 STRONG_SELL; 5m SELL #156852) | IMMEDIATE_VALIDATION → BALANCED_NEUTRAL_5M_WATCH_1H | No follow-through after a 2-candle micro-burst in a calm range; best +0.15% | R:R 0.64; entry quality 58 (−30 R:R, −12 rejection); its 5m was genuinely NEUTRAL (no F15) | **VALID LOSS** | High | Expected yes; exit avoided a stop hit at 00:01:07 | E14, E19, E24, E32, E38 |

**Exits (PROJECT EVIDENCE, E5/E11/E14):**
- No frozen trade touched its stop.
- The exits avoided a stop on 137, 143 and 162.
- They gave up small targets on 158 (+0.17%) and 172 (+0.37%).
- They had no effect on 182.
- No exit is classified as a defect.

---

## FINAL ANSWERS TO THE FIVE REPORT QUESTIONS

1. **What happened:** six Production paper longs closed at small losses of −0.13% to −0.35% gross. Five were closed by signal exits and one (143) by a profit-lock hard exit through a price gap. None reached its −0.5% stop.

2. **Why the system entered and exited:** every entry passed a coded exploratory route with an Entry Quality score of 50–60 and a reduced or exploratory size. Every exit followed `PositionExitPolicy` or the signal-path rules on fresh signals. *(PROJECT EVIDENCE)*

3. **Whether the rules were followed:**
   - Four trades followed the intended rules: 137, 158 and 162 fully, and 143 apart from F22.
   - **182** bypassed an intended hard veto because the relabelled decision was read downstream (F15).
   - **182 and 172** escaped the FIX-055 stop-exposure penalty. The code comment's stated intent is that "an exposed stop must reduce entry quality" (`ExecutionIntelligenceService.java:1007-1009`), but the implementation keys on the status, not the stop-exposed flag (F28).

4. **Confirmed defects and missing evidence:** F15 and F28 are confirmed. F22 is a candidate. Missing evidence: production logs, Replay run coverage, and the FIX-154 build identity (F7).

5. **Corrections and regression checks:** see SUGGESTED FIX CANDIDATES below. None is authorized.

---

## DATABASE EVIDENCE (PROJECT EVIDENCE unless labelled)

Queries were run by the Coordinator. Results are under `db/` and `db/CLAUDE/`. Every query was read-only and bounded.

**Selection proof**
- DBQ-FTS-001: `paper_position` schema.
- DBQ-FTS-003: 445 `wallet_trade` rows are EXECUTED, 101 with negative P&L.
- DBQ-FTS-004: `wallet_position_test` exists and is excluded.
- DBQ-FTS-005: the six frozen rows.

**Scope (F1, F2)**
- I1-011 / E8, positions opened since 2026-10-11 00:00 UTC: 58 CLOSED (9 negative) and 37 STOPPED (all negative).
- The last open was 10-15 06:08 KSA. No positions exist after 10-15, which is why the frozen set is 20 days older than the snapshot.
- **The selection rule excludes every STOPPED loss.** The frozen set therefore covers only signal or lock exits that ended at a loss.

**Entry geometry**
- I1-002, I1-003, I6-002 / E1, E34, E42.
- Stop distance = max(2×ATR, 0.5% of price); target distance = 3×ATR. This was checked against the stored values for 158 and 143.
- Stored R:R: 137 0.64, 143 1.50, 158 0.33, 162 0.44, 172 0.74, 182 0.48.

**Latency and freshness**
- I1-003, I1-010, I2-001, I2-006 / E4, E13.
- Candle close to signal: 1.5–2.8 s. Signal to fill: 1.4–2.4 s. One work-item attempt each.
- `data_quality_valid=1` on every signal used. **No stale input.**

**Signal visibility**
- I4-001 / E27: `generated_at` → `created_at` is 1.29–2.28 s.
- 158's 06:30:02.192 tick arrived before its exit signals committed, so no exit fired on it. This is not a defect.

**F15 (182)**
- I3-001 / E16: 5m #176403, #176529, #176655 and #176792 have base STRONG_SELL (22–25), DOWNGRADED → NEUTRAL at MULTI_TIMEFRAME because the 1h WATCH trend score was 16 (≥ 15).
- I3-005 / E19: 5 of 95 entries relied on a relabelled bearish 5m.
- I4-002 / E28: 963 bearish 5m signals were relabelled NEUTRAL from 10-11 to 10-15. Only 5.4% of them had a real bullish higher-timeframe decision.

**F28**
- I6-002: 172 and 182 have `liquidity_status=BULLISH_SUPPORT` and `order_book_stop_exposed=1`.
- I7-001, population of 95:

  | liquidity_status | stop_exposed | n | W/L | avg gross % |
  |---|---|---|---|---|
  | BALANCED | 0 | 54 | 28/25 | −0.016 |
  | BULLISH_SUPPORT | 0 | 13 | 9/4 | +0.099 |
  | **BULLISH_SUPPORT** | **1** | **5** (104, 138, 165, 172, 182) | **2/3** | −0.012 |
  | STOP_EXPOSED | 1 | 18 | 6/12 | **−0.250** |
  | TARGET_BLOCKED | 0 / 1 | 1 / 3 | 1/0, 1/2 | +2.131 / −0.265 |
  | WALL_WEAKENING | 1 | 1 | 1/0 | +0.115 |

  - 9 entries had an exposed stop under a status other than STOP_EXPOSED.
  - Five of them were under BULLISH_SUPPORT, where the −15 was certainly skipped.
  - The stored scores of 104, 138 and 165 are not individually known, so the full counterfactual is open (U30).

**F22**
- I5-001 / E33: 143's 1m #159408 was generated at 01:40:01.634. 5m #159407 (WATCH, immediate entry 0) was generated 2 ms later and committed at 01:40:02.941. The BUY committed at 01:40:03.115.
- I6-003 / E41: 3 of 32 boundary entries saw the 5m weaken: 100 (won), 123 (−0.698 USDT) and 143 (lost).
- **I7-004 (new):** the stored `confluence_higher_signal_generated_at` proves which 5m each entry read:
  - 143 read the 5m generated 10-12 01:35:01.553;
  - 123 read the one generated 10-11 21:45:04.826 (BUY);
  - 100 read the one generated 10-11 03:20:06.122 (STRONG_BUY).

  So all three read the previous bar's 5m. For 143 this was decisive (E32/E33). For 123 it is **unproven**: its route was a 1m-BUY IMMEDIATE_VALIDATION, and whether a 5m WATCH would have blocked it was not evaluated.

**F27 / F23 (I6-001 / E38, I7-002)**

| ATR% | n | win rate | gross avg |
|---|---|---|---|
| < 0.08 | 10 | 80% | +0.094% |
| 0.08–0.167 | 19 | 63% | +0.03% |
| 0.167–0.25 | 31 | 55% | +0.035% |
| **≥ 0.25** | **35** | **31%** | **−0.150%** |

- I7-002, high-ATR group by route:
  - IMMEDIATE_VALIDATION BUY / WEAK_UPTREND: 9 entries, 2 wins, −0.20%.
  - DEFERRED_CONTINUATION: 5 entries, 1 win, −0.48%.
  - **SETUP_TIMEFRAME_ATR: 7 entries, 1 win, about −0.30% (AGENT INFERENCE, weighted).** The same route on low ATR won 8 of 9 (I7-002).
- 143 is in the losing high-ATR SETUP_TIMEFRAME_ATR cell.

**F25 (I7-003)**
- Every Entry Quality score of 70 or more falls in the mid or high ATR buckets. None is in the low bucket, because R:R below 1 costs −30.
- Within the high bucket: 50–54 scored +0.78% (n=2); 70+ scored −0.23% (n=22, 27% win rate).
- Within the mid bucket: 50–54 scored +0.28% (n=8); 70+ scored −0.05% (n=13).
- **AGENT INFERENCE:** the score is largely a proxy for volatility. Within each stratum the inverse direction weakens but does not reverse, and the samples are small. Entry Quality is **not shown to be predictive**. This is a calibration concern, not a defect.

**Profit lock (143)**
- I1-006/007, I2-003, I3-004, I4-004 / E6, E10, E11, E20, E30.
- The original target 4.0647 was reached at 01:40:41 and extended to 4.0805, then to 4.1043 at 01:41:13.
- Peak 4.101. Lock 4.07579 (60% of the way to the target), state ACTIVE, rebase NULL.
- The 1m candle at 01:43 had high 4.100, low 3.994 and 433 trades: a real market move.
- The SELL committed 25 ms after the trigger tick.

**Fees**
- E35: `fee_usdt=0` (PROJECT EVIDENCE).
- EXTERNAL RESEARCH: about 0.10% per side at VIP 0 (secondary sources).
- At a 0.2% round trip, every R:R and ATR bucket turns net-negative, and 158's target is below zero.

**Failed or empty queries**
- DBQ-CLAUDE-I1-008: invalid SQL, replaced by I2-001.
- **DBQ-CLAUDE-I7-005: the result file is empty.** I cannot tell zero rows from a failed run, so Replay coverage of the window is UNKNOWN.

---

## LOG EVIDENCE

- **None.** Production logs are not reachable from this session, and there is no SSH evidence.
- Local `logs/` searched for `2026-10-1[12]`: **0 matches** (re-checked this iteration).
- All timing comes from DB timestamps: `generated_at`, `created_at`, `observed_at` and `executed_at`.
- Log lines that would corroborate the findings but were **not** seen:
  - the FIX-11G INFO line (`TradeExecutionValidationService.java:273`) for 182 and 137;
  - the FIX-11G post-guard line (`ExecutionIntelligenceService.java:700`);
  - the FIX-105 reference-source debug line (`ExecutionIntelligenceService.java:931`).

---

## CODE EVIDENCE (PROJECT EVIDENCE, current working tree)

Caveat F7: the build that ran on 10-11/12 is not identified by commit. Flyway V107 and V109 (FIX-154) were installed 2026-10-10 03:15 and 04:52 KSA, before every frozen trade. **That contradicts AGENTS.md's statement that FIX-154 is "not-yet-implemented".** I am reporting the contradiction, not resolving it.

**F15: relabelled bearish 5m read as neutral**
- `MultiTimeframeConfluenceService.java:136-138` neutralizes a bearish signal when bullish count > 0.
- `:337-339` / `:24` count a WATCH as bullish when its trend score is ≥ 15.
- The hard veto `:146-151` checks only `decision==STRONG_SELL`.
- These also read the relabelled `getDecision()`:
  - `TradeExecutionValidationService.java:81,93`;
  - the FIX-11G gate at `:263-284` (minimum entry quality 50, `:49`);
  - `HigherTimeframeBuyAssessmentService.java:50,64`.

**F28: stop-exposure penalty hidden by status precedence**
- `OrderBookLiquidityService.java:550-570` `classify()` checks in this order: targetBlocked (→ WALL_WEAKENING / TARGET_BLOCKED), then strong negative imbalance (→ BEARISH_PRESSURE), then moderate imbalance (→ **BULLISH_SUPPORT**), and only then `stopExposed` (→ STOP_EXPOSED).
- `ExecutionIntelligenceService.java:1007-1012` applies the −15 only when the status is exactly STOP_EXPOSED. The comment there says "an exposed stop must reduce entry quality".
- The same status precedence also changes the final score: `FinalDecisionService.java:255-262` gives BULLISH_SUPPORT 95 against STOP_EXPOSED 35. AGENT INFERENCE: the effect on total score was not quantified.
- The chase cutoff of 50 is enforced at `ExecutionIntelligenceService.java:229`, `:1171-1175`, on the IMMEDIATE_VALIDATION route (`:686-694`) and at execution time (`:1167-1168`).

**F22: boundary race on the 5m authority**
- `FIVE_MINUTE_MAX_AGE` is 20 minutes (`:66`), so a 5-minute-old 5m counts as fresh.
- The 1m-triggered setup route uses the newest committed 5m (`:614`, `:628`, `:631-634`).
- `revalidateAtExecutionPrice` (`:1150-1169`) never rereads the 5m (E40).

**F17: different ATR at execution time**
- The setup, wake-up and transition routes score with the 5m ATR (`:364`, `:439`, `:615`, `:634`).
- The stored score and the execution recheck use the 1m ATR (`:857-858`, `:1167`; `PaperTradingService.java:377-385`).

**Entry Quality and geometry**
- `assessEntryQuality` at `:865-1030`: flat −30 for R:R below 1 (`:991`); rejection floor of 50 (`:995-1003`); age rounded down to whole minutes (`:950`).
- The rolling window is the last 20 1m signals (`:2602-2603`).
- `AtrRiskService.java:34-45` builds the stop and target; `application.yml:137-144` holds minimum-stop-percent 0.50 and the 3×ATR target.
- The only minimum-R:R gate is `BREAKOUT_RETRACEMENT_MIN_RR=1.15` (`:212`, `:1539`).

**Other rule gaps**
- F19: `MarketStrategyService.java:70` maps WEAK_DOWNTREND and STRONG_DOWNTREND to TRADE_FOLLOWING with entry allowed.
- F21: the FIX-119 deferral is skipped when `buyCount > 0` (`ExecutionIntelligenceService.java:1275-1279`).

**Exits**
- `LivePositionProtectionService.java:95-97, :207-216, :235-243` and `PositionExitPolicy.java:20-92`: exits are evaluated only when a price tick arrives (F14, low).
- `ProfitLockPolicy.java:30-51` computes the lock.
- `continuationPolicy.extendTarget()` (F13) extended 143's target.
- `PaperTradingService.java:197-208` (signal-path STRONG_SELL) together with `validateSell` (`TradeExecutionValidationService.java:165-191`).

**Replay**
- `ShadowProductionReplayService.java:87-168` replays Production ticks as the exit clock and fills at the tick price.
- It makes a signal visible at `generated_at`, while Production sees it at `created_at`, 1.3–2.3 s later (F18).
- Confluence uses `replayScope.latestAtOrBefore` (`MultiTimeframeConfluenceService.java:106-108`).

**Fees**
- `WalletAutoExecutionService.java:173, 260, 349, 443, 540, 653` record `feeUsdt(ZERO)`.

---

## FINDINGS REGISTER (final status)

| ID | Finding | Type | Severity | Trades affected | Status |
|---|---|---|---|---|---|
| F15 | Bearish 5m relabelled NEUTRAL is consumed as genuine neutrality by BUY gates | Logic defect | Medium | 182 (5 of 95 entries) | **CONFIRMED** |
| F28 | FIX-055 stop-exposure −15 skipped when the status is BULLISH_SUPPORT (precedence) | Logic defect against the in-code stated intent | Medium | 172, 182 (counterfactually blocked), 5 of 95 entries | **CONFIRMED code behavior.** Intent is inferred from the code comment. |
| F22 | 1m-triggered decision at a 5m boundary reads the previous bar's 5m, and the execution recheck does not reread it | Timing defect candidate | Medium | 143 (decisive); 123 (read stale, effect unproven) | CANDIDATE |
| F17 | Execution-time Entry Quality uses the 1m ATR on 5m-ATR routes | Code inconsistency | Low | 143 (60 → 70 stored) | CONFIRMED, no loss effect |
| F18 | Replay signal visibility at `generated_at` instead of commit time | Replay parity risk | Low–medium | 158 (timing only), 143 (matches by chance) | CONFIRMED mechanism, no P&L effect proven |
| F27 | Gross population losses concentrated at ATR% ≥ 0.25 | Systemic (AGENT INFERENCE) | High (population) | 143 | Study item |
| F23 | Low-ATR geometry (R:R below 1) is gross-positive but not viable after fees | Fee viability (live only) | Low for paper | 137, 158, 162, 172, 182 | Narrowed. Not a cause of loss. |
| F25 | Entry Quality not predictive; largely a volatility proxy | Calibration | Low | n/a | Partly confounded (I7-003) |
| F13 | Target extension turned +0.84% into −0.15% | Rule question | n/a | 143 | Open design question |
| F19 | No gate on long entries in a downtrend regime | Rule gap | Low | 182 | Open |
| F21 | One old BUY skips the FIX-119 late-entry deferral | Rule question | Low | 162 | Open |
| F14 | Exits wait for the next tick | Latency | Low (zero cost here) | 137, 158, 172, 182 | Confirmed |
| F1 | Selection excludes all STOPPED losses | Scope limitation | n/a | n/a | Reported |
| F7 | FIX-154 migrations installed while AGENTS.md says not implemented | Doc contradiction | Needs both agents | n/a | Reported, unresolved |
| F12 / F16 | Traceability gaps; server clock about 60–130 ms behind Binance | Observational | Low | 158 / n/a | Reported |

Withdrawn or retracted:
- F8/F23 as a "structural root cause";
- E25/U21 (the anchor-reset hypothesis);
- the E24 floor explanation for 162;
- F10 as a protection defect;
- F3 (stale exit inputs).

---

## SUGGESTED FIX CANDIDATES (candidates only; NOT authorized; NO CHANGE made)

1. **F15:** BUY hard vetoes and FIX-11G should treat a relabelled bearish `original_decision` as opposition. Replay regression: 182 must be vetoed; the 4 SELL → NEUTRAL entries must be re-evaluated.
2. **F28:** key the FIX-055 penalty on `order_book_stop_exposed`, or make precedence keep the exposure visible. Replay regression: 172 and 182 should become CHASE_ENTRY_BLOCKED; recheck 104, 138 and 165; no FIX-055 PEPE case may regress.
3. **F22:** on 1m-triggered routes at a 5m boundary, wait for or require the 5m of the bar that just closed (freshness taken from the persisted candle close, per AGENTS.md), and reread it at execution time. Regression: 143 is not entered; check 123 and 100.
4. **F27:** run a Replay study of the high-ATR group by route, especially SETUP_TIMEFRAME_ATR and DEFERRED_CONTINUATION, before any geometry change.
5. **F18:** make Replay signal visibility follow commit time or a measured delay.
6. **F17:** use the same ATR basis at decision time and execution time.
7. **F23:** fee-aware viability gate, only if live trading with fees is planned. Keep fee-adjusted figures in every study.

Each of these needs the FINAL_CHANGE_SPECIFICATION process and user approval.

---

## UNKNOWNS / MISSING EVIDENCE

- **U29:** Replay run coverage of 10-11 17:00 to 10-12 12:00 UTC. The I7-005 result is empty, so parity is not verified.
- **U30:** stored Entry Quality for 104, 138 and 165, and the size of the F28 counterfactual across the population.
- **123 under F22:** whether a 5m WATCH would have blocked its IMMEDIATE_VALIDATION route.
- **U22:** what changed in confluence recording between 10-12 20:00 and 10-13 01:50 KSA.
- **U7/F7:** the exact deployed commit.
- **Fee tier:** the actual account tier is unknown (EXTERNAL figures are secondary sources).
- **Logs:** none were available to me.

### DB_REQUEST
QueryId: DBQ-CLAUDE-I8-001
Purpose: Post-freeze, U29. Re-run the Replay coverage check, because the I7-005 result was empty. A minimal count, so zero rows can be told apart from a failed run.
SQL:
SELECT COUNT(*) AS n_rows, COUNT(DISTINCT t.test_run_id) AS n_runs, MIN(t.generated_at) AS first_gen, MAX(t.generated_at) AS last_gen FROM trade_signal_test t WHERE t.generated_at BETWEEN '2026-10-11 17:00:00' AND '2026-10-12 12:00:00' LIMIT 1
### END_DB_REQUEST

### DB_REQUEST
QueryId: DBQ-CLAUDE-I8-002
Purpose: Post-freeze, U30. Stored Entry Quality and route for the entries whose exposed stop was hidden under another status, to size the F28 counterfactual.
SQL:
SELECT p.id, p.symbol, p.realized_pnl, s.liquidity_status, s.order_book_stop_exposed, m.entry_quality_score, w.execution_reason FROM paper_position p JOIN trade_signal s ON s.id=p.signal_id LEFT JOIN wallet_managed_position m ON m.entry_signal_id=p.signal_id LEFT JOIN wallet_trade w ON w.signal_id=p.signal_id AND w.side='BUY' WHERE p.id IN (104,138,165,172,182,113,126,136,133) ORDER BY p.id LIMIT 20
### END_DB_REQUEST

---

**Documentation check:** this is analysis only, with no implementation, so CHANGELOG, ARCHITECTURE and TRADING_RULES need no update. Candidate entries for `claude-docs/INVESTIGATION_LOG.md` are F15, F28, F22 and the F7 contradiction. They can only be written in a session that has write permission, and anything shared needs dual-agent agreement first.


## SECTION 3 - COORDINATOR COMMON FINDINGS

The Coordinator is a comparator, not a third trading analyst. It does not choose a winner.

Both agents were required to analyze the same frozen IDs: 182, 172, 162, 158, 143, 137

Read sections 1 and 2 for root causes, entry verdicts, proof IDs, and suggested fixes.
Agreement is FULL only when both primary classifications match; otherwise STRONG/PARTIAL/NONE as stated by the two frozen reports. The Coordinator does not invent a third classification.

CANDIDATE IMPROVEMENT - NOT APPROVED. No implementation.
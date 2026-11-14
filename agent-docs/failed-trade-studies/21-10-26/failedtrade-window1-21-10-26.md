# DAILY FAILED-TRADE INDEPENDENT FORENSIC STUDY

Window: WINDOW1
Run: fts-WINDOW1-20261021-125052
Implementation authorization: NOT AUTHORIZED
Suggested fixes are candidates only and were not implemented.

STUDY WINDOW: WINDOW1
SNAPSHOT TIME UTC: 2026-10-22 10:28:18
SNAPSHOT TIME KSA: 2026-10-21 12:50:52
INTENDED WINDOW CLOCK KSA: 2026-10-21 10:00:00
SNAPSHOT NOTE: SNAPSHOT CLOCK DRIFT: intended 2026-10-21 10:00:00 KSA; actual 2026-10-21 12:50:52 KSA. Exact window clock was not preserved.
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

## Final classification — WINDOW1

**Status:** Final self-review complete; classification is evidence-limited. No changes made.

- Frozen authoritative entity: `crypto_ai.paper_position`
- Frozen IDs: **182, 172, 162, 158, 143, 137**
- Selection supplied: `status=CLOSED AND closed_at IS NOT NULL AND realized_pnl < 0 ORDER BY closed_at DESC LIMIT 6`
- Permitted `db/CODEX/` evidence files: **0**
- Database proof queries `DBQ-FTS-001`–`DBQ-FTS-005`: results unavailable.
- Snapshot metadata is internally inconsistent: supplied UTC `2026-10-22 10:28:18` converts to KSA `2026-10-22 13:28:18`, not the separately supplied KSA `2026-10-21 12:50:52`. It is not used as a converted instant.

| Paper position | Final classification | Evidence/confidence |
|---|---|---|
| 182 | **Unresolved; log-supported probable mechanical stop-loss / valid-loss candidate** | Runtime logs strongly associate ICPUSDT position 182 with a completed stop path, but durable row, wallet, price-event, and exit-audit linkage is absent. |
| 172 | **Unclassifiable — insufficient evidence** | No permitted DB evidence or position-specific logs. |
| 162 | **Unclassifiable — insufficient evidence** | No permitted DB evidence or position-specific logs. |
| 158 | **Unclassifiable — insufficient evidence** | No permitted DB evidence or position-specific logs. |
| 143 | **Unclassifiable — insufficient evidence** | No permitted DB evidence or position-specific logs. |
| 137 | **Unclassifiable — insufficient evidence** | No permitted DB evidence or position-specific logs. |

## DATABASE EVIDENCE

### PROJECT EVIDENCE

No database query results are available in the permitted Codex evidence directory. Therefore this study cannot verify:

- The six `paper_position` rows actually satisfy the frozen predicate.
- Symbols, entry/exit times, quantities, prices, realized P&L, or close reasons.
- Links from position 182 to signal `164053`, price event `1837489`, wallet records, managed position, or an exit audit.
- Whether the current-source `STOPPED` status contradicts the frozen `status=CLOSED` selection, or whether a historical version/status normalization explains it.
- Replay reproducibility, data quality, candle lineage, execution freshness, or fee reconciliation.

The missing database evidence is a material blocker, not evidence that no records exist.

### DB_REQUEST

QueryId: DBQ-CODEX-I8-001  
Purpose: Verify the frozen selection and retrieve the authoritative lifecycle rows plus actual schemas needed to resolve position 182’s stop-path linkage and the `CLOSED`/`STOPPED` discrepancy.  
SQL:

```sql
SELECT
    c.table_name,
    c.ordinal_position,
    c.column_name,
    c.column_type,
    c.is_nullable,
    c.column_key
FROM information_schema.columns AS c
WHERE c.table_schema = 'crypto_ai'
  AND c.table_name IN (
      'paper_position',
      'trade_signal',
      'market_price_event',
      'wallet_trade',
      'wallet_managed_position',
      'production_exit_audit',
      'position_management_event',
      'signal_processing_work'
  )
ORDER BY c.table_name, c.ordinal_position;

SELECT p.*
FROM crypto_ai.paper_position AS p
WHERE p.id IN (182, 172, 162, 158, 143, 137)
ORDER BY FIELD(p.id, 182, 172, 162, 158, 143, 137);

SELECT p.id, p.status, p.closed_at, p.realized_pnl
FROM crypto_ai.paper_position AS p
WHERE p.status = 'CLOSED'
  AND p.closed_at IS NOT NULL
  AND p.realized_pnl < 0
ORDER BY p.closed_at DESC
LIMIT 6;
```

### END_DB_REQUEST

## LOG EVIDENCE

### PROJECT EVIDENCE — position 182 only

The available archived runtime logs support this ICPUSDT sequence:

| Event | UTC | KSA |
|---|---|---|
| 1m candle close | 2026-10-11 22:40:59.999 | 2026-10-12 01:40:59.999 |
| Signal `164053` generated | 2026-10-11 22:41:01.677 | 2026-10-12 01:41:01.677 |
| Signal row created | 2026-10-11 22:41:02.996 | 2026-10-12 01:41:02.996 |
| Continuation entry allowed and price recheck passed | 2026-10-11 22:41:03.047–03.060 | 2026-10-12 01:41:03.047–03.060 |
| Signal-processing transaction committed | 2026-10-11 22:41:04.863 | 2026-10-12 01:41:04.863 |
| Protection evaluation explicitly names `positionId=182` | 2026-10-11 22:41:05.405 | 2026-10-12 01:41:05.405 |
| Price event `1837489` observed | 2026-10-11 22:49:33.460 | 2026-10-12 01:49:33.460 |
| Stop-loss execution logged, price `3.465000000000`, stop `3.465026371152` | 2026-10-11 22:49:36.971 | 2026-10-12 01:49:36.971 |
| Event `1837489` outcome logged `APPLIED` | 2026-10-11 22:49:36.974 | 2026-10-12 01:49:36.974 |

The entry/protection log records entry `3.493`, target `3.537460443272`, and stop `3.465026371152`. The static reward/risk is approximately **1.589**, above the current continuation route’s 1:1 minimum.

The observation-to-committed-outcome duration is about **3.514 seconds**. The logs report a **2.438-second** symbol transaction. This is a measurement concern, but it does not prove a protection failure or adverse-price impact: the observed price was already below the stop, and the outcome completed as `APPLIED`.

### AGENT INFERENCE

Position 182 is best described as a probable mechanically completed stop loss, not a demonstrated missed-stop or execution defect. This remains provisional because log order is not a durable financial linkage.

No position-specific log evidence supports a classification for IDs 172, 162, 158, 143, or 137.

## CODE EVIDENCE

### PROJECT EVIDENCE

Current source shows:

- A BUY resolves a fresh canonical execution price and revalidates it; entries at/below stop or at/above target are rejected. [`PaperTradingService.java`](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/service/PaperTradingService.java:340), [`ExecutionIntelligenceService.java`](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/execution/service/ExecutionIntelligenceService.java:1150)
- The continuation route requires non-bearish 5m/1h context and reward/risk of at least 1:1. [`ExecutionIntelligenceService.java`](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/execution/service/ExecutionIntelligenceService.java:1740)
- Stop loss is absolute; successful mechanical exit calls `closePaper(..., STOPPED, "STOP_LOSS", ...)`. [`LivePositionProtectionService.java`](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/position/service/LivePositionProtectionService.java:173)
- `closePaper` persists exit price, realized P&L, status, reason, and close time. [`LivePositionProtectionService.java`](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/position/service/LivePositionProtectionService.java:391)
- A direct price event reaches `APPLIED` only within an active effects transaction and logs the outcome after commit. [`MarketPriceEventService.java`](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/market/service/MarketPriceEventService.java:80)

Current source distinguishes `CLOSED` from `STOPPED`. This conflicts with the supplied frozen selection only if position 182’s logged stop is durably linked to that row and the historical binary followed this source lineage. It is an unresolved contradiction, not a confirmed defect.

## EXTERNAL RESEARCH

No external research is used to classify any loss. Prior Binance stream documentation review only supports the general caution that kline events are not a complete exchange-tick record; it establishes nothing about these trades.

## Candidate next steps — no change authorized

1. Execute the bounded database request and reconcile the frozen selection.
2. For position 182, retrieve linked wallet, managed-position, price-event, and exit-audit evidence before attributing any loss to latency or strategy quality.
3. Investigate `CLOSED` versus `STOPPED` only after the authoritative row and selection query are available.
4. Do not classify the other five losses, or propose trading-rule changes, without their lifecycle and entry/exit evidence.

No code, configuration, database, deployment, or git state was changed.


## SECTION 2 - CLAUDE ANALYSIS

I've finished reviewing my seven earlier iterations, the I7 query results and the relevant code. Below is the frozen Iteration 8 study.

The two I7 queries closed three of the remaining unknowns and confirmed that trade 162 got no rejected-high penalty. None of the six classifications changed. No application logs were available, so none were read. No code, config, database, git or deployment changes were made. Plan mode was on but no Write or ExitPlanMode tool was available, so this message is the whole output.

---

# CLAUDE_FAILED_TRADE_STUDY.md — WINDOW1 (FROZEN, Iteration 8 of 8)

## Summary

| Field | Value |
|---|---|
| Agent / track | CLAUDE, independent (no cross-review) |
| Study window | WINDOW1 |
| Snapshot (coordinator labels) | UTC 2026-10-22 10:28:18 · KSA 2026-10-21 12:50:52. The two labels contradict each other (see Limitations L-1). |
| Authoritative entity | `crypto_ai.paper_position` |
| Selection | `status='CLOSED' AND closed_at IS NOT NULL AND realized_pnl < 0 ORDER BY closed_at DESC LIMIT 6` |
| Frozen trades | 182, 172, 162, 158, 143, 137. No substitutes. |
| Total recorded loss | −0.273 USDT. Fees are not included, because paper fills record `fee_usdt = 0`. |
| Stale data or execution delay as the cause | **None** (F-22, F-29) |
| Confirmed logic defects | **2**: trade 162 (two routes apply different guards) and trade 143 (5-minute signal chosen by wall-clock time) |
| Rules followed, but the design is defective or has gaps | **4**: trades 182, 172, 158, 137 |
| Exit defects | **None.** Trade 143's loss came from a target extension plus an 8-second gap between stored price updates during a real crash. That outcome is not an entry defect. |
| Main systemic cause | RC-1: five of six trades had planned reward/risk below 1, and fees are not modelled, so they had no edge after fees |
| Code / config / DB changes | **NONE.** Analysis only. All fixes below are candidates. |
| Status | **FROZEN** |

**Evidence labels:** **[PE]** = PROJECT EVIDENCE (DB rows, code, stored text). **[ER]** = EXTERNAL RESEARCH. **[AI]** = AGENT INFERENCE.

**Time convention [PE, F-23 proven by DBQ-CLAUDE-I3-001]:** the study's MySQL session runs at UTC+3, so every value in the result files is displayed as KSA. Stored values are correct UTC. Below, every time is converted and labelled both ways.

## Mandatory table (one row per frozen ID)

| ID | Symbol | Opened UTC / KSA | Closed UTC / KSA | PnL USDT (gross) | Entry signal / route | Reward/risk (gross / after ~0.2% fees) | Entry Quality | Exit | Root cause | Classification | Replay reproducible? |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 182 | ACEUSDT | 10-12 06:57:06 / 09:57:06 | 07:05:20 / 10:05:20 | −0.031847 | 176710 1m BUY, `IMMEDIATE_VALIDATION`, FIX-11G 25% | 0.48 / ≈0.06 | 50. The rejected-high floor held a 38 at 50. | SELL_CONFIRMED | RC-1, RC-2 (floor, counter-trend after 7 STRONG_SELLs) | Rules followed; **design defect** (floor + FIX-11G) | Likely yes for 176710 **[AI]**. Why 176692 was skipped may not reproduce. |
| 172 | SHIBUSDT | 10-12 03:36:05 / 06:36:05 | 04:15:45 / 07:15:45 | −0.045537 | 171589 1m, `IMMEDIATE_VALIDATION`, BALANCED_STRONG | 0.74 / ≈0.24 | 60 | HTF_SELL_CONFIRMED | RC-1, RC-3 (a rejected 5m signal counted as bullish), price tick larger than ATR | **Valid loss with design gaps** | Yes **[AI]** |
| 162 | DOGEUSDT | 10-12 01:02:06 / 04:02:06 | 01:26:04 / 04:26:04 | −0.051694 | 167668 1m WATCH, `ACCUMULATED_EVIDENCE`, BALANCED_EARLY | 0.44 / ≈0.03 | 50 (LATE_ENTRY), not floored. It passed because the age truncation dropped 24 ms. | STRONG_SELL | RC-3: the guards that blocked 167646 were absent on the accumulated route, and FIX-119 was bypassed. RC-1. | **Logic defect** | Likely yes **[AI]** |
| 158 | XRPUSDT | 10-11 23:35:04 / 10-12 02:35:04 | 10-12 00:30:06 / 03:30:06 | −0.031897 | 165412 1m WATCH, woken by 5m 165415 BUY, `SETUP_CONFIRMATION_WAKEUP` | 0.33 / **<0** | Not stored. Rebuilt as 60 at decision and exactly 50 at the execution recheck. | SELL_CONFIRMED | RC-1 (target smaller than fees, no minimum reward/risk), F-57 (the recheck uses a different ATR) | Rules followed; **design gap** + logging gap | Yes **[AI]** |
| 143 | EGLDUSDT | 10-11 19:40:04 / 22:40:04 | 19:43:36 / 22:43:36 | −0.037193 | 159408 1m WATCH (`WAIT_FOR_RETRACEMENT`), `SETUP_TIMEFRAME_ATR`, using the superseded 5m BUY 159285 | 1.50 / ≈0.82 | 60, scored on 159285's 5m ATR. Would be 70 on the fresh 5m ATR. | PROFIT_LOCK_HARD_EXIT | RC-4 (newest 5m picked by `generated_at`; the new 5m WATCH was 2 ms late), RC-2. Outcome: target extension plus a price gap in a crash. | **Logic defect** (signal selection) + Replay/Production parity gap | **Probably not.** Replay would likely see the 5m WATCH and reject the entry (F-34) **[AI]**. |
| 137 | EGLDUSDT | 10-11 17:55:04 / 20:55:04 | 18:00:12 / 21:00:12 | −0.075019 | 156723 1m STRONG_BUY, `IMMEDIATE_VALIDATION`, FIX-11G 25% | 0.64 / ≈0.17 | 58 (−30 for reward/risk, −12 for the rejected high) | SELL_CONFIRMED | RC-1, RC-2 (counter-trend mean reversion after 8 STRONG_SELLs) | Rules followed; **design gap** | Yes **[AI]** |

The PnL, ID, symbol, open/close and close-reason columns come from DBQ-FTS-005. The UTC conversion comes from F-23. Reward/risk comes from F-11 and F-40; Entry Quality from F-46, F-55, F-56 and F-58; routes from F-04 and I2-003.

## Root-cause catalogue

- **RC-1, no edge after fees [PE + ER].**
  - The stop is the larger of 2 ATR and a 0.50% minimum (`AtrRiskService:35-45`, `application.yml:143`). The target stays at 3 ATR.
  - So reward/risk falls below 1 whenever ATR is under about 0.167% of price.
  - These routes set no minimum reward/risk (F-31). A value below 1 only subtracts a flat 30 points from Entry Quality (`ExecutionIntelligenceService:991`).
  - Fees are not modelled (F-12).
  - Measured effect: the 27 positions with reward/risk below 1 made +0.276 USDT gross, about −0.63 to −0.93 USDT after fees (F-61).
- **RC-2, Entry Quality only sizes trades; it does not filter them [PE].**
  - It ignores direction (Javadoc, line 845).
  - A rejected-high penalty cannot take a score of 50 or more below 50 (lines 995-1002).
  - FIX-11G needs only a score of 50 (`TradeExecutionValidationService:263-284`).
  - FIX-119's own comment calls the floored-50 pattern a defect (lines 1232-1237), but FIX-119 covers only the accumulated route.
- **RC-3, routes apply different checks to the same setup [PE].**
  - The accumulated route skips FIX-041 (lines 672-684) and the post-bearish guard (lines 2081-2118). A single blocked BUY satisfies FIX-119's "at least one BUY" exemption (F-47).
  - `balancedBuy` looks only at the 5m label and ignores that signal's own `final_entry_allowed` (F-26).
  - The FIX-056 execution recheck re-scores with the 1m ATR, while the decision used the 5m ATR (F-57).
- **RC-4, the 5-minute signal is chosen by wall-clock time, not candle identity [PE].**
  - `latestAtOrBefore` filters on `generated_at`, which in Production is `Instant.now()` (`AnalysisService:150`, `ExecutionIntelligenceService:2455-2462, 2608`).
  - In 28 of 32 positions the 5m signal for the same candle close was generated after the 1m signal (I2-008).
  - Replay gives both signals the candle-close time, so it does not have this race (F-34).

## Findings frozen in this iteration (from the I7 results)

- **F-65 [PE: I7-001 + `ExecutionIntelligenceService:1208-1222`]. Trade 162 is corroborated again, which reverses F-59/C-12 for 162 only.**
  - 162's `entry_reason` contains "Entry Quality 50/100 (LATE_ENTRY) capped the initial allocation at 25%".
  - That text is only written when the cap actually reduces the size (lines 1213-1220). When it is written, it includes "recent rejection zone near …" whenever `recentRejectedHigh != null` (line 1208).
  - The rejection-zone text is absent (`rej_pos = 0`), so no rejected-high penalty was applied to 162. This agrees with the window-high argument (line 962).
  - For 172 the same check also holds: its text is present and the rejection text is absent.
- **F-66 [PE: I7-001, `PaperTradingService:411`; AI for the multiplication]. U-26 is closed.**
  - 162's text reads "Effective position 15%".
  - The 15% is the route's 25% multiplied by the 1m signal's ATR-recommended 60% (`REDUCED_POSITION 60`; see F-45). There is no separate 15% constant.
  - 182 has no Entry Quality text (`eq_pos = 0`). FIX-11G already set 25%, which equals the cap, so the guard returned the decision unchanged (line 1214). Its floor proof therefore rests on arithmetic alone (F-46, F-48).
- **F-67 [PE: I7-001]. U-27 is closed for 143 and stays open for 158.**
  - 143's text contains "Entry Quality is evaluated with the authoritative 5m ATR … Entry Quality 60/100 (ACCEPTABLE_ENTRY)". So the stored 60 is the **decision** score, which confirms F-55.
  - 158's text has no Entry Quality text at all. The wake-up route writes no score (F-42 confirmed).
- **F-68 [PE: I7-002]. U-28 is closed.**
  - 159285 (5m): BUY, `atr_immediate_entry_allowed = 1`, `final_entry_allowed = 1`.
  - 159408 (1m): WATCH, `atr_immediate_entry_allowed = 0` (ATR-extended).
  - 159407 (5m): WATCH, `atr_immediate_entry_allowed = 0`, generated 19:40:01.635944 UTC / 22:40:01.635944 KSA. That is 2.0 ms after 159408 (19:40:01.633945 UTC).
  - 165412 (1m): `atr_immediate_entry_allowed = 1`. That excludes the setup-timeframe wake-up (line 325) and confirms the FRESH_5M_CONFIRMATION wake-up (line 439) for 158.
  - 165415 (5m): BUY, both flags 1.

## Per-trade timeline and assessment

Each entry is judged only on what was known at the time. The outcome is listed separately.

**182 ACE**
- **Entry:**
  - 176710, 1m BUY 75, signal at about 06:57:01 UTC / 09:57:01 KSA.
  - The price was observed at 06:57:02.437 UTC and the fill price was 1.80 s old.
  - WEAK_DOWNTREND, `confluence=CONFLICT`, overextended, after seven 1m STRONG_SELLs (F-15).
- **Entry Quality:**
  - −30 for reward/risk 0.482.
  - −20 for distance: 5.95 ATR from the 0.1875 low (176528, 06:50:03 UTC / 09:50:03 KSA). That is 0.05 ATR below the −30 step (F-63).
  - The rejected high 0.1888 (176227, 06:38:01 UTC / 09:38:01 KSA) would take it to 38. The floor held it at 50 (F-46, F-48).
- **The earlier, better BUY 176692** (06:56:02 UTC / 09:56:02 KSA) passed FIX-11G with a floored 50 (audit row 2424) but did not execute. The cause is unknown (U-18). The leading candidate is the FIX-056 price recheck **[AI]** (F-49).
- **Outcome:** the price never traded above entry. The SELL_CONFIRMED exit followed the rules.

**172 SHIB**
- **Entry:** 171589, 03:36:05 UTC / 06:36:05 KSA, BALANCED_STRONG.
- **The weakness:** the "bullish" 5m signal 171559 was a BUY with confidence 49 and `final_entry_allowed = 0` (F-26).
- **Entry Quality:** 60 = −30 for reward/risk, −10 for 2.94 ATR.
  - SHIB's ATR (6.8e-9) is smaller than one price tick (1e-8), so each tick moves the score by 10 points (F-16, F-43).
  - The size cap cut the position from 75% to 25%.
- **Outcome:** the price got 49% of the way to the target. The exit came after the 1h signal turned SELL at 04:00:06 UTC / 07:00:06 KSA. It followed the rules.

**162 DOGE**
- **01:01:02 UTC / 04:01:02 KSA:** BUY 167646 was approved as BALANCED_EARLY with a score of 50 (LATE_ENTRY), then not executed.
  - The open-position limit is refuted: only position 160 was open (I5-002), and no wallet trade exists for 167646.
  - Either FIX-041 or the post-bearish guard blocked it. Which one is unknown (U-18) **[AI]**.
- **01:02:02 UTC / 04:02:02 KSA:** WATCH 167668 entered through `ACCUMULATED_EVIDENCE`.
  - Same authority, same LATE_ENTRY 50, at a worse price (0.08705 vs 0.08693).
  - Neither FIX-041 nor the post-bearish guard applies on this route. FIX-119 was bypassed because its evidence counted "1 BUY", which was the blocked 167646 (F-47).
- **Entry Quality:** −30, −20 (4.72 ATR). The age was 20 min 59.976 s, which `toMinutes()` truncates to 20, so the >20 min −5 penalty did not apply (F-58). No rejected-high penalty (F-65).
- **Outcome:** the price got 31% of the way to the target. The STRONG_SELL exit followed the rules.

**158 XRP**
- **Entry:** 1m WATCH 165412 at 10-11 23:35:01.469 UTC / 10-12 02:35:01.469 KSA. 5m BUY 165415 was generated 21 ms later and woke the opportunity (F-27, F-68).
- **Weakness:** reward/risk 0.327, so the target (0.16%) was smaller than the round-trip fee.
- **Entry Quality:** 60 at the decision. Exactly 50 at the FIX-056 recheck, because the 1m ATR gives 3.38 ATR → −10 (F-56).
- **Outcome:** the price got 33% of the way to the target. The SELL_CONFIRMED exit followed the rules.

**143 EGLD**
- **Signals:** 1m 159408 (WATCH, `WAIT_FOR_RETRACEMENT`, RSI 84.3) at 19:40:01.634 UTC / 22:40:01.634 KSA. The fresh 5m 159407 (WATCH, pullback, overextended) was 2 ms later, so `latestAtOrBefore` picked the 5m BUY 159285 from 19:35:01.553 UTC / 22:35:01.553 KSA instead (F-24, F-68).
- **Entry:** filled at 4.033 at 19:40:03.115 UTC / 22:40:03.115 KSA. The 159407 row had already been written at 19:40:02.941 UTC.
- **Entry Quality:** 60 = −10 for a 1.51% rise, −30 for 6.61 ATR on 159285's ATR. On 159407's ATR it would be 70, but the route returns null when the 5m signal is WATCH (line 1486) (F-55, F-67).
- **Outcome:**
  - The take-profit (4.0647) was passed at 19:40:41 UTC / 22:40:41 KSA, but the system extended the target twice instead of closing (F-14).
  - The last stored price at or above the lock level was 4.100 at 19:43:27.038 UTC / 22:43:27.038 KSA. The next stored price was 4.027 at 19:43:35.021 UTC / 22:43:35.021 KSA, 8 seconds later.
  - The system sold 25 ms after that update.
  - Binance's 19:43 UTC candle confirms a real low of 3.994 (F-25 [ER]). The exit followed the rules.

**137 EGLD**
- **Entry:** 156723 STRONG_BUY 91, a RANGE mean-reversion entry after eight 1m STRONG_SELLs. The range-location guard passed only through its expansion exception (RVOL 8.26) (F-18).
- **Entry Quality:** 58 = 70 − 12, with the rejected high 4.005 (156340, 17:40:01 UTC / 20:40:01 KSA).
- **Outcome:** the price got 47% of the way to the target. The SELL_CONFIRMED exit followed the rules.

## DATABASE EVIDENCE

All queries were read-only. Every query listed ran successfully unless noted. Result files show times in KSA (F-23).

| Query IDs | Tables actually queried | What they established |
|---|---|---|
| DBQ-FTS-001..005 | `paper_position`, `wallet_trade`, `information_schema` | Schema, the frozen six, the population (118 CLOSED, 75 STOPPED), and that `wallet_position_test` exists (Replay, excluded) |
| CLAUDE-I1-001..008, 010..012 (I1-009 **failed**: there is no `status` column) | `paper_position`, `trade_signal`, `wallet_trade`, `market_price_event`, `flyway_schema_history` | Trade rows, signals, fills with `fee_usdt = 0`, the excluded STOPPED losses (F-20), V107/V109 install dates (F-19) |
| CLAUDE-I2-001..009 | `trade_signal`, `wallet_trade`, `execution_opportunity`, `market_price_event`, `candle` (schema), `flyway_schema_history` | Entry authority signals, execution messages with `priceObservedAt`, routes, the 1m/5m generation race (I2-008), outcomes after exit |
| CLAUDE-I3-001..006 | session variables, `wallet_trade`, `candle`, `market_price_event`, `information_schema`, `execution_opportunity`, `trade_signal` | Session time zone and stored epochs (F-23), the 143 crash gap, write lag, audit-table schema, same-candle 5m mismatches (F-34) |
| CLAUDE-I4-001..004 | `buy_htf_assessment_audit`, `paper_position`, `execution_opportunity`, `trade_signal` | FIX-154 audit rows (176692 got a floored 50, row 2424). **The I4-002/003 statistics are invalid** because they excluded STOPPED positions (C-3). |
| CLAUDE-I5-001..003 | `trade_signal`, `paper_position`, `wallet_trade`, `execution_opportunity` | Windows for the 20-signal reference low/high, the open-position limit refuted, the Entry Quality band table (F-51) |
| CLAUDE-I6-001..003 | `trade_signal`, `execution_opportunity`, `paper_position` | ATR inputs, opportunity anchor/best prices (F-60), notionals for the fee analysis (F-61) |
| CLAUDE-I7-001 | `paper_position` | Entry-reason flags behind F-65, F-66, F-67 |
| CLAUDE-I7-002 | `trade_signal` (+ `SHOW COLUMNS`) | Route preconditions behind F-68 |

## LOG EVIDENCE

- **No application log evidence was examined.** The local logs (`logs/crypto-ai-trader.2026-10-1x.*.log.gz`) are gzip-compressed and my search tool cannot read them (F-30).
- The log searches requested in I3 to I6 (`signalId=167646`, `signalId=176692`, `FIX-11G post-guard result`, `BALANCED_EARLY_LATE_ENTRY_BLOCKED|BALANCED_EARLY_POST_BEARISH_RECOVERY_REQUIRED`, and EGLDUSDT between 19:43:20 and 19:43:40 UTC) produced **no results supplied to this track**.
- Coverage of 2026-10-11 UTC, which includes trades 137, 143 and 158, is unconfirmed.
- Log lines are written in UTC [PE, `logs/crypto-ai-trader.log:177`].

## CODE EVIDENCE [PE]

| Area | Reference |
|---|---|
| Entry Quality terms, floor, age truncation, window-high rule | `ExecutionIntelligenceService.java:871-1003` (age 950, window-high check 962, reward/risk 991, floor 995-1002) |
| Initial-entry cap and its explanation text | `ExecutionIntelligenceService.java:1183-1222` (rejection text 1208-1210, unchanged-decision return 1214) |
| Chase cut-off of 50 | `ExecutionIntelligenceService.java:229` |
| FIX-041 late BALANCED_EARLY block | `ExecutionIntelligenceService.java:672-684` |
| Post-bearish guard | `ExecutionIntelligenceService.java:2078-2137` |
| Accumulated route | `ExecutionIntelligenceService.java:1964-2067`; FIX-119 at 1228-1297 (comment 1232-1237) |
| Setup-timeframe ATR route (5m ATR; null if the 5m is not bullish) | `ExecutionIntelligenceService.java:1472-1514`, 634 |
| Wake-up routes | `ExecutionIntelligenceService.java:325, 364, 397-457` |
| FIX-056 recheck with the 1m ATR | `ExecutionIntelligenceService.java:1145-1219`, 858 |
| 5m selection by `generated_at` | `ExecutionIntelligenceService.java:2455-2462, 2602-2608`; `AnalysisService.java:150, 172-190` |
| Replay timeline and tie inclusion | `RegressionTestWorker.java:557-560, 584, 616, 787-793`; `ExecutionReplayScope.java:106-120` |
| FIX-11G (score ≥ 50, no confidence threshold) | `TradeExecutionValidationService.java:263-284` |
| `balancedBuy` uses labels only | `TradeExecutionValidationService.java:300-316` |
| Stop / target model | `AtrRiskService.java:35-45`; `application.yml:143` |
| Execution price age (15 s) | `ExecutionPriceAuthorityService.java:25, 46-48` |
| Paper entry text and open path | `PaperTradingService.java:296-414` (entry reason 411) |
| FIX-154 audit writer | `BuyHtfAssessmentAuditStore.java:38-53` |

## EXTERNAL RESEARCH [ER]

- **R-1:** Binance spot VIP 0 fees are commonly listed by third parties at 0.10% per side, or 0.075% with BNB. The official page was not retrieved. This study uses 0.15–0.20% per round trip.
- **R-2:** Java's `Duration.toMinutes()` drops the remainder (whole minutes only).
- **R-3:** Binance's public EGLDUSDT 1m klines for 2026-10-11 19:39–19:43 UTC match trade 143's prices, including the 19:43 low of 3.994 (F-23, F-25).

## Contradictions reported (not silently accepted)

- **X-1 [PE]:** AGENTS.md describes FIX-154 as not implemented. V107/V109 are installed, `validateBuy` calls `htfAssessment.assess`, and Production `buy_htf_assessment_audit` rows exist for 2026-10-11 and 10-12 (F-19, F-36, C-4).
- **X-2 [PE]:** `DATABASE_INVESTIGATION_RULES.md:35` says "raw MySQL timestamps are UTC". That is true of storage, but the study client's session displays them as UTC+3 (F-23).
- **L-1 [PE]:** the coordinator's snapshot labels contradict each other: UTC 10-22 10:28 is after KSA 10-21 12:50. The query log shows the queries ran at 2026-10-22 10:28 UTC.

## Suggested fixes (CANDIDATES ONLY; NO CHANGE made or authorised)

- **CF-1 (RC-1):** a minimum reward/risk after fees on all entry routes, plus fee modelling in paper fills.
- **CF-2 (RC-2):** stop the floored 50 from satisfying FIX-11G and the other non-accumulated routes (extend FIX-119's intent), and store Entry Quality components on every route, including wake-up.
- **CF-3 (RC-3):** apply FIX-041 and the post-bearish guard on the accumulated route. Do not count a BUY that a guard blocked toward FIX-119's "at least one BUY". Make `balancedBuy` respect the 5m `final_entry_allowed` flag. Make the FIX-056 recheck use the same ATR as the decision.
- **CF-4 (RC-4):** select the 5m signal by candle close identity, not `generated_at`, so Production matches Replay.
- **CF-5:** a guard for ATR smaller than one price tick (SHIB).
- **CF-6 (tooling, not the application):** the study client should set `time_zone='+00:00'`.

## Regression checks to propose [AI]

1. A Replay comparison of P&L after fees for reward/risk below 1 versus 1 or more.
2. A fixture where the rejected-high penalty would push a score from 50 to 38 on the FIX-11G route.
3. Run 167646 then 167668 in sequence. Expect the same guards on both routes.
4. A 1m and a 5m signal generated 2 ms apart (159408/159407). Expect Replay and Production to make the same choice, and trade 143 not to open.
5. An age of 20 min 59.976 s. Expect the same result in Replay and Production.
6. The FIX-056 recheck uses the same ATR as the decision.

## Unknowns / missing evidence

- **U-18:** which guard blocked 167646, and which execution step cancelled 176692. Only the logs can settle this.
- **U-25:** whether stop-outs of high-score trades come from position size or from entry selection. Out of scope.
- **U-27 (158 only):** the score is not persisted, so 60 at decision and 50 at execution are arithmetic reconstructions.
- **U-12:** why no price updates were stored for 8 seconds during trade 143's exit (ingestion code and logs not traced).
- **U-14:** no actual Replay run was performed. All Replay conclusions are [AI] from the code.
- **U-16:** why trading stopped after 2026-10-15 00:15 UTC / 03:15 KSA.
- **Fee schedule:** unverified. Paper fills record zero fees.

## Final self-review

- **Proven [PE]:**
  - The route, Entry Quality breakdown, freshness and exit compliance for all six trades.
  - Trade 182 depends on the floor.
  - Trade 162 has the route contradiction and passed only through the 24 ms age truncation; no rejected-high penalty applied.
  - Trade 143's superseded 5m authority, and that its stored 60 is the decision score.
  - Data freshness and execution delay are ruled out.
- **Inference [AI]:** which guard blocked 167646; why 176692 was cancelled; Replay non-reproduction of 143; the fee-adjusted magnitudes, which depend on the fee rate in R-1.
- **Classifications unchanged from Iteration 7.** Changes in this iteration: C-15 (162's text corroboration restored, F-65) and U-26, U-27 (for 143) and U-28 closed.

Sources:
- [Finhay: Binance trading fees (2026)](https://www.finhay.com.vn/en/phi-giao-dich-binance)
- [FAPAM: 2026 Binance exchange fees](https://virtualold.fapam.edu.br/binance-exchange-fees-s90f.html)
- [Binance klines, EGLDUSDT 1m, from 19:35 UTC](https://api.binance.com/api/v3/klines?symbol=EGLDUSDT&interval=1m&startTime=1791747300000&limit=12)


## SECTION 3 - COORDINATOR COMMON FINDINGS

The Coordinator is a comparator, not a third trading analyst. It does not choose a winner.

Both agents were required to analyze the same frozen IDs: 182, 172, 162, 158, 143, 137

Read sections 1 and 2 for root causes, entry verdicts, proof IDs, and suggested fixes.
Agreement is FULL only when both primary classifications match; otherwise STRONG/PARTIAL/NONE as stated by the two frozen reports. The Coordinator does not invent a third classification.

CANDIDATE IMPROVEMENT - NOT APPROVED. No implementation.
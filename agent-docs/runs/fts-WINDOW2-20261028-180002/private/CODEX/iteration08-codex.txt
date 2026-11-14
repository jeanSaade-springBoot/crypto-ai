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

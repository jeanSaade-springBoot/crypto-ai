# CODEX_FAILED_TRADE_STUDY.md

## Summary

| Field | Value |
|---|---|
| Track / iteration | CODEX / 8 of 8 — final self-review |
| Study window | WINDOW2 |
| Frozen IDs | 182, 172, 162, 158, 143, 137 |
| Authoritative entity | `crypto_ai.paper_position` |
| Final classification | **UNVERIFIABLE for all six trades** |
| Confirmed common defect | None |
| Changes made | None — analysis only |

Supplied snapshot metadata is retained verbatim: `2026-10-18 15:00:04 UTC`, supplied `2026-10-18 18:00:02 KSA`, intended window `2026-10-18 18:00:00 KSA`. Arithmetically, the UTC timestamp converts to `2026-10-18 18:00:04 KSA`; this two-second discrepancy is metadata-only and does not change the classification.

## Mandatory Trade Classification

| `paper_position.id` | Selection status | Entry | Exit / protection | P&L reconciliation | Final classification |
|---:|---|---|---|---|---|
| 182 | Mandated frozen ID; not independently DB-proven | Unverifiable | Unverifiable | Unverifiable | **UNVERIFIABLE** |
| 172 | Mandated frozen ID; not independently DB-proven | Unverifiable | Unverifiable | Unverifiable | **UNVERIFIABLE** |
| 162 | Mandated frozen ID; not independently DB-proven | Unverifiable | Unverifiable | Unverifiable | **UNVERIFIABLE** |
| 158 | Mandated frozen ID; not independently DB-proven | Unverifiable | Unverifiable | Unverifiable | **UNVERIFIABLE** |
| 143 | Mandated frozen ID; not independently DB-proven | Unverifiable | Unverifiable | Unverifiable | **UNVERIFIABLE** |
| 137 | Mandated frozen ID; not independently DB-proven | Unverifiable | Unverifiable | Unverifiable | **UNVERIFIABLE** |

## DATABASE EVIDENCE

**PROJECT EVIDENCE**

- The permitted Codex-only directory `agent-docs/runs/fts-WINDOW2-20261018-180002/db/CODEX/` exists and contains no result artifacts.
- No results for mandatory proof queries `DBQ-FTS-001` through `DBQ-FTS-005` are available in the permitted evidence scope.
- No database query was successfully available to this analysis. Therefore, none of these are proven for any ID: symbol, entry/close UTC time, realized loss amount, entry/exit signal IDs, close reason, wallet BUY/SELL records, fees, price-event identity, or Replay eligibility.
- The charter’s frozen selection is binding, but it is not independently re-proven without the authoritative query result.

**AGENT INFERENCE**

A negative `realized_pnl` in the selection rule would establish only inclusion in the failed-trade set. It would not establish a bad entry, missed stop, early profit-lock exit, stale data, execution latency, or accounting defect.

## LOG EVIDENCE

**PROJECT EVIDENCE**

- The inspected local application-log slice covers only `2026-10-17 00:11:41.424 UTC` through `2026-10-17 00:26:34.689 UTC`, equivalent to `2026-10-17 03:11:41.424 KSA` through `03:26:34.689 KSA`.
- Logback is configured to format application-file timestamps in UTC.
- No line in that slice identifies frozen `paper_position` IDs 182, 172, 162, 158, 143, or 137.
- No relevant FIX-129 stage-timing, block-begin, mechanical-exit, or FIX-124 protection-failure entry was found in that limited slice.

**AGENT INFERENCE**

This is inadequate coverage for the supplied snapshot on `2026-10-18`; the absence of an ID or error in the slice is not evidence that no execution, protection event, or delay occurred.

## CODE EVIDENCE

**PROJECT EVIDENCE — current working tree only, not historical-runtime proof**

- Current entry flow executes a wallet BUY using the fresh execution price before creating the paper position. [PaperTradingService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/service/PaperTradingService.java:379)
- Current paper-position creation stores the fresh execution price. [PaperTradingService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/service/PaperTradingService.java:407)
- Current direct-price evidence has a `PENDING` lifecycle and accepts canonical `LEGACY`/`APPLIED` evidence for Replay/price retrieval; an `APPLIED` callback still requires a correlated position/wallet mutation to prove an exit. [MarketPriceEventService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/market/service/MarketPriceEventService.java:50)
- The current transaction flow preserves input → protection → observer → close-dispatch ordering. [KlineTransactionCoordinator.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/infrastructure/transaction/KlineTransactionCoordinator.java:32)
- Current source supports multiple loss routes, including stop-loss, profit-lock, normal policy exit, and signal/wallet-driven closure. It does not identify which route applied to any frozen ID.

**AGENT INFERENCE**

Current code is useful only to define the evidence needed for reconstruction. It cannot prove the deployed version, configuration, runtime mode, or behavior at any of the six historical trade times.

## EXTERNAL RESEARCH

A prior iteration referenced Binance’s official kline-stream documentation for the closed-candle flag and UTC payload timing. It provides no trade-specific evidence and carries no weight in this final classification.

## Final Assessment

No confirmed valid loss, strategy defect, data-quality defect, execution failure, protection failure, or accounting discrepancy exists for any frozen trade. The only confirmed finding is an evidence-collection gap: the authorized DB evidence artifacts are absent and the available logs are not a complete trade-correlated record.

No trading-rule or code fix is justified. The only candidate follow-up is evidence acquisition and reconstruction before considering any implementation change.

### DB_REQUEST

QueryId: DBQ-CODEX-I8-001  
Purpose: Obtain deployed schema, the six authoritative lifecycle rows, and an independent proof of the frozen selection. Read-only, bounded, `crypto_ai` only.

SQL:
```sql
SELECT
    table_name,
    ordinal_position,
    column_name,
    column_type,
    is_nullable,
    column_key
FROM information_schema.columns
WHERE table_schema = 'crypto_ai'
  AND table_name IN (
      'paper_position',
      'trade_signal',
      'wallet_trade',
      'wallet_managed_position',
      'market_price_event',
      'production_exit_audit',
      'position_management_event',
      'signal_processing_work'
  )
ORDER BY table_name, ordinal_position
LIMIT 1000;

SELECT p.*
FROM crypto_ai.paper_position p
WHERE p.id IN (182, 172, 162, 158, 143, 137)
ORDER BY FIELD(p.id, 182, 172, 162, 158, 143, 137)
LIMIT 6;

SELECT id, status, closed_at, realized_pnl
FROM crypto_ai.paper_position
WHERE status = 'CLOSED'
  AND closed_at IS NOT NULL
  AND realized_pnl < 0
ORDER BY closed_at DESC
LIMIT 6;
```

### END_DB_REQUEST

Documentation-update determination: no update made; this was analysis-only and all writes were prohibited.

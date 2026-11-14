# CODEX_FAILED_TRADE_STUDY.md

## WINDOW1 — Iteration 8 Final Classification

### Summary

- Frozen subjects: `crypto_ai.paper_position` IDs `182, 172, 162, 158, 143, 137`.
- Classification: **INSUFFICIENT DURABLE EVIDENCE** for all six.
- Confirmed valid strategy losses: **0**.
- Confirmed execution/protection/accounting defects: **0**.
- Confirmed common root cause: **none**.
- Recommended implementation/configuration/trading-rule change: **NO CHANGE**.
- Database evidence files available to Codex: **none** under the permitted `db/CODEX` directory.
- External research: **none**.

The supplied snapshot timestamps conflict: `2026-10-25 10:08:57 UTC` converts to `2026-10-25 13:08:57 KSA`, not the supplied `2026-10-23 11:36:38 KSA`. The frozen IDs and supplied selection rule remain authoritative; neither snapshot clock is used as a precise cutoff.

| Frozen paper_position ID | DB lifecycle / P&L | Entry classification | Exit / protection classification | Final classification |
|---:|---|---|---|---|
| 182 | No permitted DB result | Unverifiable | An ICPUSDT managed-position `182` log candidate exists, but is not linked to paper position `182` | Insufficient durable evidence |
| 172 | No permitted DB result | Unverifiable | Unverifiable | Insufficient durable evidence |
| 162 | No permitted DB result | Unverifiable | Unverifiable | Insufficient durable evidence |
| 158 | No permitted DB result | Unverifiable | Unverifiable | Insufficient durable evidence |
| 143 | No permitted DB result | Unverifiable | Unverifiable | Insufficient durable evidence |
| 137 | No permitted DB result | Unverifiable | Unverifiable | Insufficient durable evidence |

## DATABASE EVIDENCE

### PROJECT EVIDENCE

The permitted directory `agent-docs/runs/fts-WINDOW1-20261023-113638/db/CODEX/` contains no result files. Consequently, none of proof queries `DBQ-FTS-001` through `DBQ-FTS-005` is available as Codex evidence.

The supplied selection is accepted as the frozen study input, but has not been independently re-executed:

```sql
status = 'CLOSED'
AND closed_at IS NOT NULL
AND realized_pnl < 0
ORDER BY closed_at DESC
LIMIT 6
```

Without row data, the study cannot establish symbols, UTC/KSA entry or exit times, loss amounts, signal links, close reasons, SL/TP, wallet executions, fees, partial exits, or accepted protection events.

### AGENT INFERENCE

A closed losing `paper_position` alone cannot prove net executed loss or a particular exit route. The conclusion could be a valid strategy loss, a paper/wallet reconciliation issue, a protection issue, or unavailable evidence.

### EXTERNAL RESEARCH

None.

### DB_REQUEST

QueryId: DBQ-CODEX-I8-001  
Purpose: Obtain the actual authoritative frozen rows and schema before attributing any entry, exit, P&L, or log evidence. All returned timestamps are UTC.

SQL:

```sql
SELECT
    table_name,
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
      'wallet_managed_position',
      'wallet_trade',
      'production_exit_audit',
      'market_price_event',
      'signal_processing_work'
  )
ORDER BY table_name, ordinal_position;

SELECT pp.*
FROM crypto_ai.paper_position pp
WHERE pp.id IN (182, 172, 162, 158, 143, 137)
ORDER BY FIELD(pp.id, 182, 172, 162, 158, 143, 137);

SELECT
    id,
    status,
    closed_at,
    realized_pnl
FROM crypto_ai.paper_position
WHERE status = 'CLOSED'
  AND closed_at IS NOT NULL
  AND realized_pnl < 0
ORDER BY closed_at DESC
LIMIT 6;
```

### END_DB_REQUEST

## LOG EVIDENCE

### PROJECT EVIDENCE

Archived-log investigation produced no durable paper-position linkage for any frozen ID.

There is one explicitly unlinked candidate:

- `WalletManagedPosition` ID `182`, symbol `ICPUSDT`.
- Observation: `2026-10-11 22:49:33.460 UTC` / `2026-10-12 01:49:33.460 KSA`.
- `Live STOP_LOSS executed` log: `2026-10-11 22:49:36.971 UTC` / `2026-10-12 01:49:36.971 KSA`.
- Event `1837489` commit log: `2026-10-11 22:49:36.974 UTC` / `2026-10-12 01:49:36.974 KSA`, outcome `APPLIED`.

The observed-to-commit interval is 3.514 seconds. It does not establish 3.514 seconds of processing delay because the observation time precedes transport, scheduling, and uninstrumented work.

This candidate must not be attributed to frozen `paper_position` `182`: numeric equality is not the durable association, and `APPLIED` may represent a committed protection callback without proving a particular paper-position close.

No exact frozen-position evidence was found for `172`, `162`, `158`, `143`, or `137`.

### AGENT INFERENCE

If the candidate linkage were later proven, the logs would support a normal stop-loss route, not a defect by themselves. It is presently inadmissible for trade classification.

### EXTERNAL RESEARCH

None.

## CODE EVIDENCE

### PROJECT EVIDENCE

Current source establishes the required forensic standard, but not the historical behavior of these six rows.

- A paper position contains lifecycle, price, risk-plan, signal-link, and P&L fields in [PaperPosition.java](C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/domain/PaperPosition.java:12).
- The durable paper-to-managed-position association is through the entry signal, not matching numeric IDs: [ProductionExitAuditService.java](C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/audit/service/ProductionExitAuditService.java:33).
- Direct-price protection rejects invalid/stale/out-of-order input before evaluation; a successful committed callback marks the price event `APPLIED`: [LivePositionProtectionService.java](C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/position/service/LivePositionProtectionService.java:57), [MarketPriceEventService.java](C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/market/service/MarketPriceEventService.java:86).
- A live stop-loss route updates wallet state before closing the linked paper position; the signal-close route has different ordering. Wallet-trade reconciliation is therefore mandatory: [LivePositionProtectionService.java](C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/position/service/LivePositionProtectionService.java:173), [PaperTradingService.java](C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/service/PaperTradingService.java:487).
- Initial-entry quality is checked at execution time, including fresh 5m/1h context and an entry-quality guard; the current managed-position score can be overwritten on later BUY stages: [TradeExecutionValidationService.java](C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/service/TradeExecutionValidationService.java:62), [ExecutionIntelligenceService.java](C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/execution/service/ExecutionIntelligenceService.java:229), [WalletAutoExecutionService.java](C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/wallet/service/WalletAutoExecutionService.java:139).

### AGENT INFERENCE

Current source cannot prove which source revision, configuration, or policy was active for any frozen historical loss.

## Final Outcome and Candidate Follow-up

No strategy, execution, protection, database, configuration, or code change is justified by this evidence set.

Candidate follow-up only: execute the requested bounded read-only DB evidence query, then prove each lifecycle as:

`paper_position → entry signal → managed position → wallet BUY/SELL → exit audit → accepted price-event sequence`.

Until that evidence exists, classifying any frozen loss as a valid loss or a defect would be unsupported.

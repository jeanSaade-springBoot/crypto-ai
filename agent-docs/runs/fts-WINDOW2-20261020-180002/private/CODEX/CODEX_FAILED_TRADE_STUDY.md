# CODEX_FAILED_TRADE_STUDY.md

## Frozen study summary

- **Window:** WINDOW2
- **Authoritative entity:** `crypto_ai.paper_position`
- **Frozen selection:** `status=CLOSED AND closed_at IS NOT NULL AND realized_pnl < 0 ORDER BY closed_at DESC LIMIT 6`
- **Frozen IDs:** 182, 172, 162, 158, 143, 137
- **Snapshot UTC:** `2026-10-20 15:00:04`
- **Snapshot KSA declared by charter:** `2026-10-20 18:00:02`
- **Timestamp note:** UTC `15:00:04` arithmetically converts to KSA `18:00:04`, a two-second metadata discrepancy. It does not alter the stipulated cohort.
- **Classification result:** 6/6 **INSUFFICIENT_EVIDENCE**.
- **Confirmed trade-specific defects:** 0.
- **Changes made:** None.

## Final classification

| Paper position ID | Frozen cohort status | Entry assessment | Exit/protection assessment | Final classification | Reason |
|---:|---|---|---|---|---|
| 182 | Frozen; no substitution | Unverifiable | Unverifiable | **INSUFFICIENT_EVIDENCE** | No permitted DB result identifies symbol, signal, execution, close reason, wallet trades, or accepted price event. |
| 172 | Frozen; no substitution | Unverifiable | Unverifiable | **INSUFFICIENT_EVIDENCE** | Same evidence gap. |
| 162 | Frozen; no substitution | Unverifiable | Unverifiable | **INSUFFICIENT_EVIDENCE** | Same evidence gap. |
| 158 | Frozen; no substitution | Unverifiable | Unverifiable | **INSUFFICIENT_EVIDENCE** | Same evidence gap. |
| 143 | Frozen; no substitution | Unverifiable | Unverifiable | **INSUFFICIENT_EVIDENCE** | Same evidence gap. |
| 137 | Frozen; no substitution | Unverifiable | Unverifiable | **INSUFFICIENT_EVIDENCE** | Same evidence gap. |

**PROJECT EVIDENCE:** No selected trade can responsibly be classified as a valid strategy loss, stale-data failure, protection failure, execution delay, or accounting divergence. A loss in `paper_position` alone does not establish any of those causes.

## DATABASE EVIDENCE

**PROJECT EVIDENCE:** The permitted `agent-docs/runs/fts-WINDOW2-20261020-180002/db/CODEX/` directory contains no result files. No results for `DBQ-FTS-001` through `DBQ-FTS-005` are available.

Therefore, the following facts are unavailable for every frozen ID:

- symbol, open/close times, entry/exit prices, stop, target, close reason, and P&L composition;
- linked entry/exit signals and signal-processing work;
- wallet BUY/SELL records, quantities, fees, and managed-position state;
- accepted protection evidence (`market_price_event.delivery_status IN ('LEGACY','APPLIED')`);
- exit audit evidence, candle/data-quality evidence, and runtime-version/configuration evidence.

### DB_REQUEST

QueryId: DBQ-CODEX-I8-001  
Purpose: Obtain actual schema evidence before any bounded reconstruction of the six frozen `paper_position` rows and their entry, protection, exit, and wallet evidence.  
SQL:

```sql
SELECT
    table_name,
    ordinal_position,
    column_name,
    data_type,
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
      'signal_processing_work',
      'position_management_event',
      'candle'
  )
ORDER BY table_name, ordinal_position;
```

### END_DB_REQUEST

## LOG EVIDENCE

**PROJECT EVIDENCE:** The earlier Codex log inventory found no workspace application log for `2026-10-20`. The newest available live-log write was UTC `2026-10-19 14:32:36` (KSA `2026-10-19 17:32:36`), at least 24h 27m 28s before the snapshot UTC time.

Consequently, logs cannot establish a timeline, processing delay, protection callback, wallet action, or exit cause for any frozen trade.

**EXTERNAL RESEARCH:** None relied on for this final classification.

## CODE EVIDENCE

**PROJECT EVIDENCE — current workspace source only; not proof of the runtime version for these trades:**

- Mechanical live-price exits call `executeMechanicalExit(...)` before `closePaper(...)`; a paper close follows only when the wallet mechanical exit reports success. [LivePositionProtectionService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/position/service/LivePositionProtectionService.java:165)
- Accepted protection evidence is limited to `market_price_event` rows in `LEGACY` or `APPLIED`; `PENDING`, `NON_CANONICAL`, and `PROTECTION_NOT_APPLIED` are not accepted trigger proof. [MarketPriceEventService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/market/service/MarketPriceEventService.java:123)
- Signal-linked closure persists the paper-position terminal state before invoking the wallet exit. [PaperTradingService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/service/PaperTradingService.java:501)
- That signal-linked wallet method may return without creating a SELL when the managed position is absent, non-positive, or has no sellable quantity. [WalletAutoExecutionService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/wallet/service/WalletAutoExecutionService.java:211)
- Current entry-quality logic assesses the fresh execution price, but position data does not itself preserve the complete quality calculation and its contemporaneous inputs. [ExecutionIntelligenceService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/execution/service/ExecutionIntelligenceService.java:853)

**AGENT INFERENCE:** The signal-linked closure ordering is a source-level reconciliation risk worth testing against wallet records. It is not evidence that it affected IDs 182, 172, 162, 158, 143, or 137.

## Missing evidence and candidate follow-up

**PROJECT EVIDENCE:** A defensible per-trade study requires the missing bounded DB evidence first, then a UTC/KSA timeline linking position, signal/work, wallet execution, accepted price event, and any exit audit.

**AGENT INFERENCE — candidate only, no change authorized:** If DB reconciliation identifies a paper close without its expected wallet SELL, investigate transactional/result-handling alignment between `completeClose()` and `executeSignalLinkedExit()`. Do not implement a change absent trade evidence and the required change-approval process.

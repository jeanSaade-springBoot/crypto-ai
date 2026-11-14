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

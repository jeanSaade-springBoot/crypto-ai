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

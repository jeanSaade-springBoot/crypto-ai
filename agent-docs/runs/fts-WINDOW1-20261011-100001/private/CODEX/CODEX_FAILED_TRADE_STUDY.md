# CODEX_FAILED_TRADE_STUDY

## Summary

| Field | Value |
|---|---|
| Study window | WINDOW1 |
| Status | Final classification — analysis only; no changes made |
| Frozen authoritative entity | `crypto_ai.paper_position` |
| Frozen IDs | 104, 101, 96, 91, 82, 78 |
| Selection contract | `status=CLOSED AND closed_at IS NOT NULL AND realized_pnl < 0 ORDER BY closed_at DESC LIMIT 6` |
| Exclusions | Open/incomplete, null `closed_at`, non-negative P&L, Replay `wallet_position_test` |
| Snapshot UTC | `2026-10-11 07:00:03` |
| Supplied snapshot KSA | `2026-10-11 10:00:01` |
| UTC→KSA check | `07:00:03 UTC` converts to `10:00:03 KSA`; supplied KSA timestamp differs by two seconds. Frozen scope is unchanged. |
| Overall conclusion | No selected loss can be causally classified because no CODEX-side DB evidence was supplied. |
| External research | None; it cannot establish facts about private paper-trading records. |

## Mandatory final classification

| Frozen `paper_position.id` | Classification | Confidence | What is established | What prevents a trade conclusion |
|---:|---|---|---|---|
| 104 | **INSUFFICIENT EVIDENCE** | High | It is a mandated member of the frozen study cohort. | No row, symbol, lifecycle, P&L, entry/exit signal, wallet, audit, price-event, candle, or log linkage. |
| 101 | **INSUFFICIENT EVIDENCE** | High | It is a mandated member of the frozen study cohort. | Same missing authoritative evidence. |
| 96 | **INSUFFICIENT EVIDENCE** | High | It is a mandated member of the frozen study cohort. | Same missing authoritative evidence. |
| 91 | **INSUFFICIENT EVIDENCE** | High | It is a mandated member of the frozen study cohort. | Same missing authoritative evidence. |
| 82 | **INSUFFICIENT EVIDENCE** | High | It is a mandated member of the frozen study cohort. | Same missing authoritative evidence. |
| 78 | **INSUFFICIENT EVIDENCE** | High | It is a mandated member of the frozen study cohort. | Same missing authoritative evidence. |

“Insufficient evidence” is not a finding that the trades were valid losses, strategy defects, stale-data incidents, execution failures, protection failures, or accounting errors.

## DATABASE EVIDENCE

**PROJECT EVIDENCE:** The permitted `agent-docs/runs/fts-WINDOW1-20261011-100001/db/CODEX/` evidence location contains no executed result artifacts.

Therefore, this study cannot independently verify that the six supplied IDs satisfied the snapshot predicate, nor establish any per-trade:

- symbol, entry/exit UTC time, KSA conversion, prices, quantity, fee, or P&L;
- entry or exit signal and signal freshness/data-quality context;
- wallet BUY/SELL effect or managed-position lifecycle;
- `production_exit_audit` record or close trigger;
- accepted `market_price_event` evidence for a protection exit;
- candle, indicator, or Replay linkage.

## LOG EVIDENCE

**PROJECT EVIDENCE:** In `logs/crypto-ai-trader.log`, the bounded near-snapshot interval `2026-10-11 06:35:00–07:00:03 UTC` (`09:35:00–10:00:03 KSA`) contains zero labelled matches of the form `paperPositionId`, `positionId`, `tradeId`, or `walletTradeId` for the frozen IDs.

The same interval includes:

- `2026-10-11 06:35:11.518 UTC` (`09:35:11.518 KSA`): exchange clock ahead of database/JVM by 3,132 ms; automatic correction active.
- `2026-10-11 06:35:04.779 UTC` (`09:35:04.779 KSA`): runtime summary reported zero input, pipeline, and protection “failed or unconfirmed” counters at that instant.
- Zero `ERROR` lines and zero `outcome=FAILED` lines in that bounded interval.

These are system-level observations only. Because the selected positions’ symbols and lifecycle times are unavailable, they cannot be attributed to any frozen loss and do not prove correct execution or protection.

## CODE EVIDENCE

**PROJECT EVIDENCE — source-level behavior, not historical trade proof:**

- [`PaperTradingService.java`](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/service/PaperTradingService.java:487) calculates paper P&L, marks the paper position terminal, and saves it before attempting the signal-linked wallet exit. Wallet exceptions are caught. A `CLOSED` paper row alone therefore does not prove an executed wallet SELL.
- [`LivePositionProtectionService.java`](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/position/service/LivePositionProtectionService.java:165) performs a mechanical wallet exit first for direct-price TP, stop, profit-lock, and normal signal-driven protection routes; it closes the paper position only on success.
- The current direct stop-loss path writes `STOPPED`, while several other routes write `CLOSED` ([`LivePositionProtectionService.java`](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/position/service/LivePositionProtectionService.java:173)). This does **not** prove any historic `CLOSED` row was not stop-related; actual `close_reason`, `exit_reason`, and audit data are required.
- [`ProductionExitAuditService.java`](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/audit/service/ProductionExitAuditService.java:33) records paper-position ID, close trigger, source signal, and a managed-position lookup. It must be reconciled with actual wallet ledger records.
- [`WalletTrade.java`](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/wallet/domain/WalletTrade.java:14) provides signal, position-analysis, price-observation, execution-reason, fee, and timestamp fields useful for reconciliation, but has no direct paper-position foreign key shown in current source.
- Current source may differ from the deployed version at the unknown entry/exit times. It is evidence of investigation requirements and risk boundaries, not proof of a defect for IDs 104/101/96/91/82/78.

## AGENT INFERENCE

The cohort may contain varied close routes, but assigning any route or cause without the actual rows would be speculation. No per-trade root cause is established.

## Candidate next actions — no change authorized

1. Execute the bounded read-only request below and preserve results in the CODEX evidence folder.
2. After schema confirmation, retrieve linked signal, wallet, managed-position, exit-audit, accepted-price-event, and contemporaneous candle evidence.
3. Only if a paper/wallet mismatch is proven, assess a candidate transactional/reconciliation correction. No implementation is proposed or authorized by this study.

### DB_REQUEST
QueryId: DBQ-CODEX-I8-001  
Purpose: Establish the actual deployed schemas and authoritative frozen position rows before any linked forensic query. This independently tests the frozen selection and avoids assuming columns or relationships. All returned timestamps are UTC.  
SQL:

```sql
SHOW CREATE TABLE crypto_ai.paper_position;
SHOW CREATE TABLE crypto_ai.trade_signal;
SHOW CREATE TABLE crypto_ai.wallet_trade;
SHOW CREATE TABLE crypto_ai.wallet_managed_position;
SHOW CREATE TABLE crypto_ai.production_exit_audit;
SHOW CREATE TABLE crypto_ai.position_analysis;
SHOW CREATE TABLE crypto_ai.signal_processing_work;
SHOW CREATE TABLE crypto_ai.market_price_event;
SHOW CREATE TABLE crypto_ai.candle;

SELECT p.*
FROM crypto_ai.paper_position AS p
WHERE p.id IN (104, 101, 96, 91, 82, 78)
ORDER BY FIELD(p.id, 104, 101, 96, 91, 82, 78);

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

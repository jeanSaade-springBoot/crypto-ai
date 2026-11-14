# CODEX_FAILED_TRADE_STUDY.md

## Final classification — WINDOW1 / Iteration 8 of 8

**PROJECT EVIDENCE:** Analysis-only; no code, configuration, database, Git, deployment, or documentation change occurred.

| Field | Value |
|---|---|
| Authoritative entity | `crypto_ai.paper_position` |
| Frozen IDs | `182, 172, 162, 158, 143, 137` |
| Snapshot UTC | `2026-10-17 07:00:04` |
| Supplied snapshot KSA | `2026-10-17 10:00:03` |
| Selection | `status=CLOSED AND closed_at IS NOT NULL AND realized_pnl < 0 ORDER BY closed_at DESC LIMIT 6` |
| Final outcome | No individual loss can be classified beyond **UNVERIFIABLE—missing durable trade evidence**. |
| Confirmed common root cause | None. |
| Confirmed defect among frozen trades | None. |

**AGENT INFERENCE:** `2026-10-17 07:00:04 UTC` arithmetically converts to `10:00:04 KSA`; this differs by one second from the supplied KSA snapshot. Both supplied values are preserved.

## Mandatory frozen-trade table

| Paper position ID | Frozen-selection status | Lifecycle / P&L | Entry assessment | Exit / protection assessment | Final classification |
|---:|---|---|---|---|---|
| 182 | Selected by supplied snapshot | Unknown; no row supplied | Unverifiable | Unverifiable | **UNVERIFIABLE—missing DB evidence** |
| 172 | Selected by supplied snapshot | Unknown; no row supplied | Unverifiable | Unverifiable | **UNVERIFIABLE—missing DB evidence** |
| 162 | Selected by supplied snapshot | Unknown; no row supplied | Unverifiable | Unverifiable | **UNVERIFIABLE—missing DB evidence** |
| 158 | Selected by supplied snapshot | Unknown; no row supplied | Unverifiable | Unverifiable | **UNVERIFIABLE—missing DB evidence** |
| 143 | Selected by supplied snapshot | Unknown; no row supplied | Unverifiable | Unverifiable | **UNVERIFIABLE—missing DB evidence** |
| 137 | Selected by supplied snapshot | Unknown; no row supplied | Unverifiable | Unverifiable | **UNVERIFIABLE—missing DB evidence** |

## DATABASE EVIDENCE

**PROJECT EVIDENCE:** `agent-docs/runs/fts-WINDOW1-20261017-100003/db/CODEX/` contains no files. No results for `DBQ-FTS-001..005` are available to Codex.

Therefore unavailable for every frozen ID:

- symbol, side, entry/close UTC timestamps and KSA conversions;
- entry/exit price, quantity, stop, target, loss amount, fees, partial exits;
- entry/exit signal linkage and decision evidence;
- wallet BUY/SELL success and reconciliation;
- `production_exit_audit`, accepted price-event, and protection-trigger evidence;
- deployed version/configuration at the time of trade.

A subsequent selection query could compare the current database state, but cannot independently prove the historic snapshot selection without durable snapshot evidence.

### DB_REQUEST

QueryId: DBQ-CODEX-I8-001  
Purpose: Establish the physical schema, retrieve exactly the frozen positions, and compare the current predicate result without misrepresenting it as historic snapshot proof.  
SQL:
```sql
SHOW CREATE TABLE crypto_ai.paper_position;

SELECT *
FROM crypto_ai.paper_position
WHERE id IN (182, 172, 162, 158, 143, 137)
ORDER BY FIELD(id, 182, 172, 162, 158, 143, 137);

SELECT *
FROM (
    SELECT *
    FROM crypto_ai.paper_position
    WHERE status = 'CLOSED'
      AND closed_at IS NOT NULL
      AND realized_pnl < 0
    ORDER BY closed_at DESC
    LIMIT 6
) AS current_selection_check
ORDER BY closed_at DESC;

SELECT table_name,
       ordinal_position,
       column_name,
       column_type,
       is_nullable,
       column_key
FROM information_schema.columns
WHERE table_schema = 'crypto_ai'
  AND table_name IN (
      'trade_signal',
      'wallet_trade',
      'wallet_managed_position',
      'market_price_event',
      'production_exit_audit',
      'position_management_event',
      'signal_processing_work'
  )
ORDER BY table_name, ordinal_position;
```
### END_DB_REQUEST

## LOG EVIDENCE

**PROJECT EVIDENCE:** Application log files exist locally, but no targeted content search was performed because no frozen trade symbol or UTC lifecycle was available. A broad search would not establish trade attribution.

Current log configuration formats log timestamps in UTC. Application-file delivery is asynchronous and configured to never block; file retention is bounded to seven days / 256 MB. Thus absence of a relevant line would not prove an event did not occur. [logback-spring.xml](/C:/workspace/crypto-ai-trader-mysql/src/main/resources/logback-spring.xml:24)

## CODE EVIDENCE

**PROJECT EVIDENCE:** Current source supports several possible terminal routes: live mechanical TP/SL/profit-lock/confirmed-signal exits, signal-linked exits, and manual closure. Current source is not proof of the historical build/configuration that processed any frozen trade.

- `paper_position` carries lifecycle, prices, cumulative realized P&L, entry/exit signal links, close reason, and UTC timestamps. [PaperPosition.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/domain/PaperPosition.java:10)
- Direct protection processes canonical 1m input before protection, then observer and close dispatch. [KlineTransactionCoordinator.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/infrastructure/transaction/KlineTransactionCoordinator.java:36)
- A direct price event is accepted durable protection evidence only as `APPLIED`/legacy evidence; `PENDING`, `NON_CANONICAL`, and `PROTECTION_NOT_APPLIED` do not prove a committed effect. [MarketPriceEventService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/market/service/MarketPriceEventService.java:82)
- In the live protection route, a mechanical wallet exit must succeed before `paper_position` is closed. [LivePositionProtectionService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/position/service/LivePositionProtectionService.java:165)
- In the signal-linked close route, the paper position is saved and an exit audit is written even if the wallet-exit call throws and is caught. This is a source-level accounting/execution-mismatch risk hypothesis—not evidence that any frozen trade experienced it. [PaperTradingService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/service/PaperTradingService.java:487)
- Terminal realized P&L can include an earlier partial harvest, so a terminal position row alone cannot fully reconcile P&L. [LivePositionProtectionService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/position/service/LivePositionProtectionService.java:391)

## Classification and candidate follow-up

**PROJECT EVIDENCE:** A loss alone does not prove bad entry quality, stale data, a protection failure, or an accounting defect.

**AGENT INFERENCE:** No implementation change is justified from this evidence set. If durable evidence later shows a closed paper position/audit without a successful corresponding wallet SELL, classify that as an accounting/execution consistency defect and separately assess whether the signal-linked close ordering should be changed.

**EXTERNAL RESEARCH:** None used for the final classifications.

Required evidence to classify any frozen trade: its authoritative position row, linked signals, wallet executions, exit audit, accepted price events, relevant candles, and symbol/time-bounded application logs.

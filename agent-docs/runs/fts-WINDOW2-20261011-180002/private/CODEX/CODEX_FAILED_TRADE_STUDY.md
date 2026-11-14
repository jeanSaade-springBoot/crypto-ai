# CODEX_FAILED_TRADE_STUDY.md

## Final classification — WINDOW2

- **[PROJECT EVIDENCE]** Frozen scope: `paper_position` IDs `104, 101, 96, 91, 82, 78`; no substitutions.
- **[PROJECT EVIDENCE]** Charter selection: `status=CLOSED`, `closed_at IS NOT NULL`, `realized_pnl < 0`, newest six; Replay `wallet_position_test` excluded.
- **[PROJECT EVIDENCE]** The permitted `db/CODEX/` directory contains no result artifacts. No database query was executed for this Codex track.
- **[AGENT INFERENCE]** Final classification for every frozen ID is **INSUFFICIENT DURABLE EVIDENCE**. The supplied evidence does not support a trade-specific verdict of valid strategy loss, entry defect, protection defect, execution failure, accounting discrepancy, or latency failure.
- **[EXTERNAL RESEARCH]** None used.
- **[PROJECT EVIDENCE]** No change is authorized or made.

Snapshot metadata is retained as supplied: UTC `2026-10-11 15:00:04`; stated KSA `2026-10-11 18:00:02`. **[AGENT INFERENCE]** UTC +03:00 computes to KSA `2026-10-11 18:00:04`, a two-second discrepancy that does not alter the frozen IDs.

| Frozen ID | Database proof available | Entry assessment | Exit / P&L assessment | Final classification |
|---:|---|---|---|---|
| 104 | None | Unverifiable | An EDUUSDT wallet-managed-position `104` log sequence is a candidate only; it is not proven to be `paper_position.id=104`. No wallet-fill or audit reconciliation. | **INSUFFICIENT DURABLE EVIDENCE** |
| 101 | None | Unverifiable | No linked trigger, price-event, audit, or wallet evidence. | **INSUFFICIENT DURABLE EVIDENCE** |
| 96 | None | Unverifiable | No linked trigger, price-event, audit, or wallet evidence. | **INSUFFICIENT DURABLE EVIDENCE** |
| 91 | None | Unverifiable | No linked trigger, price-event, audit, or wallet evidence. | **INSUFFICIENT DURABLE EVIDENCE** |
| 82 | None | Unverifiable | No linked trigger, price-event, audit, or wallet evidence. | **INSUFFICIENT DURABLE EVIDENCE** |
| 78 | None | Unverifiable | No linked trigger, price-event, audit, or wallet evidence. | **INSUFFICIENT DURABLE EVIDENCE** |

## DATABASE EVIDENCE

- **[PROJECT EVIDENCE]** No `DBQ-FTS-001` … `DBQ-FTS-005` result is present under the permitted `db/CODEX/` path.
- **[PROJECT EVIDENCE]** Therefore, the study lacks the authoritative records needed to verify the charter’s asserted selection per row, including symbol, open/close UTC timestamps, P&L, entry/exit signals, close reason, and quantity.
- **[PROJECT EVIDENCE]** It also lacks `production_exit_audit`, `wallet_managed_position`, `wallet_trade`, `trade_signal`, and `market_price_event` records required to prove execution, protection, and accounting.
- **[AGENT INFERENCE]** The absence of this evidence is not evidence that an execution, wallet effect, or protection callback did not occur.

## LOG EVIDENCE

- **[PROJECT EVIDENCE]** Prior Codex log extraction found an EDUUSDT profit-lock sequence for a log field `positionId=104`:
  - UTC `2026-10-10 14:45:22.035` / KSA `17:45:22.035`: price `0.071000000000` breached lock `0.071030966079`.
  - UTC `2026-10-10 14:45:44.016` / KSA `17:45:44.016`: live route logged a protected hold with `1h=BUY`, `5m=WATCH`, `current=WATCH`.
  - UTC `2026-10-10 14:46:01.300` / KSA `17:46:01.300`: live 1m signal `115326` saved.
  - UTC `2026-10-10 14:46:01.312` / KSA `17:46:01.312`: signal path logged a profit-lock trigger at `0.070900000000`.
  - UTC `2026-10-10 14:46:02.227` / KSA `17:46:02.227`: symbol-lock wait `207 ms`; transaction completion logged at UTC `14:46:02.235` / KSA `17:46:02.235`.
- **[PROJECT EVIDENCE]** The log sequence itself says that signal persistence does not imply a wallet trade.
- **[AGENT INFERENCE]** The roughly 39.3-second interval is not a latency finding: it spans different route decisions and is not causally explained by the 207-ms wait.
- **[PROJECT EVIDENCE]** Current source shows that the `positionId` printed by the profit-lock path is a `walletPositionId`; no durable join proves it is the frozen `paper_position.id=104`. Thus the sequence cannot classify trade 104.

## CODE EVIDENCE

- **[PROJECT EVIDENCE]** [`PaperTradingService.java`](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/service/PaperTradingService.java:176) handles an authoritative signal-path profit-lock trigger directly, logging `profitLock.walletPositionId()` as `positionId`.
- **[PROJECT EVIDENCE]** The same service saves paper-position closure before attempting the wallet exit and catches wallet exceptions. [`PaperTradingService.java`](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/service/PaperTradingService.java:457)
- **[PROJECT EVIDENCE]** [`LivePositionProtectionService.java`](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/position/service/LivePositionProtectionService.java:173) attempts mechanical wallet exit before closing paper state; a breached profit lock is evaluated through `PositionExitPolicy`. [`PositionExitPolicy.java`](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/position/service/PositionExitPolicy.java:52)
- **[PROJECT EVIDENCE]** The live policy can hold a breached lock with bullish 1h and supportive 5m context; the signal path shown above does not use that policy.
- **[PROJECT EVIDENCE]** [`ProductionExitAudit.java`](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/audit/domain/ProductionExitAudit.java:9) is diagnostic evidence, not proof of committed financial effect.
- **[PROJECT EVIDENCE]** [`WalletTrade.java`](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/wallet/domain/WalletTrade.java:22) retains price, quantity, fees, realized P&L, execution reason, status, and time—the missing evidence required for reconciliation.
- **[AGENT INFERENCE]** Current source describes possible behavior, not the historical deployed revision/configuration for any frozen trade.

## Candidate follow-up fixes — not authorized

- **[AGENT INFERENCE]** Candidate: persist/query a durable position-to-wallet-position-to-audit linkage and require explicit reconciliation reporting for closed paper positions.
- **[AGENT INFERENCE]** Candidate: evaluate whether signal-path and live-path profit-lock decisions should share one policy authority; only Replay and historical evidence can establish whether divergence is harmful.
- **[AGENT INFERENCE]** Candidate regressions: wallet-exit exception after paper close, partial-realization P&L reconciliation, linked price-event-to-exit proof, and route-parity tests.

### DB_REQUEST

QueryId: DBQ-CODEX-I8-001  
Purpose: Establish actual schema, database timezone rendering, and authoritative lifecycle rows for the exact frozen IDs before any trade-specific reclassification.  

SQL:

```sql
SELECT
    @@session.time_zone AS session_time_zone,
    @@global.time_zone AS global_time_zone,
    @@system_time_zone AS system_time_zone;

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
      'production_exit_audit',
      'wallet_managed_position',
      'wallet_trade',
      'trade_signal',
      'market_price_event',
      'position_management_event',
      'signal_processing_work'
  )
ORDER BY table_name, ordinal_position;

SELECT *
FROM crypto_ai.paper_position
WHERE id IN (104, 101, 96, 91, 82, 78)
ORDER BY FIELD(id, 104, 101, 96, 91, 82, 78);
```

### END_DB_REQUEST

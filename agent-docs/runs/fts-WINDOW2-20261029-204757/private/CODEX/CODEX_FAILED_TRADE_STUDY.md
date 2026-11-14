# CODEX_FAILED_TRADE_STUDY.md

## Summary

- **Study:** WINDOW2 daily failed-trade study, iteration 8/8 final classification.
- **Frozen scope:** `paper_position` IDs **182, 172, 162, 158, 143, 137** only.
- **Authority and selection:** `crypto_ai.paper_position`; stated selection is `CLOSED`, non-null `closed_at`, negative `realized_pnl`, newest six by `closed_at`. Replay table `wallet_position_test` is excluded.
- **Final classification:** **INSUFFICIENT DURABLE EVIDENCE — no individual loss classification is justified.**
- **Database evidence:** No Codex-permitted DB result exists in `db/CODEX/`; no DB facts are asserted.
- **Log evidence:** No exact frozen paper-position ID match was found in the current uncompressed `crypto-ai-trader.log` or `database-timing.log`. This is not evidence that a trade did not occur.
- **External research:** None used.
- **Change status:** Analysis only. **NO CHANGE** to code, configuration, database, Git, deployment, or documentation.

The supplied snapshot clocks conflict: `2026-10-30 12:24:16 UTC` converts to `2026-10-30 15:24:16 KSA`, not the supplied snapshot value `2026-10-29 20:47:57 KSA`. The intended clock, `2026-10-29 18:00:00 KSA`, was not preserved. The six IDs remain frozen scope, but no exact clock-window conclusion is valid.

## Mandatory frozen-trade classification

| Frozen ID | Lifecycle / P&L | Entry quality | Exit / protection | Final classification |
|---:|---|---|---|---|
| 182 | Not returned in permitted DB evidence | Unverifiable | Unverifiable | Insufficient durable evidence |
| 172 | Not returned in permitted DB evidence | Unverifiable | Unverifiable | Insufficient durable evidence |
| 162 | Not returned in permitted DB evidence | Unverifiable | Unverifiable | Insufficient durable evidence |
| 158 | Not returned in permitted DB evidence | Unverifiable | Unverifiable | Insufficient durable evidence |
| 143 | Not returned in permitted DB evidence | Unverifiable | Unverifiable | Insufficient durable evidence |
| 137 | Not returned in permitted DB evidence | Unverifiable | Unverifiable | Insufficient durable evidence |

No symbol, UTC lifecycle timestamps, KSA conversions, entry/exit signal IDs, close trigger, accepted protection observation, wallet execution, fee data, or historical configuration/version has been returned for any frozen ID. Consequently, none may be called a valid strategy loss, stop-loss defect, execution-delay failure, protection failure, or accounting discrepancy.

## DATABASE EVIDENCE

**PROJECT EVIDENCE:** The permitted directory `agent-docs/runs/fts-WINDOW2-20261029-204757/db/CODEX/` contains no result files. I did not read coordinator or other-agent database folders under the isolation rule.

**AGENT INFERENCE:** The charter’s frozen selection defines scope, but without the preserved snapshot state or returned proof-query results it does not independently prove that these were the six newest qualifying rows at the stated time.

Required trade-level proof remains:

1. `paper_position` lifecycle, entry/exit prices, P&L, signal links, stop/target, and close fields.
2. `production_exit_audit` terminal trigger.
3. Managed-position and `wallet_trade` BUY/SELL records, quantities, prices, fees, and execution messages.
4. Accepted `market_price_event` records (`LEGACY`/`APPLIED`) around the holding period.
5. Signal/work/candle evidence and historical deployment/configuration evidence.

### DB_REQUEST

QueryId: DBQ-CODEX-I8-001  
Purpose: Establish actual schema and the authoritative lifecycle/selection facts for exactly the six frozen positions before any loss classification. Raw timestamps returned by this query are UTC.

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
      'production_exit_audit',
      'wallet_managed_position',
      'wallet_trade',
      'market_price_event',
      'trade_signal',
      'signal_processing_work',
      'position_analysis',
      'position_management_event'
  )
ORDER BY table_name, ordinal_position;

SELECT id, status, closed_at, realized_pnl
FROM crypto_ai.paper_position
WHERE id IN (182, 172, 162, 158, 143, 137)
ORDER BY FIELD(id, 182, 172, 162, 158, 143, 137);

SELECT id, status, closed_at, realized_pnl
FROM crypto_ai.paper_position
WHERE status = 'CLOSED'
  AND closed_at IS NOT NULL
  AND realized_pnl < 0
ORDER BY closed_at DESC
LIMIT 6;
```

### END_DB_REQUEST

## LOG EVIDENCE

**PROJECT EVIDENCE:** A bounded exact-ID search of the uncompressed current `logs/crypto-ai-trader.log` and `logs/database-timing.log` found no `positionId=` or `paperPositionId=` entry for IDs `182, 172, 162, 158, 143, 137`.

This does not establish absence of execution or failure. The local logs do not establish the relevant dates, symbols, managed-wallet position IDs, or archive coverage. The compressed archives cannot be attributed safely before database linkage supplies those facts.

## CODE EVIDENCE

**PROJECT EVIDENCE — current source only; historical deployment applicability is unknown:**

- `paper_position` is the authoritative paper lifecycle record and is closed with price, P&L, reason, and signal context in `PaperTradingService`. Current-source inspection shows `completeClose(...)` persists the paper close before calling `executeSignalLinkedExit(...)`.  
  [PaperTradingService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/service/PaperTradingService.java:487)

- The current profit-lock path similarly saves the paper close before calling `executeProfitLock(...)`.  
  [PaperTradingService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/service/PaperTradingService.java:452)

- The signal-linked wallet exit method has normal early-return paths; therefore source order alone cannot prove that a closed paper row has a matching SELL. Actual `wallet_trade` and managed-position records are required.  
  [WalletAutoExecutionService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/wallet/service/WalletAutoExecutionService.java:211)

- The direct live-price route marks an event `APPLIED` after the protection callback completes. `APPLIED` is useful protection-path evidence, but alone does not prove that a particular paper/wallet position existed or closed.  
  [LivePositionProtectionService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/position/service/LivePositionProtectionService.java:71)  
  [MarketPriceEventService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/market/service/MarketPriceEventService.java:123)

- `production_exit_audit` is the strongest intended terminal-cause record because it records a distinct `closeTrigger`; it is diagnostic evidence, not settlement proof.  
  [ProductionExitAuditService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/audit/service/ProductionExitAuditService.java:33)

**AGENT INFERENCE:** The current code exposes a reconciliation risk for a trade that proves to have used a signal-linked or profit-lock close and lacks a corresponding wallet SELL. It is not evidence that any frozen trade has that defect.

## Candidate follow-up only — NO CHANGE

If returned DB evidence proves a paper-only close on the signal/profit-lock route, a separately governed change candidate would be to make the wallet-execution outcome explicit and reconcile it atomically with the paper close. Regression coverage would need normal no-op returns, duplicate execution keys, exceptions/rollback, and direct-price-route parity.

Until the requested durable evidence is returned, no trade-specific fix is justified.

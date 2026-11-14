# CODEX_FAILED_TRADE_STUDY.md

## Summary

- Study window: `WINDOW2`
- Authoritative entity: `crypto_ai.paper_position`
- Frozen trade IDs: `182, 172, 162, 158, 143, 137`
- Frozen selection: `status=CLOSED AND closed_at IS NOT NULL AND realized_pnl < 0`, descending close time, limit 6.
- Snapshot: UTC `2026-10-17 15:00:03`; supplied KSA label `2026-10-17 18:00:02`.
- Time note: UTC `15:00:03` mathematically converts to KSA `18:00:03`; the supplied one-second KSA discrepancy does not alter the frozen IDs.
- Final classification: **INSUFFICIENT_EVIDENCE for all six trades.**
- Changes made: **NONE** — analysis only.

| Paper position ID | Frozen-population fact | Trade-level classification | What is not proven |
|---:|---|---|---|
| 182 | CLOSED with negative `realized_pnl` at the frozen snapshot | **INSUFFICIENT_EVIDENCE** | Symbol, entry/exit times, trigger, price event, source signals, fee-adjusted P&L, and causality |
| 172 | CLOSED with negative `realized_pnl` at the frozen snapshot | **INSUFFICIENT_EVIDENCE** | Same |
| 162 | CLOSED with negative `realized_pnl` at the frozen snapshot | **INSUFFICIENT_EVIDENCE** | Same |
| 158 | CLOSED with negative `realized_pnl` at the frozen snapshot | **INSUFFICIENT_EVIDENCE** | Same |
| 143 | CLOSED with negative `realized_pnl` at the frozen snapshot | **INSUFFICIENT_EVIDENCE** | Same |
| 137 | CLOSED with negative `realized_pnl` at the frozen snapshot | **INSUFFICIENT_EVIDENCE** | Same |

No selected trade can be called a valid strategy loss, stale-data loss, late-entry loss, protection defect, execution-delay defect, or accounting defect from the evidence supplied to the Codex track.

## Database Evidence

**PROJECT EVIDENCE:** `agent-docs/runs/fts-WINDOW2-20261017-180002/db/CODEX/` is empty. No results for `DBQ-FTS-001` through `DBQ-FTS-005`, nor any Codex-specific DB result, are available.

Consequently, none of the following is evidenced for an individual frozen trade:

- `paper_position` lifecycle fields beyond the frozen selection predicate;
- linked entry/exit signals;
- `production_exit_audit.close_trigger`;
- accepted `market_price_event` and its UTC `observed_at`;
- managed-wallet position, executions, fees, or partial-harvest history;
- candle/context quality and signal age at the actual observation time.

`paper_position.closed_at` must not substitute for the protection observation time: signals generated after the accepted observation but before close commit could otherwise look contemporaneous.

### DB_REQUEST

QueryId: DBQ-CODEX-I8-001  
Purpose: Obtain bounded schema evidence before requesting the six-trade timeline, terminal audit, price-event, and wallet reconciliation. No write or unbounded data read is requested.

SQL:

```sql
SELECT
    c.table_name,
    c.ordinal_position,
    c.column_name,
    c.column_type,
    c.is_nullable,
    c.column_key,
    k.referenced_table_name,
    k.referenced_column_name
FROM information_schema.columns c
LEFT JOIN information_schema.key_column_usage k
    ON k.table_schema = c.table_schema
   AND k.table_name = c.table_name
   AND k.column_name = c.column_name
   AND k.referenced_table_name IS NOT NULL
WHERE c.table_schema = 'crypto_ai'
  AND c.table_name IN (
      'paper_position',
      'production_exit_audit',
      'market_price_event',
      'trade_signal',
      'wallet_managed_position',
      'wallet_trade',
      'position_management_event'
  )
ORDER BY c.table_name, c.ordinal_position;
```

### END_DB_REQUEST

## Log Evidence

**PROJECT EVIDENCE:** Earlier Codex-side inspection found no explicit frozen position-ID matches in the searched uncompressed runtime logs. This is not proof that trade logs do not exist.

A reliable targeted search needs the database-derived symbol, UTC entry/close range, signal IDs, and event IDs. Archived logs were not sufficient to establish trade attribution without those anchors.

**PROJECT EVIDENCE:** Current logging supports reconstruction once linked records exist: candle-analysis worker timing, signal processing claim/completion, saved-signal timing, and price-event completion are logged. Ordinary protection paths may log symbol and price without a paper-position ID, making DB correlation essential.

## Code Evidence

**PROJECT EVIDENCE:** The current direct protection route validates the incoming price event’s `observedAt` for freshness/order, then invokes an overload that selects the newest 1m, 5m, and 1h signals without an `at-or-before observedAt` restriction or explicit context-age limit. [LivePositionProtectionService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/position/service/LivePositionProtectionService.java:54) [LivePositionProtectionService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/position/service/LivePositionProtectionService.java:95)

**PROJECT EVIDENCE:** The repository already exposes an at-or-before generated-time query, but this protection path uses the unconstrained latest-signal query. [TradeSignalRepository.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/repository/TradeSignalRepository.java:23)

**PROJECT EVIDENCE:** Those contexts influence `HTF_SELL_CONFIRMED`, `SELL_CONFIRMED`, and profit-lock exit/hold decisions. [PositionExitPolicy.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/position/service/PositionExitPolicy.java:20)

**AGENT INFERENCE:** This is a real cross-cutting temporal-boundary weakness and a possible causal candidate only if a frozen trade’s audit shows a context-dependent exit. It is not evidence that it caused any of these six losses.

**PROJECT EVIDENCE:** Fixed stop-loss closures are stored as `STOPPED`; the frozen `CLOSED` selection intentionally excludes them. This is a population limitation, not a demonstrated defect.

## External Research

**EXTERNAL RESEARCH:** Prior iteration research only confirmed generic Binance kline timestamp/closed-event semantics. It does not establish entry quality, exit cause, latency, or a defect for any frozen trade.

## Final Assessment and Candidate Follow-up

- Trade-specific root cause: **not established** for IDs `182, 172, 162, 158, 143, 137`.
- Confirmed source-level concern: exit-context selection is not bounded to the accepted price-event observation time.
- Suggested fix candidates only, not authorized changes:
  1. Select 1m/5m/1h exit context at or before the accepted `observedAt` and enforce interval-specific freshness.
  2. Persist or durably correlate the closing price-event ID and all selected context signal IDs in exit audit evidence.
  3. Add tests for delayed processing, future-generated context exclusion, stale context, and Replay parity.

No code, configuration, database, Git, deployment, or documentation change was made.

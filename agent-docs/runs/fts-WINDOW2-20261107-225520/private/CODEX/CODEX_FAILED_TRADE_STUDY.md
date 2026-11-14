# CODEX_FAILED_TRADE_STUDY.md

## Final classification

Study window: `WINDOW2`  
Authoritative entity: `crypto_ai.paper_position`  
Frozen IDs: `182, 172, 162, 158, 143, 137`  
Selection basis: `status=CLOSED AND closed_at IS NOT NULL AND realized_pnl < 0`, ordered by `closed_at DESC`, limit 6.

**Final result: all six trades are `INSUFFICIENT EVIDENCE — UNCLASSIFIED`.** No trade-specific root cause, entry-quality judgment, exit-route finding, protection failure, timing failure, accounting discrepancy, or confirmed defect is established.

Snapshot caveat: supplied UTC `2026-11-07 19:55:22` mathematically converts to KSA `2026-11-07 22:55:22`, while the supplied KSA snapshot is `2026-11-07 22:55:20`. The frozen IDs remain authoritative; the exact intended-window clock was not preserved.

| Frozen ID | Charter classification | Trade-specific durable evidence available | Final classification |
|---:|---|---|---|
| 182 | Selected failed trade | None in Codex-scoped DB/log evidence | Insufficient evidence — unclassified |
| 172 | Selected failed trade | None in Codex-scoped DB/log evidence | Insufficient evidence — unclassified |
| 162 | Selected failed trade | None in Codex-scoped DB/log evidence | Insufficient evidence — unclassified |
| 158 | Selected failed trade | None in Codex-scoped DB/log evidence | Insufficient evidence — unclassified |
| 143 | Selected failed trade | None in Codex-scoped DB/log evidence | Insufficient evidence — unclassified |
| 137 | Selected failed trade | None in Codex-scoped DB/log evidence | Insufficient evidence — unclassified |

## Database evidence

**PROJECT EVIDENCE:** `agent-docs/runs/fts-WINDOW2-20261107-225520/db/CODEX/` contains no result artifacts. No Codex-scoped executed result is available for DBQ-FTS-001 through DBQ-FTS-005, schema reads, position rows, signals, wallet executions, accepted price events, exit audits, candles, or timing records.

The study charter establishes the frozen selection, but without the snapshot query result it cannot independently prove that each row satisfied the predicate at the historical snapshot instant.

### DB_REQUEST

QueryId: DBQ-CODEX-I8-001  
Purpose: Read the actual authoritative position schema, then retrieve only the six frozen rows. This establishes current durable state only and does not recreate the missed snapshot.  
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
  AND table_name = 'paper_position'
ORDER BY ordinal_position;

SELECT p.*
FROM crypto_ai.paper_position AS p
WHERE p.id IN (182, 172, 162, 158, 143, 137)
ORDER BY FIELD(p.id, 182, 172, 162, 158, 143, 137);
```

### END_DB_REQUEST

## Log evidence

**PROJECT EVIDENCE:** No trader-runtime logs were supplied in the permitted Codex evidence scope. The Codex stderr files contain client/cache and prior-analysis material, not application execution records.

Therefore, there is no evidence for:

- candle-close to signal latency;
- signal claim/processing/entry latency;
- accepted protection observation;
- stop/target/profit-lock threshold crossing;
- wallet execution success or failure;
- exception, contention, or retry affecting any frozen trade.

## Code evidence

**PROJECT EVIDENCE:** Present working-tree source shows the evidence that would be required, but does not prove historical behavior or the deployed version/configuration for these six trades.

- `PaperPosition` persists stop, realized P&L, entry/exit signal references, and open/close timestamps. [PaperPosition.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/domain/PaperPosition.java:33)
- Current BUY flow obtains a canonical price, revalidates, executes the wallet BUY, then creates the paper position. [PaperTradingService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/service/PaperTradingService.java:340)
- The current execution-price authority accepts an observation no older than 15 seconds. [ExecutionPriceAuthorityService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/execution/service/ExecutionPriceAuthorityService.java:25)
- Current mechanical protection makes stop loss absolute when an observed price is at or below the managed stop; wallet mechanical exit precedes the paper close. [LivePositionProtectionService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/position/service/LivePositionProtectionService.java:173)
- Current signal-route close persists `paper_position` before attempting the wallet exit and catches wallet exceptions; its P&L arithmetic is price difference × quantity plus prior partial-harvest P&L, not demonstrated fee-adjusted net P&L. [PaperTradingService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/service/PaperTradingService.java:487)
- Direct price-event evidence is initially `PENDING`; only durable `APPLIED`/`NON_CANONICAL` outcomes resolve that path. An `APPLIED` event proves callback completion, not necessarily a position exit. [MarketPriceEventService.java](/C:/workspace/crypto-ai-trader-mysql/src/main/java/com/crypto/market/service/MarketPriceEventService.java:50)

## Assessment

**AGENT INFERENCE:** A negative `realized_pnl` selection alone cannot distinguish a valid losing stop from an entry-quality issue, signal exit, delayed protection, execution mismatch, or accounting discrepancy. Candle OHLC must not substitute for accepted intra-candle price-event and wallet evidence.

**EXTERNAL RESEARCH:** None used for this final classification.

## Candidate next steps — no change authorized

1. Execute DBQ-CODEX-I8-001.
2. After schema confirmation, retrieve bounded entry/exit signals, wallet legs, price-event outcomes, and immutable exit audit records for these exact IDs.
3. Classify only after reconstructing each UTC timeline and its KSA conversion.

No code, configuration, database, git, deployment, or documentation changes were made.

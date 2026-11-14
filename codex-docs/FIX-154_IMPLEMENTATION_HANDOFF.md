# FIX-154 — Implementation Handoff for Code Review

Date: 2026-10-10  
Status: implemented in the local working tree; not deployed.

## Scope

FIX-154 changes only higher-timeframe BUY authority. It does not enable or
change FIX-153 reconciliation/runtime behavior.

The objective is to distinguish trusted, strong bearish 1h evidence from
unknown or weaker evidence without changing a stored historical signal.

## Implemented policy

1. The current 1m BUY still requires fresh 5m and 1h context.
2. A bearish 5m returns the existing `5M_BEARISH_VETO` first.
3. A bearish 1h with `data_quality_valid` `NULL` or `false` returns
   `1H_BEARISH_VETO`.
4. A trusted 1h `STRONG_SELL` with `confidence_score >= 70` returns
   `HTF_TRUSTED_BEARISH_VETO`.
5. A trusted 1h `SELL`, or `STRONG_SELL` with confidence below 70, is a
   SOFT conflict. It passes effective 1h `NEUTRAL` to profile logic and caps
   every allowed initial entry at 25%.
6. A non-bearish 1h with unknown quality keeps its raw decision; FIX-154 does
   not tighten bullish/non-bearish authority merely because historical quality
   is absent.
7. Existing progressive/add logic retains its raw 1h requirement. A SOFT
   conflict cannot authorize an add. Optional `SOFT_HTF_ADD_BLOCKED` evidence
   is observational only.

## Database changes

`V107__fix_154_trusted_higher_timeframe_buy.sql` adds:

- `trade_signal.data_quality_valid BOOLEAN NULL`.
- Replay parity columns on `trade_signal_test`:
  `btc_context_candle_close_time`, `btc_context_age_seconds`,
  `btc_context_freshness_threshold_seconds`, `signal_mode`, and
  `data_quality_valid`.
- Append-only `buy_htf_assessment_audit`, including effective/raw decisions,
  candle-close-based age, quality evidence, the hard-confirmation marker,
  cap, legacy veto and final result.

Historical `data_quality_valid` stays `NULL`; no value is backfilled.

## Included production code

| File | Change |
|---|---|
| `domain/TradeSignal.java` | Maps nullable `data_quality_valid`. |
| `service/AnalysisService.java` | Persists `MarketContextSnapshot.dataValid()` at signal generation. |
| `service/HigherTimeframeBuyAssessmentService.java` | Pure FIX-154 decision matrix; returns detached effective decisions and never mutates entities. |
| `service/TradeExecutionValidationService.java` | Applies assessment to direct BUY and contextual/accumulated authority; enforces SOFT cap; records non-blocking audit evidence. |
| `service/OpportunityConsolidationService.java` | Applies the same matrix and cap to consolidation. |
| `execution/service/ExecutionIntelligenceService.java` | Keeps existing progressive raw-1h guard; invokes optional audit-only SOFT evidence after its rejection. Uses `SETUP_WAKEUP` audit path for the 5m wake-up caller. |
| `service/BuyHtfAssessmentAuditStore.java` | Snapshots and asynchronously persists best-effort Production audit rows. Replay writes no Production audit row. |
| `config/Fix154AuditAsyncConfig.java` | Dedicated one-thread, bounded executor; audit saturation drops evidence rather than borrowing a trading connection. |
| `service/Fix154Metrics.java` | Non-gating counters for untrusted 1h blocks, audit failures, SOFT caps and SOFT add blocks. |
| `service/Fix154DiagnosticsController.java` | Read-only `GET /api/fix154/health` metrics endpoint. |
| `service/Fix154RuntimeSchemaChecks.java` | Checks V107-required production and Replay schema before runtime admission. |

## Replay behavior

The regular reflection-based Replay writer persists every JPA `TradeSignal`
column. V107 therefore updates `trade_signal_test` alongside production.
FIX-154 audit rows are deliberately skipped while `ExecutionReplayScope` is
active because replay-generated entities do not have Production IDs and parity
is recorded by Replay’s own evidence tables.

## Tests included

- `HigherTimeframeBuyAssessmentServiceTest`
  - hard, soft, untrusted, 5m-veto precedence, 69/70 boundary, BNB #150a and
    #150b policy cases, and unknown non-bearish authority.
- `TradeExecutionValidationServiceTest`
  - direct/context SOFT caps, conservative behavior, neutral-5m exception is
    not unlocked, audit failure isolation, Production/Replay parity, and
    future-context exclusion.
- `OpportunityConsolidationServiceTest`
  - SOFT cap through consolidation.
- `BuyHtfAssessmentAuditStoreTest`
  - Replay does not write a Production audit row.
- `Fix154MigrationContractTest`
  - V107 contains production, Replay and audit schema contracts.

## Verification performed

Executed successfully with Java 21:

```text
mvn -q -Dtest=HigherTimeframeBuyAssessmentServiceTest,
TradeExecutionValidationServiceTest,
OpportunityConsolidationServiceTest,
BuyHtfAssessmentAuditStoreTest,
Fix154MigrationContractTest test
```

## Reviewer focus / remaining external proof

All FIX-154 implementation code described above is present in this local
workspace. The remaining release proof is execution, not an identified code
gap:

1. Run Flyway V107 against a dedicated disposable MySQL schema and verify
   `trade_signal`, `trade_signal_test`, and `buy_htf_assessment_audit`.
2. Run the full application/replay regression suite against that schema before
   Production deployment.
3. Confirm `flyway_schema_history` on the target database has not already
   applied V107; once applied, any correction must use a new forward migration.

No production database, service, or deployment configuration was changed by
this implementation work.

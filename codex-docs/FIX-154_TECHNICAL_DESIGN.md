# FIX-154 — Trusted higher-timeframe BUY validation

Status: implementation design approved. FIX-154 is scheduled before the
remaining FIX-153 runtime release work.

## Purpose and boundary

FIX-154 replaces the current binary fresh-1h bearish veto with a trusted
higher-timeframe assessment. It does not relax the existing 5m confirmation
requirement, risk/reward result, current-1m BTC protection, or profile rules
except where a raw 1h decision would otherwise act as an untrusted veto.

The legacy `src/main/resources/md/FIX-146.md` is an unrelated shared-market
readiness design and is not an implementation source for this fix.

## Current integration points to replace

The 1h/5m blocks in all three paths below must be replaced as a whole by one
shared assessment call. Do not add this decision to `FinalDecisionService`.

1. `TradeExecutionValidationService.validateBuy()` — direct 1m BUYs.
2. `TradeExecutionValidationService.validateBuyContext()` — accumulated/setup
   authority, whose reference may be a 5m signal.
3. `OpportunityConsolidationService.evaluate()` — consolidated BUYs.

Each replacement owns the current lookup, freshness, 5m veto, and 1h veto
block in that method. The remaining profile/consolidation logic consumes the
assessment's effective context; it must not reread the raw 1h decision as an
independent veto.

## Immutable signal evidence

Migration `V107` adds:

```sql
ALTER TABLE trade_signal ADD COLUMN data_quality_valid BOOLEAN NULL;
```

`AnalysisService` writes `MarketContextSnapshot.dataValid()` through the
existing `TradeSignal.builder()` call for every newly generated signal on every
interval. Historic rows are never backfilled: `NULL` means unknown/invalid.

FIX-154 does not depend on `signal_mode` or any other FIX-153 runtime/schema
feature. `data_quality_valid = true` is sufficient evidence eligibility for
this release; false or `NULL` is unusable. Historical-only exclusion is added
later with FIX-153, after its own migration/runtime release is approved.

Historical `btcContextEntryAllowed=false` is audit evidence only. It is never a
FIX-154 usability or hard-veto predicate. The current 1m BTC guard is unchanged.

## Time-bounded selection and fixed freshness

For a request with reference signal `R`, use `R.generatedAt` as `evaluatedAt`.
Select the latest same-symbol 5m and 1h signal such that both its generated
time and its candle close are at or before `evaluatedAt`; no wall-clock lookup
is permitted. Replay supplies the same historical reference time.

Existing fixed freshness limits remain authoritative:

| Interval | Maximum age |
|---|---:|
| 5m | 20 minutes |
| 1h | 3 hours |

`MISSING_5M_CONFIRMATION` and `MISSING_1H_CONTEXT` retain their existing
meaning and priority when their respective interval is missing or stale. A
stale signal is not quietly converted into an allowed BUY just because it is
`NOT_TRUSTED` for FIX-154.

## Usability and 1h strength

A fresh row is usable for FIX-154 only if `data_quality_valid = true`.

FIX-154 v1 does not invent ADX/DMI/VWAP/CMF evidence, because those indicators
are not currently persisted. Its fixed strength definition is:

```text
HARD_BEARISH_CANDIDATE = 1h decision == STRONG_SELL
                         AND confidence_score >= 70
SOFT_CONFLICT          = fresh, quality-valid 1h decision == SELL
                         OR STRONG_SELL with confidence_score below 70
NOT_TRUSTED            = fresh but quality-invalid/unknown/historical-only 1h
                         OR fresh non-bearish 1h
```

`confidence_score` is the persisted effective confidence. `raw_confidence_score`
is explicitly not used. The constant `70` is a named FIX-154 configuration
default, persisted in the audit row, and may change only through a reviewed
strategy change with Replay regression.

## Exact 1h × 5m decision matrix

First apply existing missing/stale checks. Only then apply this table.

| 1h state | 5m state | Final HTF result before profile | Profile inputs |
|---|---|---|---|
| missing/stale | any | existing `MISSING_1H_CONTEXT` rejection | none |
| any | missing/stale | existing `MISSING_5M_CONFIRMATION` rejection | none |
| NOT_TRUSTED bearish from false/NULL quality | bearish | legacy `5M_BEARISH_VETO` | none |
| NOT_TRUSTED bearish from false/NULL quality | bullish/neutral/watch | `1H_BEARISH_VETO` | none |
| NOT_TRUSTED non-bearish from false/NULL quality | bearish | legacy `5M_BEARISH_VETO` | none |
| NOT_TRUSTED non-bearish from false/NULL quality | bullish/neutral/watch | continue to profile | raw 1h, raw 5m |
| SOFT_CONFLICT | bearish | legacy `5M_BEARISH_VETO` | none |
| SOFT_CONFLICT | bullish/neutral/watch | continue to profile, cap resulting first entry at 25% | effective 1h=`NEUTRAL`, raw 5m |
| HARD_BEARISH_CANDIDATE | bearish, quality-valid | legacy `5M_BEARISH_VETO`; audit `HARD_CONFIRMATION_PRESENT` | none |
| HARD_BEARISH_CANDIDATE | bullish/neutral/watch, quality-valid | `HTF_TRUSTED_BEARISH_VETO` | none |
| fresh, quality-valid non-bearish 1h | non-bearish | continue to profile | raw 1h, raw 5m |

The profile call produces the final decision for every `continue to profile`
row. It may still return its existing insufficient-confirmation code. It must
not consult the raw 1h row after this mapping.

`5m quality=false/NULL` does not alter the legacy 5m fallback. A fresh raw
bearish 5m still returns `5M_BEARISH_VETO`, exactly as today. It merely cannot
act as new FIX-154 hard-veto confirmation or a soft-conflict release.

The veto order is fixed: a bearish 5m returns `5M_BEARISH_VETO` before any
trusted-1h result. If the 1h is hard, audit records `HARD_CONFIRMATION_PRESENT`
on that legacy outcome. When 5m is not bearish, a hard trusted 1h returns
`HTF_TRUSTED_BEARISH_VETO`. The only deliberate lasting relaxation is a trusted
SOFT bearish 1h: an otherwise allowed initial entry is capped at 25%.

## Profile contract

`conservativeBuy`, `balancedBuy`, `balancedBuyWithNeutralFiveException`, and
`aggressiveBuy` receive the assessment's effective 1h/5m context—not raw
selected 1h evidence. A SOFT conflict maps to `NEUTRAL`, never `WATCH`, then
the validation result is capped with `min(profilePositionPercent, 25)`.
This means the neutral-5m/direct-BUY exception still requires a genuinely
bullish 1h and cannot be unlocked by SOFT evidence. An untrusted non-bearish
1h retains its raw decision so deployment does not accidentally tighten current
bullish authority.

The assessment result carries `maxInitialPercent`: `25` for `SOFT_CONFLICT`,
otherwise no cap. Every caller that selects a size must use
`min(itsCalculatedPercent, maxInitialPercent)` when the cap is present. This
includes direct validation, `validateBuyContext()` callers (the 5m-trigger
path and accumulated evidence), and
`OpportunityConsolidationService.consolidatedPositionPercent()`.

The raw selected signals remain audit evidence. Existing 5m requirements inside
profiles remain unchanged. HARD and untrusted-bearish outcomes never call a
profile method.

### Add-on contract

`ExecutionIntelligenceService.progressivePositionDecision()` already reads raw
1h evidence and requires `WATCH` or bullish support for `SCOUT_ENTRY`,
`CONFIRMATION_ADD`, and `PROGRESSIVE_TREND_ADD`. Keep that check unchanged and
outside the three replacement paths; it already blocks HARD, SOFT, and
untrusted-bearish 1h states. Do not route it through effective FIX-154 context,
which could widen behavior. When that existing check rejects while the assessed
band is SOFT, record `SOFT_HTF_ADD_BLOCKED` as audit/metric evidence only.

## Append-only execution audit

FIX-154 is evaluated during execution/consolidation, not while a signal is
saved. Migration `V107` creates append-only `buy_htf_assessment_audit`:

- primary key ID;
- reference signal ID, evaluation path (`DIRECT_BUY`, `BUY_CONTEXT`,
  `CONSOLIDATION`, `PROGRESSIVE_TREND_ADD`, `CONFIRMATION_ADD`,
  `ACCUMULATED_EVIDENCE_ADD`) and `evaluated_at`; no unique constraint;
- raw/effective 1h and 5m signal IDs, decisions, signal modes, candle closes,
  ages, data-quality values, and usability reasons;
- configured confidence threshold, band, legacy-veto result, profile context,
  final result/reason, Production/Replay mode, and creation timestamp.

The audit is written best-effort after the relevant execution/consolidation
decision is known. A duplicate or persistence failure is logged and increments
`fix154.audit_write_failure_count`; it must never roll back, reject, or alter a
trading decision. It is evidence only—not a fence or an input to later trading.

## Deployment behavior

Immediately after deployment all pre-V107 1h/5m rows have
`data_quality_valid=NULL`. They are treated as `NOT_TRUSTED`; they cannot make a
new permissive decision: a bearish unknown 1h remains blocked, while a
non-bearish unknown 1h retains its existing profile authority. The health view
must expose `fix154.untrusted_bearish_1h_block_count` and
`fix154.audit_write_failure_count`, `fix154.soft_capped_entry_count`, and
`fix154.soft_add_blocked_count`, grouped by execution path.

## Migration ordering and FIX-153 independence

The configured local database has V106 as its latest applied Flyway migration.
Therefore FIX-154 reserves `V107__fix_154_trusted_higher_timeframe_buy.sql`.
Before this migration is applied anywhere, rename the currently unreleased
`V107__fix_153_local_candle_reconciliation.sql` to V108. Never rename or edit
an applied migration. Do not enable Flyway `outOfOrder`; deployment order is
made explicit instead.

## Required regression suite

- All three integration paths use the shared service and no longer contain raw
  `1H_BEARISH_VETO` branches.
- `STRONG_SELL` + `confidence_score >= 70` and bearish fresh valid 5m produces
  legacy `5M_BEARISH_VETO` with `HARD_CONFIRMATION_PRESENT` audit; the same 1h
  with non-bearish 5m produces `HTF_TRUSTED_BEARISH_VETO`.
- Strong/soft/untrusted 1h plus bullish, neutral, watch, bearish, missing,
  stale, false-quality, and NULL-quality 5m covers every matrix row and its
  final profile result.
- `MISSING_1H_CONTEXT` (3h) and `MISSING_5M_CONFIRMATION` (20m) retain their
  priority and codes.
- BNB #150a: persisted historical 1h bearish row with `NULL` quality =>
  `NOT_TRUSTED` and `1H_BEARISH_VETO`.
- BNB #150b: Replay regenerates that same 1h with quality evidence and it is
  SOFT; no trusted hard veto is emitted and the final expected result is
  `REJECTED_RISK_REWARD` when the risk/reward guard fails.
- Historical `btcContextEntryAllowed=false` does not change usability; current
  1m BTC behavior is unchanged.
- FIX-154 migration succeeds with no `signal_mode` column and its assessment
  has no FIX-153 schema/runtime dependency.
- No future signal/candle is selected in Production or Replay; both produce
  equivalent audit evidence for the same historical inputs.
- Pre-migration `NULL` quality is never treated as valid.
- Repeated execution/consolidation evaluations append audit rows and audit
  failure leaves the business decision intact.
- A SOFT initial entry never exceeds 25% through progressive, confirmation, or
  accumulated-evidence or consolidation sizing; existing progressive raw-1h
  support rules block add stages while the 1h remains bearish.

## Release conditions

Run Java 21 unit/integration tests, migration tests, Production/Replay parity,
and a bounded no-look-ahead historical replay before enabling FIX-154.
FIX-153 remains independent and retains its own release gates.

## Revision addendum — Round 5 approval conditions

This addendum governs where it differs from earlier wording.

### Release build decision

The FIX-154 release build includes `V108__fix_153_local_candle_reconciliation.sql`
after the current unreleased FIX-153 V107 migration is renumbered to V108.
This is required even with reconciliation disabled: `candle_analysis_outcome`
is used by ordinary live signal processing, terminal worker outcomes, and the
closed-candle detector. Omitting V108 would cause missing-table SQL failures;
in particular, a normal signal-processing completion would roll back because
its outcome write is inside the processing transaction.

Including V108 ships FIX-153 reconciliation *schema only*. The reconciliation
runtime remains disabled and retains all of its separate release gates.

### No FIX-153 provenance dependency

Ignore earlier references to historical-only status or signal modes in the
FIX-154 band definition and audit payload. FIX-154 v1 eligibility depends only
on time-bounded selection and `data_quality_valid = true`; `signal_mode` is not
read, required, or stored by the FIX-154 audit.

### Effective context is transient

The assessment returns immutable effective decision values separately from the
loaded `TradeSignal` entities. No code may call `setDecision`, mutate, merge,
or save a selected 1h/5m signal to obtain effective `NEUTRAL` context. Profile
methods receive explicit effective decisions (or an immutable assessment
context), while the raw entity remains audit evidence only. A regression test
must load a SOFT 1h signal, execute the assessment/profile path, flush/clear,
and prove its stored decision is unchanged.

### Progressive-path evidence is observational

`progressivePositionDecision()` retains its existing raw-1h support check as
the only decision authority for scout/add paths. If that existing check passes,
FIX-154 performs no assessment lookup and writes no progressive audit row. If
it rejects because raw 1h is bearish, a best-effort audit-only assessment may
record `SOFT_HTF_ADD_BLOCKED` when the band is SOFT. That observation must not
alter the rejection, allow an add, cause a database lookup on passing paths, or
propagate an audit failure into the trading decision.

V108 ships with FIX-154 because the current runtime writes
`candle_analysis_outcome`. Reconciliation remains disabled.

### Round 5 text clarification

This addendum removes any earlier contrary implication: FIX-154 v1 has no
`signal_mode` or historical-only predicate or audit field. Effective 1h/5m
decisions are separate immutable values and never mutate a loaded signal.
Progressive-path assessment is audit-only after its existing raw-1h check has
already rejected; it is skipped entirely when that check passes and never feeds
the scout/add decision.

## Implementation completion record — 2026-10-10

The approved FIX-154 design is now implemented in the working tree.

- `V107__fix_154_trusted_higher_timeframe_buy.sql` creates the nullable signal
  quality evidence and append-only assessment audit table. No historical value
  is fabricated or backfilled.
- `AnalysisService` persists `MarketContextSnapshot.dataValid()` for every new
  signal.
- `HigherTimeframeBuyAssessmentService` owns the hard/soft/untrusted decision
  matrix. It returns immutable effective decisions and does not mutate a JPA
  signal.
- Direct BUY validation, accumulated-evidence authority, and opportunity
  consolidation invoke that one service. A SOFT result uses effective 1h
  `NEUTRAL` and applies the 25% initial-position cap. The legacy bearish-5m
  veto retains precedence. Progressive adds retain their existing raw 1h
  requirement and can only create best-effort audit evidence after rejection.
- `BuyHtfAssessmentAuditStore` is append-only and runs in an independent
  transaction. It catches insert failures so audit availability cannot alter a
  business decision.

Focused Java 21 verification passed:
`HigherTimeframeBuyAssessmentServiceTest`,
`TradeExecutionValidationServiceTest`, and
`OpportunityConsolidationServiceTest`.

This is implementation completion, not production deployment approval. Apply
V107/V108 only through the normal clean Java 21 release pipeline and complete
the required migration, Replay-parity, and broader regression validation before
enabling it in Production.

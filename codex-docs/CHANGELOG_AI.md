# 2026-10-08 — FIX-153 local-candle hardening status

# 2026-11-15 — Jenkins Mockito-stub repair (IMPLEMENTED UNDER JEAN OVERRIDE)

- **Problem:** Jenkins rejected the build before deployment because
  `ExecutionIntelligenceServiceTest.freshFiveMinuteBuyCanWakeSupportiveOneMinuteAfterLiquidityConfirmationClears`
  used an obsolete one-argument Mockito stub.
- **Root cause:** the production setup-wakeup path calls
  `validateBuyContext(signal, "SETUP_WAKEUP")`; the targeted test still stubbed
  `validateBuyContext(signal)`.
- **Solution:** aligned the targeted test stub with the existing two-argument
  production contract.
- **Files/classes changed:** `ExecutionIntelligenceServiceTest`.
- **Database impact:** NONE.
- **Production impact:** NONE; this is a test-only correction and does not
  deploy or change runtime behavior.
- **Replay impact:** NONE.
- **Validation:** Java 21 targeted `ExecutionIntelligenceServiceTest` passed:
  59 tests, 0 failures, 0 errors.

# 2026-10-16 — FIX-156 Replay boundary foundation correction (in progress)

- **Problem:** the active baseline Replay run still consulted the shared
  `fix122_replay_revision` experiment switch, and the Replay chart queried
  Production `wallet_trade` rows as an overlay.
- **Root cause:** legacy FIX-122 comparison/UI behaviour remained reachable
  from the manual Replay path after the FIX-156 no-wallet decision.
- **Solution:** baseline Replay now fixes its parity policy locally instead of
  reading the shared experiment switch; its chart returns Replay executions
  and market candles only. Production comparisons belong in immutable fixtures
  and the future parity report.
- **Files/classes:** `ShadowProductionReplayService`, `RegressionTestService`.
- **Database impact:** none; no migration or data change was run.
- **Production impact:** none.
- **Replay impact:** removes two prohibited shared-data reads. Full Replay
  parity implementation and its formal validation remain incomplete.

# 2026-10-17 â€” FIX-156 Replay Spring wiring repair

- **Problem:** application startup failed because `ShadowProductionReplayService`
  requires `ReplaySizingPolicy`, but that class was not registered as a Spring bean.
- **Root cause:** the newly introduced Replay-only policy primitives were plain
  Java classes despite constructor injection into a Spring-managed service.
- **Solution:** registered `ReplaySizingPolicy` and
  `ReplayMinimumExecutionPolicy` as Replay-only Spring components.
- **Files/classes:** `ReplaySizingPolicy`, `ReplayMinimumExecutionPolicy`.
- **Database impact:** the user-started application applied already-present
  additive V111 successfully; no data repair or Production-table change occurred.
- **Production impact:** none.
- **Replay impact:** Replay service dependencies can now be resolved during
  ApplicationContext construction; full parity remains incomplete.

# 2026-10-16 â€” FIX-156 Replay boundary foundation correction (in progress)

- **Problem:** the initial Replay guard was table-name-only, the frozen profile
  lacked required BUY-mirror evidence, and an attempted V110 extension would
  have edited an already-applied Flyway migration.
- **Root cause:** the existing Replay design has shared wallet/Binance/service
  dependencies and V110 was treated as pending before a read-only Flyway check.
- **Solution:** added operation categories to `ReplayAccessPolicy`, a dedicated
  Replay O-8 `fix122_evaluation` gateway, frozen Replay sizing/minimum-order/
  SELL-policy primitives, and profile fields for execution/BUY settings.
  Restored applied V110 unchanged and added forward-only V111 for Replay-only
  profile detail, certificate, and price coverage records.
- **Files/classes:** `com.crypto.regression.replay.*`, V111, the Replay
  foundation tests, `ShadowProductionReplayService` SELL-policy dependency,
  and `fix-registry.js`.
- **Database impact:** V111 is unapplied and additive. No database data or
  Production table was changed.
- **Production impact:** none. Replay wiring and all parity claims remain
  incomplete and blocked by active Jean findings.

- Implemented: OFF-mode trader-owned candle ingestion, monotonic close tokens,
  bounded detector/lanes, canonical price evidence, direct-attempt freshness,
  REST exchange-time close validation, and controlled ADMIN drain behavior.
- Operational impact: a successful drain exits the JVM; a timeout resumes
  admission. Price protection pauses during a drain window.
- Database impact: existing FIX-153 V106/B105 schema remains required; no
  production migration or data change was run by Codex.
- Release status: NOT APPROVED FOR PRODUCTION. Java 21, disposable-MySQL,
  crash/race, Windows lock, soak and Replay-parity validation remain open.

# 2026-10-08 — FIX-153 exchange/database clock correction

- Problem: JVM/database clock skew could make a Binance-derived candle appear
  too early or too old during freshness checks.
- Solution: `ExchangeClockSynchronizer` measures Binance server time against
  database UTC and JVM time at startup and periodically; database and JVM
  callers apply their own bounded offset without HTTP inside locks.
- The clock-skew warning uses the larger absolute database or JVM offset; the
  dedicated Binance time client has a five-second connect/read bound.
- General Binance REST requests now use the configured request timeout, and
  scheduled clock sampling cannot monopolise the four-thread scheduler pool.
- Bootstrap REST reads occur before its local candle-write transaction, so
  the bounded Binance timeout does not hold a database transaction open.

# 2026-10-09 — FIX-153 audit: analysis outcomes and protection clock

- Audit result: normal live and historical analysis persist a `TradeSignal`,
  including neutral/no-entry decisions; there is no normal analysed-without-
  durable-outcome path to reconcile.
- Corrected the post-lock direct-protection timestamp validation to use the
  JVM-to-exchange clock correction, matching initial receipt validation.
- A universal shutdown barrier remains a separate, unverified design task:
  it must cover financial mutations without treating unrelated administration
  or configuration writes as trading authority.
- Database impact: none. Production and Replay impact: execution freshness now
  follows an automatically measured exchange-aligned reference clock.
- Validation still required: unavailable-time-source, stale observation,
  positive/negative skew, and first-attempt freshness regression tests.

# 2026-10-09 — FIX-154 identifier alignment

- The pending higher-timeframe BUY-validation design was renumbered from
  `FIX-146` to `FIX-154` in the project guidance and architecture record.
- It remains separate from FIX-153, unimplemented, and not approved for
  deployment. There is no database, Production, or Replay behavior change.

# 2026-10-09 — Automated local-candle reconciliation direction

- Approved direction: the pending local-candle reconciler must automatically
  repair authoritative interrupted-open candles and exact absent identities in
  the active quality window. Inserted identities use `BACKFILL` provenance and
  never receive a close token or dispatch live analysis.
- No manual approval, database repair, or persisted review fence may be needed
  for pending candle/analysis reconciliation. Unverified data remains explicit
  non-executable evidence; this direction does not override legitimate
  strategy or risk-entry vetoes.
- Status: design only; no database migration, code, Production, or Replay
  behavior has changed.

# 2026-10-09 — Appendix K reconciliation contract alignment

- Project guidance now records the approved pending design constraints: pure
  insert-only authoritative gap backfill; quality-window-bounded candidates;
  durable terminal outcomes; structurally isolated historical processing; a
  shared Binance REST rate-limit gate; and durable financial-effect
  reconciliation only after atomicity is proven.
- The planned universal mutation barrier is explicitly in-memory and active
  only during drain; it must not queue, delay, or persistently fence fresh
  trading outside drain. Protection for open positions during replacement is
  still an unresolved release gate.
- Status: documentation/design alignment only. No runtime behavior, schema,
  Production data, or Replay behavior was changed.

# 2026-10-09 — FIX-153 automatic reconciliation implementation (initial slice)

- Problem: reconnect-boundary or startup candle gaps could leave local history
  invalid until a person performed a database repair.
- Solution: added V107 durable reconciliation/audit and terminal-outcome
  tables; guarded update-only authoritative recovery; pure insert-only gap
  backfill; a dedicated recovery executor; reconnect/scheduled requests;
  token-free recovery outcomes; post-ingestion late-live-close observation;
  and reconciliation participation in local drain.
- Files/classes changed: `CandleRepository`, `BinanceMarketDataClient`,
  `BinanceKlineService`, `BinanceWebSocketManager`, `LocalDrainService`,
  `LocalCandleHealth`, `SignalProcessingStore`, plus new local reconciliation,
  outcome, audit, executor-config, and V107 migration files.
- Database impact: forward migration V107 is required. Production and Replay
  impact: feature is disabled by default and has not been deployed. Universal
  mutation-barrier coverage, shared REST admission/rate-limit control,
  quarantined-effect reconciliation, MySQL crash/race tests, and Replay parity
  remain open release work.

# 2026-10-09 — FIX-153 reconciliation outcome correction

- Corrected recovery semantics so `SKIPPED_NOT_LIVE` is recorded only when a
  recovery/backfill actually changes candle evidence; a competing live or
  existing row is not relabelled.
- `REST_NOT_FINAL` is now retryable rather than a terminal mismatch. Audit keys
  use fixed epoch-millisecond identity precision, and reconciliation is safely
  disabled by default even when configuration is absent.
- Added terminal outcomes for worker data-unavailable, historical-only,
  already-analysed, stale work, and detector expiry paths. Transient failures
  remain without a terminal outcome so retry remains possible.
- Added `LocalReconciliationAuditRepositoryTest`; targeted Java 21 tests and
  compile passed. MySQL, race/crash, rate-limit, barrier, effect-reconciler,
  and Replay validation remain open.

# 2026-10-09 — FIX-153 reconciliation safety correction

- Recovery now requires positive loss evidence before it may finalise an open
  row: either the successor candle is already present or a reconnect was
  admitted after the candidate close. This prevents a short healthy-stream
  delay from being converted into a token-free, unanalysed close.
- REST finality/identity disagreement is retryable while the local row remains
  open. The authoritative Binance close timestamp, not the local expected
  value, is persisted after a successful exact match. A bounded first-candle
  probe identifies pre-listing identities as explicit unavailable outcomes.
- Recovery uses the short timeout client and checks drain between requests;
  late-live-close evidence now preserves its first timestamp.
- Status: Java 21 compilation passed. Dedicated reconciliation race, drain,
  MySQL, and Replay tests remain required before enabling the feature.

# 2026-10-09 — FIX-153 deterministic reconciliation test coverage

- Added deterministic Java 21 tests for the reconciliation contract: a healthy
  late live close without successor/reconnect evidence is left untouched;
  successor evidence permits update-only repair and records a non-live outcome;
  a gap-insert race ends as `ALREADY_PRESENT` without inventing an outcome;
  a confirmed exchange gap ends as `SKIPPED_DATA_UNAVAILABLE`; and active drain
  prevents REST admission.
- Corrected the audit test to use a true terminal state (`EXPIRED`) rather than
  `REST_MISMATCH`, which is deliberately retryable.
- Extended the disposable MySQL integration test with actual affected-row
  checks for update-only authoritative repair and insert-only gap backfill.
- Files/classes changed: `LocalCandleReconciliationServiceTest`,
  `LocalReconciliationAuditRepositoryTest`, and `LocalCandleMySqlIT`.
  Database schema and runtime trading behavior are unchanged.
- Verification: the focused deterministic tests passed under Java 21. The
  MySQL integration test is compiled but cannot run until a disposable
  loopback MySQL service is supplied through the required `FIX153_TEST_MYSQL_*`
  environment variables. Reconciliation remains disabled by default.

# 2026-10-09 — FIX-153 configuration validation correction

- Removed the duplicate top-level `local-candle` mapping from
  `application.yml` by merging the reconciliation and local-runtime settings
  into one mapping. Spring/YAML rejects duplicate keys, so this was preventing
  isolation-context startup.
- A full Java 21 `mvn test` now parses configuration correctly but is not yet
  green: two pre-existing shared-market tests fail in `Fix142DeliveryTest`
  (`fix150LegacyFlatRecoveryPreservesUncertaintyAndAllowsNextProtection` and
  `fix152RejectedMarkerContinuesWithoutProtectionReplayThenProcessesNewTick`).
  These failures are outside the reconciliation test slice and must be resolved
  or baselined before release certification.

# 2026-10-09 — FIX-153 shared Binance REST admission gate

- Added `BinanceRestAdmissionGate`, a process-wide cooldown for Binance HTTP
  429 and 418 replies. All `BinanceMarketDataClient` REST paths now pass through
  it, including server-time, bootstrap klines, reconciliation klines, depth,
  and exchange-info reads.
- The gate rejects a new REST request during the exchange cooldown; it does not
  queue work, take database locks, disable WebSocket ingestion, or pause price
  protection. Individual callers retain their own retry behavior.
- Added deterministic tests for cooldown expiry and for preserving the longer
  of overlapping cooldowns. Focused Java 21 tests pass.
- Files changed: `BinanceMarketDataClient`, new `BinanceRestAdmissionGate` and
  `BinanceRestAdmissionGateTest`. No database, Production, or Replay state is
  changed. Reconciliation remains disabled pending the remaining release gates.

# 2026-10-09 — FIX-154 higher-timeframe BUY validation design

- Added the implementation-ready design record for the pending higher-timeframe
  BUY validation change. It specifies data-quality persistence, time-bounded
  evidence selection, trust bands, hard-veto/recovery conditions, immutable
  audit evidence, preservation of legacy `5M_BEARISH_VETO`, and Replay parity.
- Clarified that the legacy `src/main/resources/md/FIX-146.md` readiness
  document is not the FIX-154 implementation source.
- Design only: no Java, migration, database, Production, or Replay behavior
  changed.

# 2026-10-09 — FIX-154 trusted-bearish policy decision

- Recorded the approved policy: HARD trusted bearish 1h vetoes; unknown bearish
  1h remains blocked; only a trusted SOFT bearish 1h may permit an otherwise
  valid first entry, capped at 25%, with all add paths blocked while SOFT.
- Defined legacy 5m veto precedence, append-only/best-effort audit behavior,
  post-deployment unknown-quality behavior, health counters, and distinct BNB
  #150 historical/replay regression cases.
- Read-only 30-day local evidence supports the fixed confidence split:
  `STRONG_SELL` has 203 rows at >=70 and 192 below 70.
- Design only: no Java, migration, or runtime behavior changed.

# 2026-10-09 — FIX-154 Round 5 release-build decision

- The FIX-154 build must include the renumbered FIX-153 V108 migration because
  ordinary signal processing depends on `candle_analysis_outcome`; reconciliation
  runtime remains disabled.
- Appended safeguards: FIX-154 does not use `signal_mode`; effective context is
  immutable/transient and cannot mutate stored signals; progressive Soft audit
  evidence is best-effort and cannot influence an add decision.
- Design only: no Java, migration, or runtime behavior changed.

# 2026-10-09 — V108 outcome-table startup guard

- Renumbered the unreleased FIX-153 reconciliation migration from V107 to V108
  to reserve V107 for the pending FIX-154 migration. No applied migration was
  edited.
- Added `CandleAnalysisOutcomeSchemaVerifier`: startup now fails clearly if
  `candle_analysis_outcome` is absent even though outcome-writing runtime code
  is deployed. Added focused H2 coverage for the missing-table and present-table
  paths; Java 21 focused tests pass.
- V108 ships with FIX-154 because current live signal processing writes the
  outcome table. Reconciliation remains disabled.

# 2026-10-09 — FIX-154 implementation start: V107

- Confirmed read-only Flyway history: neither V107 nor V108 is applied to the
  configured local trader database.
- Added V107 as the first FIX-154 implementation artifact. It adds nullable
  `trade_signal.data_quality_valid` and the append-only execution assessment
  audit table. It deliberately has no `signal_mode` dependency or unique
  execution-audit key.
- No execution behavior has changed yet; BUY assessment/service integration is
  pending implementation and review.

# 2026-10-09 — FIX-154 deployment-order and cap correction

- FIX-154 is now scheduled before remaining FIX-153 runtime work and no longer
  depends on `signal_mode`. Its V107 migration is reserved from the actual
  local Flyway history; the unreleased FIX-153 V107 must become V108 before
  implementation.
- Defined assessment-level `maxInitialPercent=25` for SOFT outcomes and required
  every self-sizing caller to apply it. Kept progressive raw-1h support checks
  unchanged; they already block add stages for bearish 1h evidence.
- Corrected unknown-bearish/5m-veto priority and added SOFT counters.
- Design only: no Java, migration, or runtime behavior changed.
# 2026-10-10 — FIX-154 trusted higher-timeframe BUY assessment (implementation)

- **Problem:** every bearish 1h row had equal veto authority, including
  historical rows with unknown data-quality provenance; there was no durable
  explanation of the effective 5m/1h BUY context.
- **Root cause:** BUY validation and consolidation read raw `TradeSignal`
  decisions directly, with no assessment of quality or bearish strength.
- **Solution:** added `data_quality_valid` persistence to `TradeSignal` and
  `AnalysisService`; introduced `HigherTimeframeBuyAssessmentService`; wired it
  into direct BUY, accumulated-evidence authority, consolidation and
  observational progressive-add evidence. The SOFT 1h result is capped at 25%
  and uses immutable effective decisions; HARD and unknown bearish evidence
  vetoes. `V107` creates `data_quality_valid` and append-only
  `buy_htf_assessment_audit`.
- **Files/classes:** `TradeSignal`, `AnalysisService`,
  `HigherTimeframeBuyAssessmentService`, `TradeExecutionValidationService`,
  `OpportunityConsolidationService`, `ExecutionIntelligenceService`,
  `BuyHtfAssessmentAuditStore`, `V107__fix_154_trusted_higher_timeframe_buy.sql`,
  and focused tests.
- **Database impact:** new nullable `trade_signal.data_quality_valid`; no
  backfill. Historical `NULL` stays unknown. New append-only audit table.
- **Production / Replay impact:** the same shared validation service is used
  for both. Audit failure is explicitly non-blocking. V108 ships because live
  runtime outcome writers require its schema; reconciliation remains disabled.
- **Validation status:** focused Maven tests could not run in this shell because
  the active JDK reports `release version 21 not supported`; execute with the
  project Java 21 toolchain before release.

## Validation correction — 2026-10-10

The shell was switched to the installed Java 21 toolchain
(`C:\Program Files\Java\jdk-21`). Focused Maven verification then passed:
`HigherTimeframeBuyAssessmentServiceTest`,
`TradeExecutionValidationServiceTest`, and
`OpportunityConsolidationServiceTest`.

## FIX-154 review-round correction — 2026-10-10

- **Replay parity:** V107 now brings `trade_signal_test` forward with the
  V97 BTC freshness fields, V106 `signal_mode`, and FIX-154
  `data_quality_valid`, matching the reflection-based Replay writer.
- **Audit isolation:** audit records are snapshot and submitted to a bounded,
  one-thread executor. Replay never writes a Production audit row, and audit
  saturation/failure only increments an in-memory metric.
- **Evidence accuracy:** audit candle close times are derived from candle open
  time plus the interval, hard-confirmation is persisted, and both legacy veto
  codes are retained.
- **Startup safety:** outcome-schema verification now occurs at bean creation;
  FIX-154 verifies its required production and replay columns before runtime
  admission.
- **Validation:** Java 21 focused tests passed, including the higher-timeframe
  matrix, validation, consolidation and early outcome-schema verifier tests.

## FIX-154 closure work — 2026-10-10

- Added explicit SOFT-policy tests for conservative/direct/context/consolidated
  sizing, neutral-5m non-escalation, untrusted non-bearish context, quality
  boundaries, and Replay audit suppression.
- Extended the dedicated disposable MySQL migration test to assert V107 and
  Replay-table/FIX-154 audit schema creation when the MySQL release test stage
  is run.
- Added read-only FIX-154 health counters at `/api/fix154/health`.
- The MySQL test remains an external release-stage proof: it cannot run in this
  session without the designated disposable MySQL environment variables.

## Scope correction — 2026-10-10

FIX-153 reconciliation/runtime work remains postponed. The temporary changes
to its outcome-schema verifier and its MySQL integration test were reverted.
FIX-154 does not enable or alter reconciliation behavior.

## FIX-154 reviewer handoff — 2026-10-10

Added `codex-docs/FIX-154_IMPLEMENTATION_HANDOFF.md`, listing the implemented
policy, database changes, source files, tests, executed Java 21 verification,
and the remaining external MySQL/release proof.

## FIX-153 automatic reconciliation enabled — 2026-10-10

- Problem: a missing historical candle left current candle ingestion healthy
  but blocked later signal generation for the affected lane.
- Root cause: the data-quality gate correctly rejects incomplete windows; the
  recovery component was present but disabled.
- Solution: enabled OFF-mode token-free reconciliation, kept its one-worker
  executor as the default, and added deterministic tests for reconnect
  evidence, bad local close-time replacement, and per-pass REST budgeting.
- Files changed: `application.yml`, `LocalCandleReconciliationConfig`, and
  `LocalCandleReconciliationServiceTest`.
- Database impact: uses existing V108 audit/outcome tables; no data rewrite or
  backfill was executed by this change.
- Production/Replay impact: recovery is non-executable and has no Replay
  authority. Targeted Java 21 reconciliation tests passed; real-MySQL release
  tests still require the disposable test environment.

## Flyway V107 checksum recovery — 2026-10-10

- Problem: startup failed because the V107 file was changed after that version
  had been applied to the local database.
- Solution: restored the exact applied V107 content and checksum
  (`-501147284`). Moved the later Replay-table columns and audit
  `hard_confirmation_present` column into forward-only V109.
- Verification: Flyway's checksum calculator matches the stored V107 value;
  `Fix154MigrationContractTest` and `LocalCandleReconciliationServiceTest`
  pass on Java 21.
# 2026-10-15 — FIX-156 Replay-only foundation (in progress)

- Problem: the existing Replay path reads live wallet settings and exchange
  constraints, so it cannot support an isolated historical parity claim.
- Solution started: added Replay-local policy-profile/access-guard classes and
  additive V110 tables (`replay_policy_profile`, `replay_fixture`,
  `replay_run_output`, `replay_decision_trace`). The guard fails closed for
  account/unregistered table names.
- Files/classes: `com.crypto.regression.replay.*`, V110, and Replay-only unit
  tests. All new code is tagged `FIX-156`.
- Database impact: additive Replay-owned schema only; no migration was applied.
- Production impact: none. Replay service rewiring, fixture use, and parity
  comparison are still incomplete; this is not deployable or final.

## 2026-11-08 — FIX-156 Replay-owned execution summary (in progress)

- Problem: a Replay execution ended without publishing its result through the Replay-owned evidence boundary.
- Solution: `ShadowProductionReplayService` now publishes a non-parity-claimed execution summary through `ReplayRunOutputStore` into `replay_run_output`.
- Files/classes: `ReplayRunOutputStore`, `ReplayRunOutputStoreTest`, and `ShadowProductionReplayService`.
- Database impact: uses the existing additive Replay-owned V110 table; no migration was applied and no Production data was changed.
- Production/Replay impact: none/Replay isolation-parity remains incomplete. The output status is `NOT_PARITY_CLAIMED` and is not a parity certificate.

## 2026-11-08 — FIX-156 fail-closed run certificate (in progress)

- Problem: Replay had no durable certificate stating that parity had not been measured.
- Solution: `ReplayRunCertificateStore` records both analysis and lifecycle status as `NOT_MEASURED` until qualified Production comparison and price-coverage evidence exist.
- Files/classes: `ReplayRunCertificateStore`, `ReplayRunCertificateStoreTest`, and `ShadowProductionReplayService`.
- Database impact: uses the existing additive `replay_run_certificate` table; no migration was applied.
- Production impact: none. This prevents an unsupported parity claim; it does not establish parity.

## 2026-11-14 — FIX-156 Replay-owned lifecycle schema (in progress)

- Problem: the Replay engine still persists lifecycle state in legacy Replay test tables.
- Solution started: forward-only V112 defines Replay-owned position, execution, and position-management tables with compatible Replay lifecycle columns and run/time indexes.
- Database impact: additive migration only; it was not applied.
- Production impact: none. Writer/reader migration to these tables is still pending.

## 2026-11-14 — FIX-156 Replay lifecycle writer/reader migration (in progress)

- Solution: the Shadow Replay writer now persists position, execution, and management lifecycle state to `replay_position`, `replay_execution`, and `replay_position_management`. Active run counts, trades, chart executions, and management views read the same Replay-owned tables.
- Verification: targeted Java 21 FIX-156 Replay suite passed.
- Database impact: requires additive V112 at a future controlled migration run; it was not applied here.
- Production impact: none. Historical archive/proven-trade tools remain separate historical functionality; qualified parity comparison remains pending.

## 2026-11-14 — FIX-156 Replay BUY decision trace (in progress)

- Solution: each Replay-local BUY-policy decision is written to `replay_decision_trace` with its key, timestamp, code, explanation, and `NOT_MEASURED` status.
- Verification: targeted Java 21 FIX-156 Replay suite passed.
- Production impact: none. The trace is Replay evidence only and does not establish a Production comparison.

## 2026-11-14 — FIX-156 Replay lifecycle access policy (in progress)

- Solution: Replay-owned lifecycle tables are now registered in `ReplayAccessPolicy`, and the Shadow Replay flow requires those table permissions before lifecycle persistence.
- Verification: targeted Java 21 FIX-156 Replay suite passed, including rejection of legacy wallet test tables.
- Production impact: none. Full per-statement enforcement and qualified parity evidence remain pending.

## 2026-11-14 â€” FIX-156 qualified Replay price coverage (in progress)

- Solution: `ReplayPriceCoverageStore` persists one Replay-owned coverage record per run/symbol. Coverage is qualified only when supplied historical price events cover both execution-window boundaries with no gap greater than two minutes.
- Verification: targeted Java 21 FIX-156 Replay suite passed.
- Production impact: none. Qualified coverage alone does not establish analysis or lifecycle parity.

## 2026-11-14 â€” FIX-156 active Replay read access policy (in progress)

- Solution: active Replay run metadata, Replay signal, position, execution, management, and historical candle display reads now explicitly declare their `ReplayAccessPolicy` operation.
- Verification: targeted Java 21 FIX-156 Replay suite passed.
- Production impact: none. Historical archive and regression-administration paths are excluded from parity execution.

## 2026-11-14 â€” FIX-156 V112 disposable-MySQL migration coverage

- Solution: the existing guarded disposable-MySQL Flyway test now asserts successful V112 application and all three Replay-owned lifecycle tables.
- Verification: Java 21 `test-compile` passed.
- Database impact: no migration was applied. The test cannot run until a dedicated loopback `FIX153_TEST_MYSQL_URL`, disposable marker, and verified server UUID are provided.
- Production impact: none; the harness rejects non-disposable targets.

## 2026-11-14 â€” Consolidate application logs under `logs/`

- Problem: JDBC/application logs used a configurable directory and the deployment launcher placed JVM stdout/stderr in a release directory.
- Solution: Logback now uses the project-relative `logs/` directory for both rolling appenders; the deployment launcher creates `<DeployDirectory>/logs` and redirects JVM stdout/stderr there.
- Files: `src/main/resources/logback-spring.xml`, `scripts/deploy/Deploy-Trader.ps1`.
- Database/Replay impact: none.
- Verification: Logback XML and PowerShell syntax parsing passed. No deployment was run.

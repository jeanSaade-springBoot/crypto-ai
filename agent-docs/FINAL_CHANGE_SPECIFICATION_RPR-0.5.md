# Replay / Production Business-Logic-Parity Redesign

**Revision:** RPR-0.5-DRAFT  
**Status:** DRAFT — NOT APPROVED — IMPLEMENTATION FORBIDDEN  
**Created:** 2026-10-10  
**Supersedes for review:** RPR-0.4-DRAFT only; it does not change Production.  
**Implementation authorization:** NOT AUTHORIZED

## 1. Business objective

**Current business behavior:** manual Replay regenerates signals and runs a
shadow position model, but it can read `wallet_settings`, has different
close/price/signal ordering from Production, can include unclosed candles, and
cannot support several ID-dependent or signal-path exit rules exactly.

**Required business behavior:** provide a manual, isolated Replay that uses the
same Production business rules wherever durable historical evidence permits,
but runs from a versioned virtual account and never reads Production account
state, writes Production tables, or calls external HTTP services.

**Why:** Replay must become a trustworthy regression and investigation tool
without pretending that candle data alone reconstructs unknown live ordering or
real Production wallet history.

## 2. Agreed business flow

### Current flow

`RegressionTestWorker` builds a timeline, regenerates signals, feeds
`ShadowProductionReplayService`, and writes Replay result tables. Some shared
services read current account settings or use different historical branches.

### Agreed target flow

1. The user starts a run with a saved immutable `ReplayAccountProfile` and an
   explicit requested mode.
2. Before any generated signal, virtual-position, execution, management, or
   outcome row is written, `ReplayEvidenceQualifier` builds an evidence
   certificate from the requested window.
3. The run is refused with one run-level diagnostic if required evidence is
   absent. A refused run writes no other Replay artifact.
4. A qualifying run uses only closed, identity-matching candles, accepted
   persisted price observations, as-of context evidence, and the profile.
5. A virtual-account adapter invokes extracted pure Production policies for
   entry, sizing, adds, protection and exits. It never reads `wallet_*` account
   tables or performs network I/O.
6. Replay records a stable, run-scoped Replay signal identity and the evidence
   IDs/order used for every decision.
7. The run is labelled with the strongest claim actually earned:
   `ANALYSIS_PARITY`, `ENTRY_DECISION_PARITY`,
   `PRODUCTION_LOGIC_SIMULATION`, or `REFUSED_INSUFFICIENT_EVIDENCE`.

### Claim levels

| Level | Meaning | Permitted only when |
|---|---|---|
| `ANALYSIS_PARITY` | Signal/indicator/context outcome comparison | Exact closed candle identity, source/code/config evidence and required context evidence are available. |
| `ENTRY_DECISION_PARITY` | Entry decision comparison for a no-open-position window | Analysis parity plus complete decision/price/processing evidence; no Production account state is read. |
| `PRODUCTION_LOGIC_SIMULATION` | Same rules over virtual account state | Profile and required inputs exist, but historical oracle/evidence is insufficient for a parity certificate. |
| `REFUSED_INSUFFICIENT_EVIDENCE` | No simulated lifecycle output | Mandatory evidence is missing, ambiguous, wrong-era, or unsafe. |

Full historical Production-wallet lifecycle parity is **out of scope** because
the user forbids reading Production account state. A virtual lifecycle is valid
only as `PRODUCTION_LOGIC_SIMULATION` unless a future approved, isolated account
evidence source exists.

## 3. Confirmed root cause and evidence

**Confirmed root cause:** Replay is a partly independent shadow implementation,
not a fully ordered invocation of Production rules with an isolated account
boundary.

**Contributing factors:**

- `RegressionTestWorker` timeline loading can include `closed=0` candles while
  indicator snapshots select closed candles.
- `AnalysisService.analyzeForRegression` returns unsaved signals; current Replay
  rows omit a stable signal identity, breaking ID-dependent policies.
- `ShadowProductionReplayService` and shared validation paths read current
  `wallet_settings`; near-TP minimum-order evaluation can reach a Binance client
  once its currently latent ID path becomes reachable.
- Production has separate price-event and signal-path position management
  sequences (advisory stop, TP, label-free profit lock, SELL/add suppression,
  progressive adds). Current Replay does not reproduce all of them.
- Historical configuration, derivatives, order-book, sentiment availability,
  close-message lineage and processing timing are not universally durable.

**Evidence:** coordinator run `20261010-121438`, especially Claude/Codex round
5; current source paths cited in registries below.

**Rejected hypothesis:** candle OHLCV plus candle close timestamps alone proves
historical live-event ordering. It does not prove whether TP/floor was first
inside a candle, which closing receipt triggered work, or what context was
visible when a concurrent analysis started.

**Remaining unknowns:** deployed JAR/config per historical window; Flyway
history/checksums; evidence completeness; close-message lag; whether exact
historical order-book/derivative/sentiment availability can be reconstructed.
These are qualification inputs, not assumptions.

## 4. Scope

### In scope

- One-symbol, one virtual-account Replay per run for the first release.
- Closed-candle identity/finality enforcement.
- Versioned virtual profile, no Production account-state reads, no HTTP.
- Stable Replay IDs and explicit event/order evidence.
- Shared pure-policy extraction needed to reproduce current Production business
  flow without changing its business policy.
- Exact-price and simulation paths with honest qualification/result labels.
- Refusal-before-write, deterministic cleanup/cancellation, tests and metrics.

### Out of scope

- Production strategy/risk-policy changes, real-exchange execution, database
  writes to Production wallet/account state, and multi-symbol virtual portfolio
  scheduling.
- Claiming strict parity for collector-era or otherwise incomplete evidence.
- Removing FIX-11K, FIX-11Q, old FIX-122 selection, or experimental paths in
  this change. Their retirement requires a separate caller/data/UI inventory
  and explicit user decision.

## 5. Complete class/file change registry

| ID | Module | File/Class | Change Type | Reason | Flow step |
|---|---|---|---|---|---|
| RPR5-01 | regression | `RegressionTestWorker` | MODIFY | Qualify before writes; closed identity only | 2–4 |
| RPR5-02 | regression | `ReplayEvidenceQualifier` | CREATE | Build immutable evidence certificate/refusal | 2–3 |
| RPR5-03 | regression | `ReplayAccountProfile`, store/repository | CREATE | Virtual account/config boundary | 1, 5 |
| RPR5-04 | regression | `ReplayRunContext`, `ExecutionReplayScope` | MODIFY | Run-scoped IDs/profile/evidence; clear state | 4–6 |
| RPR5-05 | regression | `ShadowProductionReplayService` | MODIFY | Invoke ordered shared policies only through virtual adapter | 5–7 |
| RPR5-06 | wallet/execution | pure policy adapters extracted from `WalletAutoExecutionService`, `PaperTradingService`, `TradeExecutionValidationService`, `BinanceMinimumExecutionPolicy` | MODIFY/CREATE | Prevent account reads/HTTP while preserving rules | 5 |
| RPR5-07 | regression | `ReplayVirtualAccountAdapter` | CREATE | Isolated account/position/effect state | 5 |
| RPR5-08 | regression | `ReplayParityValidator` | MODIFY | Separate analysis/entry oracle from simulation | 7 |
| RPR5-09 | persistence | V110 replay tables/columns | MIGRATION | Immutable profile/certificate/run metadata | 1–7 |
| RPR5-10 | configuration | `application.yml`, typed properties | CONFIG | Resource limits and mode defaults | 1 |
| RPR5-11 | tests | regression/production policy tests + MySQL IT | TEST | Prove ordering, isolation and migration behavior | all |
| RPR5-12 | documentation | listed in §16 | DOCUMENTATION | Record final validated behavior | final |

## 6. Class-by-class technical specification

### [RPR5-01] RegressionTestWorker

- **Current responsibility:** builds the replay timeline, persists test signals,
  and invokes shadow execution.
- **Required change:** call `ReplayEvidenceQualifier.qualify(request)` before
  any generated Replay row is written. Query only `closed=1` candles and retain
  `(symbol, interval, open_time, close_time, provenance)` for each timeline
  item. Before signal generation, require the generated snapshot candle identity
  to equal the timeline candle identity; otherwise refuse/skip with an explicit
  run diagnostic according to the requested mode.
- **After:** assign a stable run-scoped `replay_signal_key` derived from run ID
  plus candle identity; never use/null-match a Production `trade_signal.id`.
- **Side effects:** writes occur only after qualification; refused run writes
  only its run/certificate status.
- **Production impact:** none in business behavior; shared policy tests required.

### [RPR5-02] ReplayEvidenceQualifier

- **Responsibility:** evaluate evidence before any Replay lifecycle output.
- **Inputs:** window, requested claim level, source baseline/config fingerprint,
  profile, candle identities, accepted price event lineage, processing outcomes,
  requested context dependencies.
- **Rules:**
  - Accepted price evidence comprises `LEGACY`/`APPLIED` rows Production could
    use. `NON_CANONICAL` and `PROTECTION_NOT_APPLIED` remain deliberate
    exclusions, not missing observations.
  - Exact-price claim requires the close-message lineage or a proven inference;
    otherwise it refuses strict parity and may permit simulation only.
  - Context source is selected per dependency: stored as-of evidence, historical
    recomputation, or refusal. This applies independently to BTC, higher TF,
    order book, derivatives, sentiment and fundamentals.
  - Recovered/historical-only/processing-expired signals are reproduced from
    durable processing evidence or make strict parity ineligible.
- **Error handling:** no exception may leave partial Replay output. Persist a
  bounded refusal reason list on the run/certificate only.

### [RPR5-03/RPR5-07] Virtual profile and account adapter

- **Required responsibility:** replace all decision-time reads of Production
  account state with an immutable profile and mutable state held only in
  Replay-owned tables/memory for the run.
- **Profile fields:** virtual cash/balances; reserve; position/global limits;
  daily loss/budget rules; execution constraints (minimum notional, step/tick
  sizes); fee/slippage model; relevant feature flags; configuration/source hash.
- **Forbidden reads:** `wallet_asset`, `wallet_trade`, `wallet_managed_position`,
  `paper_position`, daily Production statistics/snapshots and `wallet_settings`.
  Permitted read-only evidence tables are explicitly enumerated in §9.
- **No HTTP:** constraints come only from profile; an adapter injected into
  `BinanceMinimumExecutionPolicy` must fail a Replay run if it would invoke the
  Binance client.

### [RPR5-04] ReplayRunContext / ExecutionReplayScope

- Carry profile, claim level, certificate, deterministic replay signal keys,
  position state, opportunity state and reference/visibility clocks.
- Clear all thread-local/Replay state in `finally`, including failure and
  cancellation. Do not allow a Replay ID to equal a Production signal ID.
- First release rejects a request containing more than one symbol.

### [RPR5-05/RPR5-06] Shadow replay and pure shared policy adapters

- Extract pure input/output policies; Production remains their caller with its
  existing repositories and transactions. Replay supplies virtual adapters.
- **Exact-price ordered flow:** accepted price protection first; then for every
  valid signal-price path run the same ordered Production checks: advisory
  position stop, TP close without extension, label-free profit-lock trigger,
  rebase/floor, SELL/add suppression, then progressive add gate.
- **Simulation flow:** when exact price ordering is unavailable, run the same
  policy rules over declared candle-price assumptions and label output
  `PRODUCTION_LOGIC_SIMULATION`; never label it parity.
- Record decision clock separately from fill/event clock and record evidence IDs
  and ordering slot used.

### [RPR5-08] ReplayParityValidator

- Compare only at the earned claim level. Match signals by symbol/interval/
  candle-open-time, not generated-time equality. Generated time is diagnostic
  clock evidence.
- Entry oracle includes final permission, reason/result, sizing-cap inputs,
  data-quality, signal mode, assessment/audit code and accepted price-event IDs.
- It must not assert full lifecycle equality when Production account state is
  deliberately excluded.

## 7. Method-level change registry

| ID | Class | Method/area | Current logic | Agreed new logic | Reason |
|---|---|---|---|---|---|
| RPR5-M01 | `RegressionTestWorker` | run/timeline generation | writes before full evidence guard; accepts open rows | qualify first; closed identity equality before generation | no stale/duplicate Replay signals |
| RPR5-M02 | `TechnicalIndicatorService` integration | historical snapshot | snapshot identity not asserted by Replay | return/assert source candle identity | parity lineage |
| RPR5-M03 | `ShadowProductionReplayService` | entry/exit/add loops | independent/simplified ordering | call ordered virtual-policy adapter | business-flow parity |
| RPR5-M04 | `ExecutionReplayScope` | enter/close/lookups | nullable/Production-like IDs and incomplete cleanup | run-scoped key + deterministic cleanup | ID/bleed prevention |
| RPR5-M05 | `TradeExecutionValidationService` | settings lookup seam | may read wallet settings during Replay | accept profile-backed settings port in Replay | isolation |
| RPR5-M06 | `BinanceMinimumExecutionPolicy` | minimum constraint lookup | may call Binance | profile constraint in Replay; fail-fast port | no live HTTP |
| RPR5-M07 | `ReplayParityValidator` | comparison | signal-only/pre-wallet | level-specific oracle/report | honest result |

## 8. Logging registry

| ID | Area | Level | Event | Safe context |
|---|---|---|---|---|
| RPR5-L01 | qualifier | INFO | `REPLAY_REFUSED` | run ID, claim level, reason codes, symbol/interval/window |
| RPR5-L02 | qualifier | INFO | `REPLAY_CERTIFIED_INPUTS` | certificate ID/hash, evidence counts/era, no account values |
| RPR5-L03 | virtual adapter | DEBUG | `REPLAY_DECISION` | run/replay-signal key, route, result, evidence IDs/order |
| RPR5-L04 | HTTP/account guard | WARN | `REPLAY_FORBIDDEN_DEPENDENCY` | component, dependency type, run ID; no secrets |
| RPR5-L05 | worker | WARN | `REPLAY_CANDLE_IDENTITY_MISMATCH` | run, symbol, TF, expected/actual open time |

All backend timestamps remain UTC; UI/reporting converts to KSA where shown.

## 9. Database change registry

| ID | Object | Change | Reason | Migration | Rollback |
|---|---|---|---|---|---|
| RPR5-D01 | `replay_account_profile` | create immutable profile JSON/hash/version/created_at | reproducible virtual account | `V110__replay_virtual_profile_and_evidence.sql` after preflight confirms V109 | forward migration: disable profile use; retain evidence |
| RPR5-D02 | `regression_test_run` or dedicated certificate table | profile ID, requested/earned claim level, source/config hash, certificate status/reasons | truthful run classification | V110 | nullable forward-compatible columns / retain history |
| RPR5-D03 | Replay signal/execution output | run-scoped replay signal key and evidence/event-order identifiers | ID and ordering proof | V110 | additive; legacy rows remain legacy |

**Preflight:** before authoring/applying V110, read `flyway_schema_history` and
source migration inventory. V110 is valid only if V109 is the actual last
migration on the target branch/database; otherwise allocate the next free
forward migration. Never edit applied migrations.

## 10. Configuration registry

| ID | Property | Current | Proposed | Reason |
|---|---|---|---|---|
| RPR5-C01 | `replay.default-claim-level` | implicit/ambiguous | `PRODUCTION_LOGIC_SIMULATION` | avoid impossible parity default |
| RPR5-C02 | `replay.strict-parity.enabled` | none | false by default | explicit rollout |
| RPR5-C03 | `replay.max-concurrent-runs` | existing worker guard/unclear | 1 | protect Production JVM/pool |
| RPR5-C04 | `replay.max-db-connection-share` / executor limit | none | bounded | no Production freshness impact |
| RPR5-C05 | `replay.profile.required` | none | true | no Production wallet fallback |

## 11. Trading-behavior registry

| ID | Area | Current | Agreed new rule | Replay | Production |
|---|---|---|---|---|---|
| RPR5-T01 | Entry/exit/add rules | Replay differs in several paths | same pure Production policy sequence, virtual adapters | changes | no policy change |
| RPR5-T02 | Account state | Replay reads settings | profile only; no account-state reads | changes | none |
| RPR5-T03 | Missing evidence | may write partial output | refuse strict run before output; simulation is labelled | changes | none |
| RPR5-T04 | Fees/slippage | paper baseline is zero-fee | profile declares model; zero remains explicitly reported if selected | changes | none |

## 12. Test registry

| ID | Requirement | Test type | Scenario | Expected result |
|---|---|---|---|---|
| RPR5-X01 | RPR5-M01/M02 | unit + MySQL | open candle in window | refused/ignored before signal write |
| RPR5-X02 | RPR5-M01 | regression | snapshot identity differs from timeline | no stale duplicate signal |
| RPR5-X03 | RPR5-M04 | unit | ID-dependent near-TP/BUY consumption | stable replay key works; cannot match Production ID |
| RPR5-X04 | RPR5-M05/M06 | integration | any wallet read/HTTP attempt | fail-fast; no external call |
| RPR5-X05 | RPR5-M03 | differential | price-event and signal-path exits/adds | same policy order/result as Production fixtures |
| RPR5-X06 | qualifier | integration | incomplete orderbook/derivative/sentiment/config | strict refusal before writes |
| RPR5-X07 | qualifier | integration | deliberate excluded price events | exclusion retained, not falsely missing |
| RPR5-X08 | claim levels | E2E | qualifying no-position window | analysis/entry comparison keys match |
| RPR5-X09 | virtual lifecycle | E2E | entry, partial harvest, TP, lock, stop, add | simulation audit complete |
| RPR5-X10 | isolation | concurrency | Replay alongside production-like analysis load | bounded resources/no prohibited tables |
| RPR5-X11 | cancellation | concurrency | cancel/failure during run | scope cleared; no partial output after refusal |
| RPR5-X12 | migrations | real MySQL | fresh/upgrade target with baseline/V107–V110 | Flyway succeeds; schema contract verified |

## 13. Observability and validation registry

| ID | Requirement | Evidence | Success criterion |
|---|---|---|---|
| RPR5-V01 | qualification integrity | refusal/certificate counts | zero generated artifacts for refused runs |
| RPR5-V02 | account isolation | repository/HTTP guard tests and logs | zero Production account reads/HTTP |
| RPR5-V03 | ordering | event IDs/ordering slot/clock audit | every exact-price decision traceable |
| RPR5-V04 | resource safety | DB pool/executor metrics | Replay stays within configured bound |
| RPR5-V05 | truthfulness | result-level distribution | no simulation labelled parity |

## 14. Risks

| Risk | Severity | Mitigation | Validation |
|---|---|---|---|
| Wrong historical parity claim | High | evidence qualification/claim levels | RPR5-X06–X08 |
| Production behavior changes via shared extraction | High | pure-policy tests, no policy edits, rollback | RPR5-X05 |
| Production account leakage | High | forbidden-port guards/profile only | RPR5-X04 |
| Replay harms live freshness | High | one run/resource bounds | RPR5-X10 |
| Flyway collision | High | target history preflight/real MySQL upgrade | RPR5-X12 |
| Future data look-ahead | High | as-of availability model/refusal | RPR5-X06 |

## 15. Rollback plan

- **Code:** revert the RPR-0.5 commit as one unit; Production business policy
  remains unchanged by design.
- **Database:** no destructive rollback. Disable strict mode and retain
  immutable Replay profiles/certificates/results; use a new forward migration
  for a schema correction.
- **Configuration:** set `replay.strict-parity.enabled=false`; reject affected
  requests rather than falling back to Production account reads.
- **Deployment:** do not enable any new Replay route until all RPR5-X tests and
  resource validation pass on Java 21 and real MySQL.

## 16. Documentation registry

| ID | Document | Required update | When |
|---|---|---|---|
| RPR5-DOC01 | `codex-docs/ARCHITECTURE.md` | final Replay/Production boundaries and data flow | after implementation validation |
| RPR5-DOC02 | `codex-docs/CHANGELOG_AI.md` | problem/root cause/files/DB/Production/Replay effects | after implementation validation |
| RPR5-DOC03 | `codex-docs/TRADING_RULES.md` | clarify no Production trading-policy change | after implementation validation |
| RPR5-DOC04 | `codex-docs/INVESTIGATION_LOG.md` | rejected historical-parity assumptions and evidence limits | after validation |
| RPR5-DOC05 | agent/Claude guidance | only through required dual-review/user gates | after approval |

## 17. Master implementation registry

| ID | Type | Artifact | Agreed change | Status |
|---|---|---|---|---|
| RPR5-01..08 | source | §5–7 classes | isolated ordered Replay flow | PLANNED |
| RPR5-09 | migration | V110 or next verified forward migration | profile/certificate/trace metadata | PLANNED |
| RPR5-10 | config | §10 properties | explicit safe defaults/limits | PLANNED |
| RPR5-11 | tests | §12 | required verification | PLANNED |
| RPR5-12 | docs | §16 | final-state docs | PLANNED |

## 18. Dual-agent final review

Codex verdict: NOT REVIEWED  
Claude verdict: NOT REVIEWED  
Unresolved material disagreements: NOT YET REVIEWED  
Coordinator status: CONSENSUS NOT ACHIEVED  
Implementation authorization: NOT AUTHORIZED

## 19. User approval

User decision: NOT YET PROVIDED  
Approved exact revision: NONE

No implementation may begin until this exact document is approved by Codex and
Claude and this section records explicit user approval.

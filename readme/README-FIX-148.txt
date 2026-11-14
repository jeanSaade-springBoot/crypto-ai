FIX-148 — Automatic validated first activation, no per-signal operator approval

User requirement supersedes the manual-first-cutover workflow in FIX-146/147.
The supplied application.yml enables shared-market.automatic-first-cutover-enabled
(default true). LIVE mode and existing activation-approved deployment configuration
remain required. No isolated approval console or CUTOVER_APPROVER login is required
for a genuinely new PENDING_CUTOVER/AUTO_OBSERVED symbol that passes these checks.

Activation checks
- First activation only: no prior approval audit or published cutover history.
- No active/fenced phase-stream marker or approval provenance already present.
- Source cursor has not regressed below discovered sequence.
- A live closed 1m event within 90 seconds is present in the last 512 symbol sequences.
- At least 300 closed canonical candles for every configured interval plus 1m.
- No unfinished processing work except exact FIX-145 audited legacy cancellations.
- No unfinished deliveries, review records or unresolved protection attempts remain.
- Source snapshot must still be within 15 seconds when the activation commits.

Source reads finish before the local symbol transaction. Under symbol lock, recheck
exact observation boundary, first-activation status and durable work evidence.
Write existing cutover approval audit with actor FIX148_SYSTEM and reference
FIX-148-AUTO-UUID, set SYSTEM_VALIDATED/READY, and publish immutable history in the
same transaction. A failed audit or state update rolls everything back. Approved
operator and system boundaries are never moved automatically on later restarts.
New enabled symbols with no state can bootstrap observation state automatically.
Normal readiness still verifies exact audit/boundary and source/history checks.

Expired records
During first activation, examine at most 8 old phase-0 PENDING/WAITING observations
per pass, under symbol then delivery lock. No owner/start/completion evidence may
exist. Closed observations still within analysis eligibility remain untouched.
Exclude any linked source/natural-identity work, signal or price-effect record.
For eligible rows, audit AUTO_EXPIRE_NEVER_STARTED and mark SKIPPED/NOT_APPLICABLE;
never claim that a business action completed. Source events/candles are retained.
If additional old rows remain, activation waits and the next refresh continues.
Existing completed-work and analysis-only reconcilers remain unchanged and active
according to their existing configuration. No automatic uncertain-business replay.

Limitations made explicit
This is NOT an unconditional promise to trade despite bad data or unknown effects.
An uncertain started trade/protection attempt retains its fence. Prior approved
state that was reset/lost is not silently rebuilt; ambiguity remains a reported
blocker. These cases need proven recovery, not age-based abandonment. The new
readiness reason AUTOMATIC_ACTIVATION_WAITING indicates automatic validation has
not succeeded; it does not mean a manual approval is routinely required.
The operational flag waitingCutoverDoesNotAutoApprove is false while this policy
is enabled; already approved symbols still undergo recurring readiness checks.

After activation, only events beyond the committed source boundary and time are
eligible for live execution. The current already-arrived close is not replayed.
Expect fresh signals after subsequent live closes, and verify BTC freshness and
position protection. Scoring, sizing, exit rules and Replay calculations are not
changed; Production admission availability intentionally changes. Run existing
Production/Replay parity and protection tests rather than assuming equivalence.

Validation and deployment
Ten new consumer/H2 regressions cover automatic readiness and future price ticks,
restart idempotence, audited stale retirement, unresolved protection retention,
missing/stale source context, prior-audit rejection, rollback on audit failure,
new-symbol bootstrap/source regression, natural-identity signal evidence, and
rejection of a system provenance marker without its exact durable audit.
Tests are authored, not executed in this environment. Java syntax parsing and a
dependency-free authority-policy smoke passed. Java 21 Maven, Spring configuration,
H2, real MySQL lock ordering/concurrent activation/crash rollback/Flyway, backlog
load and end-to-end recovery remain release gates. No new migration is necessary;
V100/V101/V102 are unchanged. V102 account is optional for manual fallback only.

Merge and run Java 21 mvn -B -ntp clean verify. Restart only the built candidate
against the intended schemas. Watch [FIX-148][AUTOMATIC_FIRST_CUTOVER] and inspect
/api/fix142/readiness, then measure candle-close-to-signal latency and verify actual
protection. Repeated restarts must preserve the same boundary and audit count.
UTC is stored; logs explicitly include UTC and KSA boundary time.

Timestamp correction after local Maven execution
The supplied Java 21 clean verify log ran 508 tests: 1 failure, 7 errors.
Seven automatic-activation tests failed on a Timestamp cast of H2's
OffsetDateTime CURRENT_TIMESTAMP result. The readiness test also reported
CHECK_FAILED. Both activation-boundary and retirement-close conversions now
use an explicit JDBC timestamp adapter: Timestamp, OffsetDateTime and Instant
preserve their instant; timezone-free LocalDateTime is interpreted as UTC.
Null/unsupported types fail closed. One additional regression verifies offset
and microsecond preservation and invalid evidence rejection. Existing H2
activation tests still use real CURRENT_TIMESTAMP, not a stubbed cast.
This revision has NOT run the full Maven/H2 suite here. Re-run clean verify;
prior test failures are recorded evidence, not proof this correction is green.
No migration, approval policy, trading decisions or Replay rules changed.

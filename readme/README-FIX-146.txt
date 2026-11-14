FIX-146 — Per-symbol trading readiness; application availability without automatic approval

Problem and observed recovery
The local 31 legacy attempts were operator-cancelled with original-state audit and no replay.
The two never-started SOL/UNI open observations were separately retired with audit and no replay.
Neither recovery approved cutover. All 21 observed symbol states remained PENDING_CUTOVER /
AUTO_OBSERVED. The old ApplicationReadyEvent validator then stopped the whole Spring context
on the first unapproved symbol, preventing diagnostics and recovery APIs from staying available.

Change in this package
LIVE startup initializes a process-local, fail-closed readiness registry rather than throwing
for recoverable per-symbol operational conditions. Approved and validated symbols may proceed;
blocked symbols receive no NEW shared admission, analysis claim, or protection claim. Existing
owned work is not silently replayed, cancelled or reclassified. Application availability does
not certify that protection or execution is working for a blocked symbol.

Checks retained
Activation approval, exact state/boundary/immutable approval audit, non-regressed source cursor,
and 300 closed candles for each required interval remain mandatory. Legacy work must either be
terminal or match the exact FIX-145 cancellation audit. Cross-symbol ownership inconsistencies
block both identifiable symbols. Work with unknown symbol scope blocks every symbol. Partial
unowned phase-stream deliveries and incompatible ownership mode block their symbol. Existing
FIX-142 marker/protection/analysis review fences and operator evidence requirements remain.
No status=READY update, approval insertion, automatic cancellation, uncertain retry, or replay
is added. Immutable cutover history is published only after validation; if absent, publication
rechecks the exact validated approval boundary under symbol lock. Existing history avoids
periodic writes. V100/V101 are unchanged; FIX-146 needs no new migration.

Readiness operation and diagnostics
A dedicated clock refreshes readiness with round-robin progression every 10 seconds. Each pass
has a soft 20-second budget, local query/transaction timeouts, and checks expire after 60 seconds.
The budget is NOT a hard connection-acquisition, network or lock-duration guarantee. Failed or
expired checks block new work. Removed/re-enabled symbols must be validated again. New work
checks readiness before selection and at locked claim/admission boundaries. Existing durable
approval checks remain in place. Strict validateCutover() remains for explicit acceptance checks.
Configuration and initialization failures can still stop application startup.

ROLE_ADMIN GET /api/fix142/readiness reports READY, DEGRADED or NO_READY_SYMBOLS, ready and
blocked symbols, reason codes, and UTC/KSA check times. GET /api/fix142/health includes this
snapshot along with existing marker, analysis and protection diagnostics. Log tags:
[FIX-146][STARTUP_READINESS], [FIX-146][SYMBOL_READINESS], [FIX-146][READINESS_*_FAILED].
Existing JDBC timing instrumentation remains responsible for database statement/transaction spans.

Important operational limits
With all 21 cutovers unapproved, the expected result is application available + NO_READY_SYMBOLS;
trading does NOT automatically resume. Use the existing isolated FIX-132 FIRST_CUTOVER approval
workflow with actual operator evidence, then allow the readiness refresh to validate it. Do not
mass-update READY or manufacture approvals. Keep cancelled/retired records unreplayed.
A blocked symbol with an open position cannot be assumed protected by new shared ticks; inspect
existing health/fences and perform operator recovery. This change provides availability and
isolation, not a replacement for cutover acceptance or protection recovery.

Validation status
Nine JUnit regressions added: four registry tests and five consumer/H2 readiness tests.
A dependency-free readiness smoke compiled and passed here on Java 17. Six changed/new Java
files passed Java syntax parsing; registry JavaScript syntax checked. These do NOT establish
Java 21 project compilation, Spring wiring, H2 execution or MySQL semantics. The existing pom.xml is
unchanged; Java 21 and Maven are unavailable here. The previously reported 483
passing tests predate FIX-146 and are not FIX-146 evidence.

Required local verification before live use
Merge src into the existing project; preserve pom.xml and local configuration. Run Java 21:
  mvn -B -ntp clean verify
Start using the explicit Java 21 executable. Verify diagnostics remain reachable with all
symbols unapproved and no new delivery/work/protection attempts occur. In a disposable MySQL
schema, verify one blocked coin does not stop an approved coin, approval/audit restoration
re-enables only the intended coin, missing/expired checks fail closed, history publication
rechecks the boundary, and existing owned work/fences do not replay. Run the existing FIX-142
MySQL protection/ownership/concurrency and backlog gates; verify Production/Replay regression
suite and admin authentication. No Replay decision or exit policy change is intended.

FIX-146 build correction: removed accidentally copied readiness deadline check from
dispatchProtectionTicks(); protection retains its existing worker-capacity bound.
Full Maven compilation remains required; syntax parsing does not resolve variable scope.

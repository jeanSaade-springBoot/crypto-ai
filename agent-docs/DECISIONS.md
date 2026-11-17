# APPROVED TEAM DECISIONS

Persistent record of technical/design decisions that passed the applicable
Codex + Claude review gate and user approval gate. Preserve superseded decisions
for traceability; do not silently rewrite history.

No approved decisions recorded yet.

### JEAN-OVERRIDE-20261118-001 — FIX-157 corrected Replay-only ledger

Date/time KSA: 2026-11-18

Scope: Implement the corrected Replay-only candidate-recovery ledger rules,
tests, enforcing Replay data access boundary, V110–V114 disposable-MySQL
coverage, scoped documentation corrections, and only the configurable JDBC log
directory restoration in `logback-spring.xml`.

Waived: ALL pending items for this scope.

Jean's exact words:

> proceed direct from jean\
> scope: Implement the approved FIX-157 corrected Replay-only scope: A1–A6, one-candidate rule, SUPERSEDED, BUY_REJECTED_LEVELS, correct t2 mark, PASSED-only runs, enforced Replay SQL boundary, required Java 21/disposable-MySQL tests; restore only logback-spring.xml to configurable diagnostics.jdbc.log-directory behavior; update FIX-156/FIX-157 documents with scoped Production-change exceptions.\
> waive: ALL pending

Status: IMPLEMENTATION AUTHORIZED UNDER JEAN OVERRIDE. This override does not
authorize Git push, remote merge, deployment, Production database writes or
migration application, live service configuration changes, live orders, or
deletion.

---

### JEAN-OVERRIDE-20261116-003 - Suspicious blocked-BUY review evidence

Date/time KSA: 2026-11-16 01:58:00 +03:00

Scope: Add persistent suspicious-review marking for blocked BUY signals from
Trade Inspector, including mark/unmark actions, future-analysis query support,
and separate technical score, raw confidence, effective confidence, and blocker
display.

Waived: ALL pending items for this scope.

Jean's exact words:

> proceed direct from jean\
> scope: Add persistent suspicious-review marking for blocked BUY signals from Trade Inspector, including mark/unmark actions, future-analysis query support, and separate technical score, raw confidence, effective confidence, and blocker display\
> waive: ALL pending

Status: IMPLEMENTED UNDER JEAN OVERRIDE. This override does not
authorize Git push, remote merge, deployment, Production server configuration
or live-service changes, Production database writes or migration application,
live Binance account or order actions, or deletion.

---

### JEAN-OVERRIDE-20261108-001 — FIX-156 Replay-only parity work

Date/time KSA: 2026-11-08

Scope: FIX-156 Replay-only parity work.

Waived: ALL pending items for this scope, including pending reviews, findings,
approval gates, and the active coordinator review.

Jean's exact words:

> proceed direct from jean
> scope: FIX-156 Replay-only parity work
> waive: ALL pending

Status: IMPLEMENTED UNDER JEAN OVERRIDE. This override does not
authorize Git push, remote merge, deployment, Production server configuration
or live-service changes, Production database writes or migration application,
live Binance account or order actions, or deletion.

---

### JEAN-OVERRIDE-20261117-001 — Replay-only candidate-recovery decision ledger

Date/time KSA: 2026-11-17 00:00:00 +03:00

Scope: Add a Replay-only candidate-recovery decision ledger for a selected
`[t1,t2]` period. It records chronological `BUY_ALLOWED`, `SELL_ALLOWED`,
`BUY_REJECTED`, and `EXPIRED` decisions, virtual prices, realized/unrealized
P&L, fees/slippage, cumulative scenario results, and baseline Replay comparison.
It must not create paper positions, wallet trades, production trades, or alter
production behavior. Ledger events must render on the existing Replay graph as
distinct scenario BUY/SELL markers.

Waived: ALL pending items for this scope.

Jean's exact words:

> proceed direct from jean\
> scope: Add a Replay-only candidate-recovery decision ledger for a selected [t1,t2] period. It must record chronological BUY_ALLOWED, SELL_ALLOWED, BUY_REJECTED, and EXPIRED decisions; virtual entry/exit prices; realized and unrealized P&L; fees/slippage; cumulative scenario results; and comparison with baseline Replay. It must not create paper positions, wallet trades, production trades, or alter production behavior.\
> waive: ALL pending

Status: IMPLEMENTED UNDER JEAN OVERRIDE. This override does not
authorize Git push, remote merge, deployment, Production server configuration
or live-service changes, Production database writes or migration application,
live Binance account or order actions, or deletion.

---

### JEAN-OVERRIDE-20261116-004 — Trade Inspector indicator evidence breakdown

Date/time KSA: 2026-11-16 02:10:00 +03:00

Scope: Add an expandable Trade Inspector technical-indicator breakdown showing
trend, volume, momentum, component indicators, risk context,
higher-timeframe context, liquidity, and strategy explanation.

Waived: ALL pending items for this scope.

Jean's exact words:

> proceed direct from jean\
> scope: Add an expandable Trade Inspector technical-indicator breakdown showing trend, volume, momentum, component indicators, risk context, higher-timeframe context, liquidity, and strategy explanation\
> waive: ALL pending

Status: IMPLEMENTATION AUTHORIZED UNDER JEAN OVERRIDE. This override does not
authorize Git push, remote merge, deployment, Production server configuration
or live-service changes, Production database writes or migration application,
live Binance account or order actions, or deletion.

---

### JEAN-OVERRIDE-20261116-001 â€” Trade Inspector copy-investigation summary

Date/time KSA: 2026-11-16 00:38:35 +03:00

Scope: Add a Trade Inspector copy-investigation-summary button with trade ID,
symbol, KSA open/close time, status, P&L USDT, and P&L percent.

Waived: ALL pending items for this scope.

Jean's exact words:

> proceed direct from jean\
> scope: Add a Trade Inspector copy-investigation-summary button with trade ID, symbol, KSA open/close time, status, P&L USDT, and P&L percent\
> waive: ALL pending

Status: IMPLEMENTATION AUTHORIZED UNDER JEAN OVERRIDE. This override does not
authorize Git push, remote merge, deployment, Production server configuration
or live-service changes, Production database writes or migration application,
live Binance account or order actions, or deletion.

---

### JEAN-OVERRIDE-20261116-002 â€” Trade Inspector blocked-BUY chart evidence

Date/time KSA: 2026-11-16 01:26:44 +03:00

Scope: Add Trade Inspector blocked-BUY chart markers with a right-click View
Info panel showing the persisted blocker reason and evidence.

Waived: ALL pending items for this scope.

Jean's exact words:

> proceed direct from jean\
> scope: Add Trade Inspector blocked-BUY chart markers with a right-click View Info panel showing the persisted blocker reason and evidence\
> waive: ALL pending

Status: IMPLEMENTATION AUTHORIZED UNDER JEAN OVERRIDE. This override does not
authorize Git push, remote merge, deployment, Production server configuration
or live-service changes, Production database writes or migration application,
live Binance account or order actions, or deletion.

---

### JEAN-OVERRIDE-20261115-001 — Jenkins Mockito-stub repair

Date/time KSA: 2026-11-15 00:32:34 +03:00

Scope: Fix the Jenkins-failing Mockito stub in `ExecutionIntelligenceServiceTest`
and run the affected test suite.

Waived: ALL pending items for this scope.

Jean's exact words:

> proceed direct from jean\
> scope: Fix Jenkins failing Mockito stub in ExecutionIntelligenceServiceTest and run the affected test suite\
> waive: ALL pending

Status: IMPLEMENTATION AUTHORIZED UNDER JEAN OVERRIDE. This override does not
authorize Git push, remote merge, deployment, Production server configuration
or live-service changes, Production database writes or migration application,
live Binance account or order actions, or deletion.

# FIX-125 / FIX-132 — approved review corrections, 2026-09-27

Review candidate only. LIVE remains disabled; `crypto_ai.candle` is unchanged. This revision is based on the delivered integrated candidate, not an older FIX-132-only tree. The user explicitly approved replacing the Java monitor with the transaction-owned database mutation guard on 2026-09-27. No monitor is restored.

## Wallet coordination contract

| Stage | Scope and order |
| --- | --- |
| Shared delivery fence | Price phases may hold the local consumer symbol fence. Analysis claim commits before business work starts. |
| Wallet symbol | Durable row exists even when no position exists. Acquire before position lookup. No new symbol may be acquired while holding mutation. |
| Processing work | Signal route locks its owned work row before managed-position access. |
| Managed position | Automatic routes lock the current OPEN position before mutation. Manual asset/cash/settings operations do not pretend to own a managed position. |
| Wallet mutation | Single global InnoDB row, held through the actual outer commit/rollback. Manual trade/asset writers join after their symbol; cash/settings-only writers start here. |
| Serialized shared resources | Statistics, settings, cash/coin assets and ledger/snapshot writes require mutation ownership. Automatic SELL retains statistics-before-cash, matching BUY. |

The global mutation guard, not Java `synchronized`, now serializes the shared-resource region across symbols and processes using this protocol. The previous monitor/DB inversion allegation remains withdrawn. Retaining statistics-before-cash is useful discipline but is not a substitute for this guard, nor proof that narrowing it later is safe. Do not run old binaries or external wallet writers that bypass the protocol against the same wallet.

`WalletMutationRepositoryGuard` checks shared-wallet repository writes and locking reads, including inherited saves, before delegating. `WalletTransactionCoordination` records ownership in transaction synchronizations; Spring suspends these during REQUIRES_NEW and removes them after completion. A naked ThreadLocal would authorize the wrong transaction. Tests exercise the actual repository decorator, absent ownership, nested transactions, and mutation-before-new-symbol rejection. A source inventory test rejects raw SQL writes to these wallet tables. This is not a database privilege boundary against arbitrary external SQL or a guarantee about future direct EntityManager dirty writes: new persistence paths require review.

### Throughput is a cost, not a free correctness improvement

One slow wallet mutation blocks other symbols until commit, including time spent in `captureSnapshot()` and its price reads. Pure indicator/analysis work does not take this guard. No throughput optimization, asynchronous snapshot or broadened retry is included.

Timers `wallet.coordination.wait` and `wallet.coordination.hold` have bounded stage/outcome labels. Hold is recorded after completion, including commit/rollback; wait measures successful acquisition separately. Failed acquisitions are not represented in the successful-acquisition timer. Slow/failed transaction logs include `[FIX-125][TX_COMPLETED]`, `waitMs`, `holdMs`, `elapsedMs`; waits >=100ms have `[LOCK_WAIT]`. A real-MySQL latch-controlled snapshot test observes an unrelated symbol waiting specifically on `wallet_mutation_coordination` via `performance_schema`. Its numerical results are in the raw test evidence and are NOT a production capacity estimate. Production p95/p99/max wait and hold, timeout rate, snapshot cost and trade throughput still require representative staging/soak acceptance before sign-off.

## Operator approval is a separate maintenance application

The same JAR accepts the exact startup argument `--fix132-approval-console=true`. It boots an isolated configuration outside Trader's component scan, without JPA repositories, schedulers, wallet services, WebSocket, discovery or analysis workers. It requires OBSERVE and cannot start in LIVE. The ordinary Trader application does not expose these approval endpoints.

Stop and drain ALL Trader instances before launching it. This is an operational prerequisite: the console does not remotely terminate, fence or certify absence of an older external Trader process. Tests prove console isolation, not that an operator stopped every server. Do not point a second normal Trader at the wallet during approval.

The console provides:

- `GET /api/fix132/cutover/csrf`: authenticated, role-authorized session CSRF token.
- `POST /api/fix132/cutover/preview`: `{ "symbol": "BTCUSDT", "operation": "FIRST_CUTOVER" }` or explicit `REAPPROVAL`.
- `POST /api/fix132/cutover/approve`: `{ "proposalId": "returned UUID", "reference": "review ticket / evidence reference" }`.

Authentication alone is insufficient: both HTTP security and service checks require `ROLE_CUTOVER_APPROVER`. The existing `app_user` role must be deliberately provisioned by an authorized administrator; no migration grants it automatically. Approver identity comes from Authentication, never request JSON. CSRF stays enabled for all mutating requests. An authenticated USER with a valid token still receives 403. Unrelated console routes are denied.

V94 adds durable, five-minute proposals. FIRST_CUTOVER captures committed collector sequence and explicit UTC time in one read on the UTC source connection. The response is the exact reviewable boundary. Source advancement after preview does not change it; regression rejects it. Approval checks the same principal, unexpired/unconsumed proposal, unchanged local state fingerprint, allowed state, correct first/re-approval operation, no unfinished signal or delivery work, and current source sequence. Local state is locked and revalidated; audit insertion, state update and proposal consumption commit/roll back together. There is no XA transaction with the collector.

FIRST_CUTOVER initializes checkpoint to the approved sequence and clears inherited last-price authority. REAPPROVAL preserves the original cutover, discovered checkpoint, last price and observation authority; it only refreshes approval metadata/audit. Existing history or prior approvals prevent using FIRST_CUTOVER as a reset. Moving an already-live boundary is not implemented by this console. Quarantined/unfinished work is rejected, never automatically executed or marked complete. Successful approval does not switch LIVE on. Ordinary LIVE restart validates existing approval and does not require creating a new proposal.

Logs: `[FIX-132][CUTOVER_PREVIEW]`, `[CUTOVER_APPROVED]`, `[CUTOVER_REJECTED]`. Approval evidence is persisted and remains visible through existing diagnostics. Never use direct SQL as the normal approval workflow; privileged database access remains inherently able to bypass application controls.

## Historical/live concurrency across instances

Candidate discovery is only a hint. Under the local symbol row lock, claim now performs current locking reads of the candidate, due time, and pending/running/review lane state. A RUNNING or REVIEW_REQUIRED owner blocks the lane regardless of whether its sequence is above or below a newly due historical row. The existing narrow same-candle live bypass of HISTORICAL_DEFERRED is retained. Completion and COVERED_BY_LIVE are now one local transaction under the same symbol fence.

The JVM sets/striped locks remain optimizations and local legacy coordination, not cross-instance authority. No analysis task is reassigned after uncertainty. Tests create two consumers and two actual CandleClosedAnalysisWorker objects with distinct stripe coordinators. Latches pause after real SignalProcessingStore registration, force historical expiry during the live task, and assert persisted source_event_id, work completion and exactly one controlled business callback. Both arrival orders and historical-only expiry are covered. Scoring/indicator and paper-business collaborators are controlled; these tests do not claim full-strategy or real-wallet parity. The separate wallet integration suite executes real JPA wallet operations.

## Unchanged and outstanding

No scoring, sizing, exit thresholds, Replay clocks, candle lineage, verifyEventResolution or Shadow-write optimization is changed. Existing shared policy/Replay source comparisons and tests remain applicable. Runtime timing/order can change; exact real-window parity is not inferred from unchanged formulas.

Collector FIX-133 classification remains a separate LIVE blocker. Full deployed-schema migration rehearsal, operator workflow rehearsal, retention/history readiness, other-consumer impact, production-sized recovery scan measurements and identical-window OLD/NEW Replay acceptance remain outstanding. No deployment or candle rename is performed by this package.

# FIX-142 v3 — consolidated response to the v2 code review

Date: 2026-10-03. Project: crypto-ai-trader. Baseline under review: fix-142-development-candidate-v2.zip. All corrections stay inside FIX-142; no deployment, data cleanup or database operation was performed.

## Decision

The protection-lockout finding is accepted and independently confirmed against source. V2 must not be activated. Its implementation retained uncertain protection starts without any audited operator path to terminally reconcile them. The outstanding counter stayed occupied, and an earlier unresolved attempt also blocked ordering: one such attempt could block future prices even before two slots filled. The previous README understated this correctness/recoverability gap as a capacity limitation.

V3 adds an explicit evidence-based resolution operation and independent detection. It does not automatically exclude unresolved reviews from capacity or replay them. A case without trustworthy outcome evidence still remains fenced. Recovery depends on a qualified operator establishing that evidence; the code does not certify it from age, PID absence or an error string.

## Review comment disposition

| Review comment | Response and location |
|---|---|
| No way to resolve started protection uncertainty | Added SharedMarketConsumer.resolveProtection and POST /api/fix142/resolve-protection, guarded and audited. |
| Two timeouts permanently consume both slots | Operator-confirmed outcome sets protection_completed_at; the existing counter and earlier-order barrier then stop treating that exact attempt as unresolved. No automatic eviction. |
| Phase-stream marker also remains stuck | Protection resolution intentionally leaves the marker alone. The existing resolve-marker operation can subsequently RESUME/RELEASE after its separate guard/evidence checks; it still rejects truly unresolved protection. |
| Old rows disappear from recent-price polling before timeout fencing | Added independent five-second monitorProtectionAttempts. It examines aged starts regardless of observed_at and fences them without declaring completion. |
| Protection failure could go unnoticed | Added repeated PROTECTION_RECOVERY_REQUIRED and PROTECTION_MONITOR_FAILED log events/counters; health includes exact aged attempts and CRITICAL_OPERATOR_RECOVERY_REQUIRED or UNKNOWN_MONITORING_FAILED. Operational alert integration is required before activation, not claimed implemented. |
| No timeout-and-recovery test | Added four delivery regressions covering aged-start detection, two-slot/new-price/marker recovery, exact identity/evidence, non-replay and audit rollback. Added three controller guard/principal tests; inventory-read failure is caught so monitoring can run again. All remain unexecuted here. |
| Duplicated README/full document | README now points to the canonical src/main/resources/md/FIX-142.md and this consolidated review response. |
| v1 test output might be mistaken for v3 | Renamed it validation/isolated-v1-test-results.txt. Added v3-review-checks.txt; Change Log explicitly identifies historic versus new/unexecuted evidence. |
| Other reviewed areas structurally sound | Their existing mechanisms remain, subject to the original real-build/MySQL/business/load gates. The external review's static findings are not converted into runtime acceptance claims. |

## Exact resolution contract

The endpoint requires ROLE_ADMIN, shared-market.fix142.manual-resolution-enabled=true and X-FIX142-Operator-Review: CONFIRMED. New scheduling must also be enabled for the service operation. The authenticated principal supplies the audit reviewer; the request cannot substitute a different reviewer.

Required request identity: symbol, source event ID, protection_owner and exact protection_started_at in UTC. Under symbol -> delivery locks, the service checks symbol, canonical 1m interval, REVIEW_REQUIRED, exact owner/start, and absence of completion. A changed, completed or incorrectly identified attempt is refused.

Only these outcome pairs are supported:

| Disposition | Required transaction outcome | Stored protection outcome |
|---|---|---|
| CONFIRMED_APPLIED | COMMITTED | APPLIED |
| CONFIRMED_NOT_APPLIED | CONFIRMED_NO_EFFECTS | REJECTED |

Both require independently established owner fencing and detailed effects evidence. A bare ROLLED_BACK string is refused: rollback does not prove all in-process effects were absent. Committed no-wallet-trade cases can still have applied position/price/profit-lock effects; inspect the complete operation, not only wallet_trade existence.

The guarded write changes only protection_completed_at and protection_outcome and requires exactly one row. Its audit insert requires exactly one row in the same transaction. Audit failure rolls back the outcome change. The old owner, start timestamp, last_error, event identity, phase, marker, observer status and analysis review remain. The completion timestamp is the reconciliation-record time, not an invented historical effect-execution time. Original REVIEW_REQUIRED status is retained until any separately authorized phase-stream continuation.

No business service/onPrice invocation occurs during resolution. A repeated request is refused as already completed; an old worker rechecking the completed/reviewed attempt cannot apply it again through this code. Marker continuation rotates its owner separately, preventing reuse of the old marker token.

## Operator runbook

1. Treat PROTECTION_RECOVERY_REQUIRED or critical protection health as a live-position-management incident. Verify actual position risk and follow the team's controlled incident procedure. Do not assume process liveness or a globally green application health response establishes protection freshness.
2. Obtain the exact event, protection owner and UTC start from /api/fix142/health and database/log evidence. Retain incident references.
3. Independently fence the actual old owner across relevant JVMs and resolve its transaction outcome. This application has no automatic cross-JVM fencing proof. Do not substitute elapsed time or a missing PID.
4. Reconcile all relevant position, wallet, daily statistics and in-process effects. Choose one supported outcome only if proven. Unknown cases remain blocked; do not invent CONFIRMED_NO_EFFECTS merely to free capacity.
5. Submit the exact protection resolution with authenticated admin review. Confirm the audit and terminal protection outcome. Do not invoke onPrice for the old observation.
6. If the phase marker is fenced, review its event/owner/phase and use the separate resolve-marker operation. Phase-2 observer uncertainty requires its own disposition. Protection resolution never silently clears other uncertainty.
7. Verify a fresh price observation can be claimed/applied and last_observed_at advances. Verify committed latest signals and context separately; this endpoint alone does not establish signal recovery.
8. Keep production monitoring connected to critical/unknown protection health and recovery log tags. The candidate does not install an external pager or notification service.

Example request structure (replace every placeholder with actual reviewed evidence):

```json
{
  "symbol": "BTCUSDT",
  "eventId": 123,
  "expectedOwner": "EXACT_PROTECTION_OWNER",
  "expectedStartedAt": "EXACT_UTC_START_FROM_EVIDENCE",
  "disposition": "CONFIRMED_NOT_APPLIED",
  "transactionOutcome": "CONFIRMED_NO_EFFECTS",
  "fenceProof": "Independent old-owner fence and transaction-resolution evidence references",
  "effectEvidence": "Independent verification of durable and in-process effects, with incident references"
}
```

This is a review template, not authorization to execute against a live database.

## Tests and honest release status

New tests drive old observation/start timestamps past 30 seconds without sleeping. They prove the intended scenario in authored executable assertions: monitor fences both starts, fresh claim initially blocks, audited resolution terminally records both, a new price applies once, separate marker continuation finishes without repeating protection, original owner/error survive, stale/mismatched/rollback-only requests are refused, and audit rejection retains unresolved state.

The added tests are inherited by the dedicated real-MySQL delivery fixture. Controller tests exercise the flag/header/authenticated-principal boundary directly; full ROLE_ADMIN HTTP/security-chain validation remains separate.

Available checks in this environment: changed Java files parse syntactically, XML and Change Log JavaScript parse, archive integrity and retirement-service byte comparison can be checked. No Java21/Maven or H2/MySQL execution of v3 occurred. The historic 26-test v1 pass and limited v2 diagnostic smoke are clearly labeled and do not validate v3.

Before activation: full Java21 clean verify; MySQL migration/query-plan/locking and timeout/manual-recovery races; actual security-chain authorization; Production/Replay and protective exits; late-arrival/overload ordering; sustained 44,738-row full-application backlog reproduction; database and logging overhead; and operational alert wiring. The monitor's LIMIT bounds rows returned, not examined or total duration; measure its plans and contention. The two-outstanding policy remains explicit and still requires load/ordering acceptance.

This fixes the missing recovery mechanism in code. It does not certify runtime recovery or authorize deployment while these gates remain open.

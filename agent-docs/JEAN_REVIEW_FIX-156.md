# JEAN_REVIEW — FIX-156

Status: ACTIVE

Activated: 2026-10-15 (Asia/Riyadh)

Reviewed scope: FIX-156 Replay / Production Business-Logic Parity.

Reviewed design: `FIX-156-RPR-0.9.12-DRAFT.md`

Reviewed design SHA-256:
`ddfd5f64a95f6b55ed39e8bffa7b4c93bd65f4e6dd3b23d8bf821500de722b24`

Implementation boundary: Replay area only. Production classes, Production
runtime behavior, wallet/account records, Binance behavior, schedulers, and
deployment configuration are out of scope. The sole shared-table exception is
the exact `fix122_evaluation` policy in RPR-0.9.12 Section 2.1.

## Formal findings

### USR-REV-001 — FIX-156 foundation is isolated but not yet wired; policy/spec gaps

Status: **ACCEPTED BY JEAN**

Severity: **High** (blocks further FIX-156 wiring and acceptance)

Jean review comment received 2026-10-15 (Asia/Riyadh):

- The first foundation step is correctly isolated: no existing file changed;
  only Replay additions, V110, and two Replay tests were added. V110 is
  additive and follows V109. The current policy fails closed for
  `wallet_settings`, `wallet_trade`, missing profile hash/time, and zero
  notional.
- The additions are not wired into the existing Replay flow. In particular,
  `ShadowProductionReplayService` still reads `wallet_settings`, calls
  Binance, reads `wallet_trade`, and does not use the policy profile or
  allow-list. Comparator and register rows D13, D26, D28, D29, D47-D56 remain
  open.
- `ReplayAccessPolicy` conflicts with RPR-0.9.12 Section 2.1: it rejects
  `fix122_evaluation`, omits legitimate Replay evidence/source tables, and
  cannot enforce the statement-specific O-8 allowance from a table-name-only
  decision. The corrected design must authorize exact operations and require
  every Replay SQL operation to be checked through it.
- `ReplayPolicyProfile` accepts incomplete profit-lock percentages and is
  missing `requireNewBuyTransition`, `executionProfile`, and a policy
  fingerprint. Its tests do not prove refusal of every required profile value.
- V110/documentation needs per-table owner, retention, indexes, and rollback
  notes, plus an explicit mapping for the run certificate and price-coverage
  evidence required by RPR-0.9.12 Section 6.2.
- `fix-registry.js` is stale: it describes FIX-156 as RPR-0.6, not
  implemented, and an isolated virtual account. It must reflect RPR-0.9.12
  and the no-wallet Replay decision.

Affected scope: FIX-156 Replay-only foundation, operation-level data-boundary
enforcement, frozen policy inputs, V110 evidence ownership, registry accuracy,
and the eventual Replay wiring.

Required response before further wiring: reconcile the approved RPR-0.9.12
change registry with the actual foundation; submit an exact operation policy
for `fix122_evaluation` and the required Replay source/evidence tables; then
return the corrected scope to Jean. No Production file, Production data,
wallet/account data, Binance runtime behavior, scheduler, or deployment
configuration may be changed.

### USR-REV-002 — Required implementation-review acceptance checks

Status: **ACCEPTED BY JEAN**

Severity: **High** (required before Jean can accept the next FIX-156 package)

Jean review comment received 2026-10-15 (Asia/Riyadh):

- Add the missing static reachability test **T156-09a** to the required test
  list, alongside T156-09 and T156-09b.
- The next package will be diffed against source baseline `930ab3d9`. Expected
  changes are Replay files, V110 or a later additive migration, tests, and the
  necessary documentation update in `fix-registry.js`; Production files must
  remain unchanged.
- Tests must prove the exact Section 2.1 `fix122_evaluation` operations:
  active-run insert/read, the scoped purge, and the display-only Production-row
  read. Every other statement must fail.
- The allow-list must cover market-evidence tables, existing Replay-owned
  tables, and new `replay_*` tables; every Replay SQL operation must use the
  guard.
- Replay must refuse to run without a complete frozen profile. The profile
  must require all frozen values, including BUY-mirror fields and policy
  fingerprint; no wallet-settings fallback is allowed.
- `ShadowProductionReplayService` must cease calls to `wallet_settings`,
  `BinanceMinimumExecutionPolicy`, shared `validateBuy`, and wallet-trade
  chart reads, using Replay-local mirrors instead.
- The review request must attach passing Java 21 output for T156-09,
  T156-09a, and T156-09b.

Affected scope: FIX-156 Replay-only implementation and verification.

Required response: update the approved scope/change registry if needed,
implement only within the Jean-approved Replay boundary, and return the
complete package with the stated test evidence. Jean alone may close or accept
this finding.

## Gate status at activation

Implementation has not started. The consolidated preparation document records
unresolved frozen-specification/P01 baseline gates. These are status facts,
not Jean findings, until Jean comments on them under this review.

## Local implementation authorization

Authorized by Jean: 2026-10-15 (Asia/Riyadh), command intent
`proceed development from jean`, interpreted as `proceed merging from jean`.

Authorization is limited to the reviewed FIX-156 RPR-0.9.12 scope and the
Replay-only file boundary recorded above. It authorizes local working-tree
implementation only. It does not authorize Git operations, deployment,
Production configuration/data changes, or a database migration being applied.

## Corrective implementation authorization

Authorized by Jean: 2026-10-16 (Asia/Riyadh), explicit option-2 decision.

Jean authorizes Replay-only corrective implementation of the scope in
`FIX-156_IMPLEMENTATION_STATUS_REPORT.md` to address `USR-REV-001` and
`USR-REV-002`. This is a limited exception to the ordinary no-open-finding
precondition: the two findings remain **OPEN** until Jean reviews the complete
corrective package. It authorizes no Production source/runtime behavior,
wallet/account data, Binance runtime behavior, scheduler, deployment
configuration, Git operation, or applied-database migration. All actual code
and tests must remain inside the approved Replay/regression boundary, plus the
additive V110 migration and the required FIX-156 registry/documentation work.

## Finding lifecycle

Every subsequent Jean comment on FIX-156 is recorded verbatim as
`USR-REV-NNN` and starts `OPEN`. Codex may change a finding only to
`ADDRESSED - AWAITING JEAN`; only Jean may set a final status.

## Jean closure record

On 2026-10-17 (Asia/Riyadh), Jean explicitly closed `USR-REV-001` and
`USR-REV-002` and authorized continued FIX-156 Replay-only development. This
does not authorize Production source/runtime changes, wallet or account data
changes, live Binance access, scheduler/deployment changes, Git actions, or
applying a database migration.

## Jean direct authority override — 2026-11-08 (KSA)

Status: **ALL PENDING ITEMS WAIVED BY JEAN** for FIX-156 Replay-only parity
work. The active coordinator review was stopped for this scope. Implementation
continues locally under the override; it is not a review approval.

Jean's exact words:

> proceed direct from jean
> scope: FIX-156 Replay-only parity work
> waive: ALL pending

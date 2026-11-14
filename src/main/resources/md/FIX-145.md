FIX-145: audited abandonment of the exact 31 approved legacy work attempts

No database operation has been executed here. Java/MySQL tests not run here.
1. Merge src into your project; keep your pom/config. Build: mvn -B -ntp clean verify.
2. Stop all trader/execution writers, including any second instance and automatic restart.
3. Run the updated application once so Flyway applies V101. The unchanged unresolved
   records should still block LIVE startup. Stop that process before running recovery.
4. Open scripts/FIX-145-cancel-approved-31.sql in MySQL Workbench on local JohnnyM:3307,
   crypto_ai. Set cleanup_writers_stopped=1 only when all execution writers are stopped.
   Run the whole script with stop-on-error (mysql CLI: do NOT use --force).
5. Expect exactly 31 cancellations and 31 original audit snapshots. Any assertion error
   rolls back both audit and status changes. If already applied, a repeat aborts.
6. Restart revised trader. Other cutover/ownership/review guards remain enforced;
   this change does not certify that every startup gate will pass or signals are fresh.

Cancellation is permanent no-replay policy, not completed execution, confirmed rollback,
no-effects certification, or permission to retry. The original errors and timestamps
are archived; live completed_at stays NULL. Signals, trades, positions, delivery ownership,
BTC fences and per-symbol cutover state are unchanged. In particular the failed UPDATE
attempts 8065/8110 retain UNKNOWN past effects; inspect existing positions separately.

The startup exemption requires the original audit and matching live attempt identity,
error, stage, attempts, next-attempt time and cancellation timestamp. A bare
OPERATOR_CANCELLED status, a tampered record, or a source-attributed cancellation
still blocks. Existing claim/due positive allowlists prevent replay of cancelled rows.
No schema reset or old Flyway migration edit. V100 is left unchanged.

Compatibility correction (2026-10-04): isolated CutoverApprovalService now uses
exactly the same audited cancellation predicate as LIVE startup. Two additional
H2 regressions authored, not executed here. This permits reviewed cancellation
through the WORK guard only. First-cutover approval, unfinished delivery guards,
operator identity/proposal review and all source/history checks remain mandatory.
No V101 edit and no additional database migration; do NOT rerun the 31 cancellations.
Per-symbol degraded operation is not implemented by this compatibility correction.

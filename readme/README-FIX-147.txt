FIX-147 — Flyway provisioning for the isolated cutover approver

V102__fix_147_cutover_approver_account.sql creates cutover_approver only if absent,
using the enabled admin/ADMIN account's current stored password. No password is
logged or hard-coded. Login uses cutover_approver and the admin password at migration
time. Later password changes are independent. The admin account is unchanged.
Existing destination accounts, including disabled accounts, are never overwritten.
Absent/disabled/wrong-role admin or blank credential results in no account creation.

The migration grants CUTOVER_APPROVER access to the existing isolated approval console.
It does not create cutover approvals, modify consumer states or unblock trading itself.
Use FIRST_CUTOVER preview and approval with the trader stopped and collector running,
then restart the trader and verify readiness plus candle-close-to-signal latency.
No Production/Replay scoring, entry, exit, sizing or execution behavior change.

Merge this package and run Java 21 mvn -B -ntp clean verify. Flyway applies V102 on
startup when enabled. Check flyway_schema_history and username/role/enabled afterward.
Four H2 regression tests authored. Java syntax parsing and SQLite SQL-fixture checks
passed here, but Maven/H2/MySQL/Flyway execution remains required locally.
V100 and V101 and existing trading migrations are unchanged.

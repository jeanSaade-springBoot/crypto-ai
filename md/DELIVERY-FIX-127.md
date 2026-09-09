# FIX-127 delivery

Full source candidate based on the preceding FIX-124 + FIX-126 package. Contains all earlier source changes, the FIX-123/FIX-125 reservations, V86, shared signal processing coordination, bounded recovery, logs, comments, Proven / Analyze Trades diagnostics, registry update and 22 new JUnit tests.

Use the full source tree OR apply `patches/FIX-127.patch` once to the exact preceding FIX-124 + FIX-126 tree (`git apply -p1`). Do not also reapply the older FIX-124/FIX-126 patches already included for historical review. No JAR is included and no server was deployed from this workspace.

Validation actually run: 15 Node tests passed; 337 Java files parsed with no syntax errors. `mvn test` failed to start because Maven is absent. Java compilation, all JUnit tests, MySQL migration/concurrency and historical parity remain unverified. This package is a candidate, not a verified production build.

On the Java 21 build machine:

```powershell
java -version
mvn test
```

Read `md/FIX-127.md` for behaviour changes, recovery policy, read-only verification queries and remaining gates. In particular, FIX-127 changes failure recovery and duplicate handling; it does not prove that all deadlocks are eliminated and does not complete FIX-125's wallet monitor-through-commit repair.

V86 is additive and does not backfill historical signals. Do not delete durable processing rows to reset errors. Returning to the previous application version disables its use of this ledger; retain it for investigation. A rollback does not undo trades that already committed.

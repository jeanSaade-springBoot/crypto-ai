# FIX-153 validation and release status

Source: latest uploaded trader ZIP, SHA256 `b12f472ade4ab856445ec8493006465456ad1278f3d7488f0433d3e3f325e033`.
Contract: local candle design revision 23. Changes apply to the trader only.

## Executed

- The preceding candidate reported a Java 21.0.3 / Maven 3.9.11 `clean verify` with 558 tests and zero failures/errors/skips; exact output is in `validation/fix153-build.log`. That result predates the revision-23 hardening and must not be presented as validation of this tree.
- Mockito required an explicit ByteBuddy agent in this container. The verification command was `mvn -B -ntp -DargLine=-javaagent:/root/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar clean verify`, with proxy settings for dependency retrieval. This changes test instrumentation, not application behavior. README provides ordinary Windows commands.
- JavaScript syntax check for the Change Log registry.
- Every original migration through V105, including Java V103, compared byte-for-byte against the upload and is unchanged.
- Flyway baseline resolution selects B105 for an empty installation and V106 afterward. This is a resolver test, not proof that MySQL executes the entire baseline successfully.

New and updated tests cover atomic candle/price input, no-position/no-op protection outcomes, rejection rollback, duplicate timestamp ownership, stale and out-of-order acceptance, resolver races, Replay pending exclusion, bounded lane replacement/rejection, detector provenance/lookback and nonblocking locks, repeated bootstrap producing no analysis/trades, OFF runtime without shared tables, single-instance retention during shutdown, and stale first direct execution expiring without wallet calls.

The default suite uses fixtures/mocks and H2 where appropriate. Fault injection deliberately produces ERROR stack traces in a passing build. Read final Surefire results to distinguish those from failures. The optional `fix153-mysql` profile is not part of the 558 executed tests.

## Changes affecting existing verification

The uploaded source lacked the Jenkinsfile and deploy script referenced by two deployment tests. Those assertions now check shipped OFF defaults/guards. External deployment integration is unverified. Shared-mode controller/candle fixtures explicitly select LIVE to retain rollback-path coverage.

An existing stale-token completion shortcut in `SharedMarketConsumer.prepareProtectionApply` is corrected to validate the exact protection owner before acknowledging completion; its existing FIX-152 regression now passes. This does not enable uncertain work replay.

## Open release gates

The C1–C10 hardening changes are source-level changes only until the tests and
environment gates below are rerun. In particular, the two-process Windows
exclusion rehearsal and a real application drain command are not certified by
static PowerShell inspection.

1. Disposable real MySQL 8 execution of `mvn -Pfix153-mysql test`, including V106 upgrade, empty-schema B105 bootstrap, competing closes, resolver/late-callback races and immutable provenance. MySQL DDL may partially commit on failure; inspect actual schema before repair.
2. Full Spring/JPA startup with the actual wallet services and authenticated health/security chain. Source audit found normal protection effects use the same REQUIRED transaction; verify real effect/status atomicity and crash/kill behavior on MySQL.
3. Windows/Eclipse shutdown and restart: same absolute instance-lock path, all previous PIDs exited, no second trader or collector writing the same candle schema. Multi-host operation is unsupported.
4. Reconnect/backfill soak and backlog/load acceptance with 21 symbols and all configured intervals. Measure pool acquisition, transaction/lock holds, protection delay, per-lane missing coverage, queue replacement/rejection and logging overhead. Timeout/budget values do not establish a strict end-to-end deadline through connection acquisition.
5. Production/Replay historical window comparison using identical accepted APPLIED/LEGACY observations and candle identities. Formula preservation and fixture tests do not prove exact real-window parity. Pending inputs abort Replay, and excluded outcomes must be counted.

Existing uncertain shared records remain unresolved evidence; local work bypasses their runtime admission dependency without asserting that past effects never occurred. Runtime logs distinguish transaction outcomes from callback returns. Health reports observed local stages, not an unconditional production-ready verdict.

No user database was modified and no application was deployed by this implementation task. This is a tested source candidate with the above real-environment gates open.

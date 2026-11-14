# FIX-153 — trader-owned local candles (revision 23 hardening candidate)

This source candidate makes `crypto-ai-trader` ingest Binance directly and use its own `crypto_ai.candle`. It keeps live intra-candle position protection. Default `shared-market.mode=OFF`, both FIX-142 scheduling flags false, scheduled historical analysis false. Collector and next projects are untouched; their source is not part of this ZIP.

Existing shared delivery/state/health records remain as evidence. OFF startup, scheduling, diagnostics and direct Replay do not depend on collector event/cursor tables. No migration deletes or certifies uncertain old effects. New direct work is independent of old shared review markers. Collector-attributed processing work is excluded from local recovery.

## Before starting locally

1. Back up `crypto_ai` and preserve the existing configuration/environment. Stop every trader instance and wait for it to exit. Stop the collector only if it writes into `crypto_ai`; it may continue in its separate `crypto_ai_v2` database. Do not overlap two writers into the same candle schema.
2. Merge this source and verify Java 21 with `java -version` and `mvn -version`. Ensure `DB_URL` targets **crypto_ai**, not crypto_ai_v2. Remove inherited LIVE environment/IDE overrides, or explicitly set the three OFF flags below. Use the same absolute `LOCAL_TRADER_INSTANCE_LOCK` file for every launch on this host. Multi-host traders are unsupported. The instance lock remains held through Spring shutdown until JVM exit; verify the original PID has exited before the next launch.
3. Build and validate on disposable MySQL before production. The real MySQL, Windows restart/shutdown, load, reconnect, crash and historical parity gates are open. Unit tests alone do not close them.
4. Start one trader. Existing schema upgrades apply **V106**; existing V1–V105 SQL and Java V103 are unchanged. The new **B105** baseline is only for empty installations; it builds the local schema without executing the collector-index Java V103. A real MySQL test exercises that bootstrap; it has not been run here. Startup checks the configured local schema, InnoDB tables, explicit UTC session and successful V106 before socket admission; failed checks stop startup.
V106 includes MySQL DDL and an index build; DDL can commit partially on failure. Inspect actual schema/history before any repair, and never manually mark a failed migration successful.

5. Verify V106 success and inspect `/api/fix142/health` with the existing authenticated admin session. OFF shows local observations; it does not claim READY solely from configuration. Existing shared administration routes are disabled in OFF.

Git Bash build:

```bash
set -o pipefail
mvn -B -ntp clean verify 2>&1 | tee fix153-build.log
```

PowerShell, from the project directory:

```powershell
$env:SHARED_MARKET_MODE = 'OFF'
$env:SHARED_MARKET_FIX142_ENABLED = 'false'
$env:SHARED_MARKET_FIX142_ACK_ENABLED = 'false'
$env:LOCAL_TRADER_INSTANCE_LOCK = 'C:\workspace\crypto-ai-trader-mysql\logs\crypto-ai-trader.instance.lock'
mvn -B -ntp clean verify 2>&1 | Tee-Object -FilePath fix153-build.log
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
```

Stop Eclipse/app execution before `clean` on Windows. Eclipse run configurations and service environment can override YAML. This package does not change your external Jenkinsfile or deployment scripts: neither was included in the uploaded source.

## Runtime contract

- Input transaction atomically persists candle and a valid direct 1m PENDING price receipt. Runtime provenance is assigned on first insert and never overwrites BACKFILL/legacy provenance. Authoritative REST backfill may finalize an existing open candle, but never changes an already-closed candle, writes no close token and never dispatches analysis. Startup and newly enabled symbol bootstrap import data without generating signals or STARTUP trades. Closed OHLCV is immutable; late opens cannot reopen it. A unique receipt token identifies the winning close transition; affected-row counts are not used as close evidence.
- Protection runs in a separate READ_COMMITTED transaction. Existing wallet symbol/position consistency locks are acquired before price age/order validation. The accepted callback, including no-position/no-op returns, and exact APPLIED update commit atomically. Stale, future, invalid, duplicate or out-of-order observations are excluded. First valid `(symbol, observed_at)` receipt owns slot 1 permanently; duplicates retain evidence with a NULL slot.
- A failed or unconfirmed callback is never automatically replayed. The bounded resolver conditionally retires old FIX-153 PENDING receipts to PROTECTION_NOT_APPLIED after 120 seconds; it cannot overwrite APPLIED, and a later callback that loses the exact status update rolls back its effects. Legacy uncertain sources are untouched. This inference is valid only under the atomic effects/status contract and single-trader prerequisite.
- Close analysis uses one running task per symbol/timeframe, latest pending for 1m, FIFO with four pending for higher intervals. Executor saturation rejects; it never executes analysis on the ingestion thread. All failed/replaced/shutdown reservations are released. A rotating bounded RUNTIME-only detector repairs never-started recent closes; any existing signal/work identity prevents re-admission. Older detector candidates are historical-only and cannot trade.
- First automatic direct execution checks freshness after locks. Stale work becomes EXPIRED with no wallet body. A saved signal and execution work remain separately audited; old REVIEW_REQUIRED work is not retried because a new candle arrives.
- Signals persist LIVE/HISTORICAL_ONLY mode. Old NULL mode is preserved. Replay accepts only LEGACY/APPLIED prices, aborts on PENDING throughout its consumed window, reports excluded outcome counts and avoids shared cutover queries in OFF. Trading formulas remain unchanged; timing/input acceptance and operational trade outcomes can change. Historical Production/Replay parity has not been executed here.

## Accurate logging

Normal per-tick details are DEBUG. `[FIX-153][RUNTIME_SUMMARY]` at INFO every 60 seconds contains finite event counters **since process start**, latest accepted observation time, completion-observed time, and analysis attempts. It is not a durable ledger or a one-minute delta.

`PRICE_OUTCOME_COMMITTED` is emitted from transaction completion with event ID, symbol, UTC/KSA timestamp, outcome/reason. `PRICE_OUTCOME_NOT_COMMITTED` retains uncertainty. `PENDING_RESOLVED` identifies automatic atomic-no-commit evidence and explicitly says no replay. `KLINE_PHASES_RETURNED` means phase callbacks returned; it does not certify a wallet trade. `ANALYSIS_TASK_RETURNED` records worker outcome and elapsed callback time including waits/SQL/mapping; it is not server SQL execution time. Queue replacement/rejection includes candle/lane identity at DEBUG; counters remain visible at INFO.

Enable short diagnostic capture through Eclipse VM/program configuration or environment:

```powershell
$env:LOGGING_LEVEL_COM_CRYPTO_LOCAL = 'DEBUG'
$env:LOGGING_LEVEL_COM_CRYPTO_MARKET_SERVICE_MARKETPRICEEVENTSERVICE = 'DEBUG'
$env:LOGGING_LEVEL_COM_CRYPTO_INDICATOR_EVENT_CANDLEANALYSISDISPATCHER = 'DEBUG'
$env:LOGGING_LEVEL_COM_CRYPTO_SERVICE_BINANCEKLINESERVICE = 'DEBUG'
```

Existing logs default to `logs/crypto-ai-trader.log` relative to the Eclipse working directory; confirm `diagnostics.jdbc.log-directory` in your configuration. The file appender is bounded and asynchronous; saturation can drop file messages, so durable price/work evidence is authoritative. DEBUG volume depends on feed traffic: use a short capture and return to INFO. UTC is the storage/decision clock; KSA labels explicitly use +03:00. Do not compare KSA-formatted table timestamps to UTC clocks.

Read-only monitoring SQL: `scripts/fix153-monitor.sql`. Generation delay describes completed LIVE signals only; missing lane coverage must be checked separately. Negative historical -0.001 values are as-of clocks and excluded.

## MySQL test gate

Use a dedicated disposable **loopback** MySQL server and a precreated `fix153_test_admin` schema. Set `FIX153_TEST_MYSQL_URL`, `FIX153_TEST_MYSQL_USER`, `FIX153_TEST_MYSQL_PASSWORD`, `FIX153_TEST_MYSQL_SERVER_UUID` and `FIX153_TEST_MYSQL_DISPOSABLE=YES`. Never use the application server UUID merely to satisfy this test: the instance must be disposable. Run:

```bash
mvn -B -ntp -Pfix153-mysql test
```

Missing/mismatched configuration fails rather than silently skipping. Tests create/drop only generated `fix153_test_*` schemas after UUID verification. Tests cover inherited atomicity cases, competing close transitions, resolver/effects concurrency, immutable backfill provenance and collector-free Flyway bootstrap. Full real JPA wallet effects/crash kill tests and soak/load acceptance remain additional gates.

## Rollback

Stop the direct trader and wait for exit first. Reconcile positions/work, collector source configuration/indexes and fresh shared cutover boundaries before explicitly enabling LIVE and both FIX-142 flags. Do not simply flip a running process to LIVE. Retain V106 columns/evidence and the single-writer rule. Fresh B105 installations intentionally have no V103 collector-index effects; validate/install the required collector indexes separately before shared rollback. No automatic launch, rollback, review reset or DB cleanup is included.

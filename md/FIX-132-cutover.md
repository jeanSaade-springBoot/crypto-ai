# FIX-132 controlled cutover runbook

Candidate only. No commands here have been run against your server. Keep LIVE disabled until the checks below pass. Do not reset an existing LIVE checkpoint as a restart procedure.

## 1. Schema and collector preparation

Back up both schemas. Verify MySQL versions, permissions, available space and migration duration on a representative copy. Trader Flyway V89–V94 run even when shared mode is OFF; V89's ALTER of the potentially large market_price_event table needs a maintenance/online-DDL assessment. Do not modify old Flyway files.

Apply collector `md/sql/FIX-132-source-tables.sql` to **crypto_ai_v2** using its schema owner's migration process. It creates additive stream tables, including the observation lane. Collector does not take over another application's migration history. Existing market_data_candle_event claim fields remain untouched.

Deploy collector with MARKET_DATA_STREAM_ENABLED=false first and verify ownership/reconnect behavior. Arrange exactly one active candle writer before enabling its feed. Every writer must use the enabled publication protocol; it cannot fence old binaries or feed-disabled writers. Then enable MARKET_DATA_STREAM_ENABLED=true and measure source transaction time, event volume, disk growth and existing-consumer latency. Validate timeout/late-handshake/drain cases and historical repair behavior on MySQL. No feed-retention deletion is provided: agree storage/retention that protects every consumer and the required Replay evidence before activation.

## 2. Trader observation

Give a dedicated user SELECT only on crypto_ai_v2.candle, market_data_stream_event and market_data_stream_cursor. Do not give it source claim/update privileges. JDBC readOnly is not a substitute for grants. Keep Trader's normal local datasource unchanged.

Set SHARED_MARKET_MODE=OBSERVE, SHARED_MARKET_JDBC_URL to the source schema with UTC session semantics, and SHARED_MARKET_USERNAME / SHARED_MARKET_PASSWORD through your secret configuration. OBSERVE keeps the existing candle readers and Trader ingestion; feed events have no business effects. Inspect persisted diagnostics and verify source sequences and classifications. This observation period is not a full business-effect load test.

Use a production-sized MySQL staging copy to validate ordered delivery, fence/rollback behavior, crash reconciliation, protection-before-analysis, OLD/NEW Replay windows and UI coverage. FIX-125 is implemented in this integrated candidate; validate its wallet-wide mutation guard, transaction boundaries and manual-writer coverage on the deployed configuration. Do not infer real-window parity or production capacity from isolated tests. Test existing collector consumers and source writer-version rules too.

Measure actual configured symbol/interval update rates, burst rates, publication overhead, backlog growth, p95/p99/worst protection and close-analysis delay, and freshness exclusions. Initial 2 price workers, 3 read connections and 100-row discovery batches are not throughput guarantees. Accept limits based on those measurements, not an assumed events/sec calculation. Confirm required history per enabled symbol/interval, including 1d if configured. Startup's 300-row check is a minimum, not proof of full Replay history.

## 3. First cutover only

Stop Trader cleanly and wait for old transactions and workers to finish. Reconcile old-path wallet outcomes independently. Never equate its identifiers with collector feed identifiers. Preserve observations/audit evidence before editing state.

Use the isolated approval workflow below for the following boundary operation; this paragraph describes its contract, not a manual SQL alternative. For each enabled symbol, capture the collector's committed `market_data_stream_cursor.last_sequence` and an explicit UTC cutover time. Use a consistent source read and record both values together in the operator's cutover evidence. Persist these as cutover_sequence, cutover_at and discovered_sequence in crypto_ai.shared_market_consumer_state with no inherited last-price/observation authority. In the same local approval transaction, record cutover_source=OPERATOR_APPROVED, approved_by, approved_at, approval_reference, approved_sequence and approved_cutover_at, plus the exactly matching shared_market_cutover_approval audit row; only then set status READY. A READY string alone is rejected. This is an initial cutover operation, not an unconditional restart UPDATE.

If OBSERVE already populated state, inspect all delivery rows first. Proceed only after confirming none ran LIVE effects or has started protection/analysis. Preserve them, classify their outcomes as pre-cutover historical/observed and ensure no pending work at or below the selected boundary remains dispatchable. Do not blindly delete/requeue/reset a previously LIVE consumer. Previously LIVE/uncertain work requires event-by-event reconciliation and a reviewed maintenance script tailored to actual rows. The package intentionally does not provide an unconditional destructive reset command.

Events at or below the sequence boundary are not eligible for live execution. Events above it must also pass original observation time, source classification, observation order and freshness checks. A repair published later remains historical.

Before the optional old-table rename, inspect live views, triggers, foreign keys, scripts and external consumers referencing crypto_ai.candle; inspect whether candle_bck already exists. Confirm the complete read inventory and migration/rollback rehearsal. With Trader stopped and only after these checks, the requested operation is:

```sql
RENAME TABLE crypto_ai.candle TO crypto_ai.candle_bck;
```

It has NOT been executed. Do not overwrite an existing backup table. Shared mode removes Candle from JPA entity validation and disables local candle writers, but source inspection cannot certify external database dependencies.

Set SHARED_MARKET_MODE=LIVE and SHARED_MARKET_ACTIVATION_APPROVED=true only after accepting the gates above. Start Trader. Startup validates operator approval/audit/boundary consistency, unfinished-work ownership, source sequence and minimum history. Confirm FIX-132 source configuration, no Trader kline ingestion/reload, applied prices, ordered closes, source provenance and persisted outcomes in Proven. Preserve the existing temporary scheduled-analysis recovery setting during the agreed diagnostic window; re-enable it through the separately agreed operational procedure, not silently as part of this migration.

## 4. Failure/recovery and rollback

Do not automatically retry REVIEW_REQUIRED protection or started analysis. Reconcile source event, local phase, actual wallet outcome and any surviving worker first. Stop the old worker before any approved manual reassignment. Observer failures are recorded separately and do not stop later price protection. Sequence gaps require source/retention investigation; never skip the cursor merely to clear an alert.

For rollback, stop/drain Trader, preserve all new evidence/state, and verify no uncertain effects. Restore the old candle name only if both table identities and dependencies are correct. The backup candle table becomes stale while LIVE runs: repair and validate its history before allowing old-path trading. Then change mode to OFF. Do not drop the new tables or price columns just to roll back code. A rollback to an older binary needs its own migration compatibility review.

No wall-clock latency guarantee, exactly-once external wallet guarantee, or complete historical snapshot guarantee is made by this delivery.

## Review-delivery recovery clarification

In LIVE, re-enabling scheduled-analysis-enabled does not authorize old recovery to execute collector-era work. Shared delivery remains the only automatic route; post-cutover gaps and uncertain/blocked outcomes require explicit reconciliation. OFF retains legacy recovery. This avoids masking a failed shared outcome by silently executing its signal elsewhere.

The supplied MySQL tests validate the delivery tables/transactions on isolated MySQL 8.0.44 with mocked business services. They do not replace a full Flyway/JPA startup rehearsal on a production-sized copy, FIX-125 deployment acceptance, historical parity or latency acceptance.

## V91 upgrade: existing ambiguous approval state

V91 intentionally demotes every old READY row to PENDING_CUTOVER and records UNKNOWN_LEGACY provenance. It cannot distinguish old auto-observation from an operator-prepared boundary. This includes genuinely reviewed old rows: re-approval is required, not inferred. Quarantine states are not cleared. For an already LIVE consumer, preserve its boundary, discovered checkpoint, last-observed authority and delivery records; do not run the first-cutover reset procedure. Reconcile the existing cutover-history row and all unfinished work, then approve that same boundary explicitly with a new audit reference. A new boundary after prior LIVE requires a separate reviewed transition; do not rewrite history to bypass startup.

Before first activation, inspect all unfinished signal_processing_work records. source_event_id IS NULL is unattributed work, regardless of age; reconcile actual outcomes without auto-executing, deleting or marking completed. Non-null ownership is the specific event link, not a natural-key guess; missing/mismatched delivery or incompatible analysis state blocks activation. Interrupted owned delivery follows its own REVIEW_REQUIRED handling. Preserve IDs and transaction evidence in the approval reference.

Use the isolated approval console described below instead of hand-written approval SQL. Do not automatically approve every restart. Existing valid approvals remain sufficient for a normal LIVE restart; re-approval is an explicit maintenance operation preserving its old boundary.

V92 must provision every enabled pair before trading; it also seeds existing positions/assets. Do not deploy binaries that bypass coordination alongside this version against the same wallet. V93 adds deferred-analysis state; do not remove it to make a dashboard backlog disappear. FIX-133 collector classification remains an explicit activation blocker.

Candle rename stays a later, stopped-Trader, verified-dependency operation. Neither Flyway nor startup performs it. Recovery remains temporarily disabled only for the agreed diagnostic window; that is not an indefinite operating recommendation.


## Isolated approval workflow (V94)

This workflow is for a later approved cutover rehearsal/activation, not an instruction to enable LIVE now. First deploy/rehearse migrations while shared mode stays OFF, then collect OBSERVE evidence. Drain deliveries and stop ALL Trader instances cleanly; reconcile unfinished work and external wallet effects. The console will reject unfinished/quarantined records but cannot prove that an older process on another host is stopped.

Start the same JAR in a separate CMD window on the server:

```bat
java -jar "C:\apps\crypto-ai\crypto-ai.jar" --fix132-approval-console=true --shared-market.mode=OBSERVE --server.address=127.0.0.1 --server.port=8086
```

Use the existing local-database and SELECT-only collector credentials/configuration. The explicit argument selects a different application context; no Trader business component scan or schedulers run. Do not expose Basic authentication on an unencrypted remote interface; loopback is the shown binding. Provision a dedicated enabled `app_user` with `role_name=CUTOVER_APPROVER` and a securely hashed password through existing administrator procedures. Ordinary authenticated users cannot approve.

For each enabled symbol:

1. Authenticate to `GET http://127.0.0.1:8086/api/fix132/cutover/csrf`; retain session cookies and the returned token/header name.
2. POST JSON to `/api/fix132/cutover/preview` with that header and session: `{"symbol":"BTCUSDT","operation":"FIRST_CUTOVER"}`. Use `REAPPROVAL` only to approve an existing boundary without changing checkpoint or price authority.
3. Review returned `cutoverSequence`, `cutoverUtc`, symbol, operation and five-minute expiry against reconciliation evidence. No feed identifier correspondence is inferred. Higher sequences must still pass all live source/order/freshness gates.
4. POST `{"proposalId":"<returned UUID>","reference":"<review/evidence reference>"}` to `/api/fix132/cutover/approve` with the same authenticated principal, session and CSRF header. No approver-name field is accepted as authority. Source progress is allowed; local state changes, regression, expiry, prior consumption or unresolved work reject approval.
5. Preserve returned evidence and audit records; stop the console. It never enables LIVE or renames candles. Only after all remaining acceptance gates are approved may the existing runbook's activation step be performed.

If preview or approval fails, reconcile the reported IDs and preview again. Do not clear status, ownership, event history or work records to force success. FIRST_CUTOVER is refused if an earlier approval/cutover history exists. Moving an established live boundary needs a separately reviewed procedure; REAPPROVAL cannot do it.

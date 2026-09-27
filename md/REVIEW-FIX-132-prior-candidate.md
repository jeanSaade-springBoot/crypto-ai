> Historical prior-candidate review; superseded by FIX-125-FIX-132-review.md. Counts/status below describe that earlier package only.

# FIX-132 — Trader review package

Baseline: pom(20260926-180106).zip. This is the complete Trader Maven source project, not a collector update. Start with md/FIX-132.md, md/FIX-132-candle-read-inventory.md and md/FIX-132-cutover.md. The change manifest lists every changed/added file against that baseline.

## Defaults and scope

SHARED_MARKET_MODE defaults to OFF; SHARED_MARKET_ACTIVATION_APPROVED defaults to false. The existing scheduled-analysis-enabled=false setting is preserved. External environment settings can override these defaults. No deployment, live SQL, checkpoint reset or candle rename has been performed.

LIVE routes candle history, dashboard/chart/trade inspection reads and historical Replay reads through the collector SELECT-only datasource. Collector events drive live protection and closed-candle analysis via independent Trader delivery state. Trading formulas remain in the existing services. Freshness/cutover/order gates and recovery coordination can change which work executes, as documented; this is not a claim of unchanged operational outcomes.

FIX-125 remains unresolved. WalletAutoExecutionService and PaperTradingService remain byte-equivalent to baseline after line-ending normalization. This package does not claim to solve wallet monitor/commit coordination. Existing collector consumers' state is untouched, but source read load still requires measurement.

## Review and testing

See md/FIX-132-test-results.txt and md/test-evidence/ for executed results. The optional fix132-mysql Maven profile requires a dedicated loopback MySQL test instance and creates/drops random fix132_test_* schemas; never use production. MySQL tests use actual InnoDB delivery transactions but mock trading services. Full app startup on the deployed schema, representative-window Replay parity, source coverage and latency acceptance remain rollout gates.

Do not rename candle or switch LIVE merely by unpacking this review package. Flyway V89/V90 are additive Trader migrations that execute even in OFF mode; rehearse their DDL on a copy first. Read the cutover runbook before any deployment changes.

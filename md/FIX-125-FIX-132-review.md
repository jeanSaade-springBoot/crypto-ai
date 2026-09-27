# FIX-125 + FIX-132 integrated Trader review

Complete source package based on pom(20260926-180106).zip plus the prior FIX-132 review candidate. No teammate project_main2 code was available to merge; changes here are explicit in the patch/manifest. This package contains Trader only, not the deployed collector.

Start with FIX-125.md, FIX-132.md, FIX-132-cutover.md and FIX-132-test-results.txt. FIX-134 tracks wallet-asset coordination separately; FIX-133 is the planned collector classification correction. The previous root REVIEW-FIX-132.md has moved under md and is superseded by this document.

Implemented: transaction-lifetime wallet coordination/current reads; explicit cutover approval and migration of ambiguous READY rows; event-owned signal-processing registration and routing; historical-close deferral; persisted delivery/approval/work diagnostics; recovery-query measurement; Java comments and searchable FIX logs. The implementation deliberately uses a wallet-wide database mutation guard as described in FIX-125. It is not a promise of parallel per-symbol wallet mutations.

Defaults remain SHARED_MARKET_MODE=OFF and SHARED_MARKET_ACTIVATION_APPROVED=false; scheduled-analysis-enabled=false remains the existing temporary diagnostic setting. External configuration can override defaults. V89–V94 migrate the local schema even with mode OFF. V91 demotes ambiguous old READY rows and requires explicit approval, including previously manually prepared rows. No source table, collector claim field, server setting or candle name has been changed here.

Replay retains shared decision policies, exact candle identity, historical evaluation clocks, OLD behavior, verifyEventResolution and Shadow-write behavior. It does not consume live claims or Production locks. Current candle fingerprints detect changed inputs; they cannot reconstruct overwritten historical versions. Real-data outcome parity, collector classification, full migration/startup rehearsal, other-consumer load, retention and latency acceptance remain LIVE gates. Successful tests alone do not authorize LIVE or a candle rename.

## Revision after team review (2026-09-27)

Start with FIX-125-FIX-132-review-correction.md for the authorized monitor replacement, enforced repository invariant, wallet-wide wait/hold measurement, isolated role-protected approval console, and durable cross-instance analysis claim correction. Read the current test-results file for executed counts and limitations. Previous integrated evidence is historical; the current revision evidence is under test-evidence/revision-20260927/.

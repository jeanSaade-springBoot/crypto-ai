# FIX-105 — Bounded opportunity-anchor authority for chase quality

## Scope
Pre-wallet change only. `ExecutionIntelligenceService.assessEntryQuality()` remains the single shared Production/Replay algorithm. No Wallet classes or execution persistence contracts are changed.

## Problem
FIX-055 intentionally allowed an active opportunity's `anchorEntryPrice` / `bestEntryPrice` to become the chase-quality reference when below the rolling 30-minute low. This fixed the verified ~71-minute PEPE late-entry case. For a multi-hour sustained trend, however, the opportunity origin/lowest-ever price could remain authoritative forever. BICO replay showed expansion increasing monotonically against that stale origin and repeated `CHASE_ENTRY_BLOCKED` outcomes even when later confirmation attempted to reactivate the setup.

## Change
- Added `ANCHOR_MAX_AGE = Duration.ofHours(2)`.
- Opportunity age is `Duration.between(opportunity.startedAt, signal.generatedAt)`; no wall-clock time.
- Age <= 120 minutes: FIX-055 anchor/best behavior is preserved.
- Age > 120 minutes: anchor/best cannot undercut the rolling `EVIDENCE_WINDOW` reference.
- Negative/malformed age does not receive anchor authority.
- Added DEBUG diagnostics: reference source, chosen price, opportunity age minutes and freshness. No schema expansion.

## Safety
This does not authorize a trade. Existing rolling-window chase thresholds, ATR extension, reward/risk, opportunity-age penalty, rejection-zone logic, volatility penalties, `validateBuy()` and downstream context gates remain unchanged.

## Replay parity
`currentOpportunity()` already delegates to `ExecutionReplayScope.currentOpportunity()` while replay is active. FIX-105 deliberately stays inside the shared `assessEntryQuality()` implementation, so Replay and Production cannot diverge into separate chase algorithms. The parity test opens `ExecutionReplayScope`, injects the same replay evidence/opportunity, and compares EntryQuality against the repository-backed Production path.

## Regression tests
1. Verified PEPE-shaped 71-minute opportunity retains FIX-055 anchor protection.
2. Exactly 120 minutes retains anchor authority.
3. 121 minutes returns authority to rolling 30-minute evidence.
4. Production and Replay produce identical score, classification, expansion, ATR extension and opportunity age after anchor expiry.

Historical BICO replay remains an outcome validation target, not a forced-BUY assertion: after expiry, BICO may still be blocked by any legitimate downstream gate.

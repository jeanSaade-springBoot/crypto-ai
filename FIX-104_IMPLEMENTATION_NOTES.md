# FIX-104 — Trade Activity symbol-filter stale-response guard

## Problem
Trade Activity could render the selected symbol correctly and then revert to all symbols when an older asynchronous ALL-symbol request completed later.

## Root cause
Multiple `loadTradeAnalysis()` calls could overlap. Responses were rendered unconditionally, so completion order rather than current filter state decided what remained on screen.

## Change
- Added an incrementing request sequence.
- Added `AbortController` cancellation of the previous fetch.
- Snapshot Symbol/Period/Type at request start.
- Before rendering, require both the request sequence and the filter snapshot to still match the current controls.
- Ignore `AbortError` and stale request errors.
- Cache-busted Trade Activity JS/CSS to `v=104`.

## Boundary
Read-only UI fix only. No signal generation, FinalDecision, ExecutionIntelligence, Replay, position, or wallet behavior changes.

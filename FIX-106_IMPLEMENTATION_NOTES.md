# FIX-106 — Trade Inspector database pagination

## Problem
Trade Inspector loaded only the newest 100 completed SELL wallet rows and filtered symbol in Java. Older trades could never appear. The SELL-to-BUY pairing also depended on the newest 100 wallet ledger rows.

## Change
- Database query filters completed SELLs by symbol before pagination.
- Response includes page, pageSize, totalElements and totalPages.
- Symbol dropdown reads distinct symbols from all persisted completed SELLs.
- Historical SELLs resolve BUY candidates from persisted prior BUY rows for the same symbol, preserving quantity-match-first then latest-prior-BUY fallback semantics.
- UI adds Previous/Next and 25/50/100 page sizes.
- View Chart and View Path continue to operate on the currently loaded trade row.

## Safety boundary
Read-only Trade Inspector only. No wallet writes, signal decisions, Execution Intelligence, FinalDecision, Production execution or Replay behavior changed.

## Regression
1. More than 100 completed trades remain reachable.
2. Symbol filter is applied before LIMIT/OFFSET.
3. Page navigation preserves symbol/venue/page-size filters.
4. Historical SELLs do not disappear because their BUY is older than the newest 100 wallet rows.
5. Existing View Chart/View Path actions continue to work.

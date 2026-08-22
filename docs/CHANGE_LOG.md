# Change Log

## FIX-055 — Long-lived opportunity entry-quality memory

- Added `anchor_entry_price` and `best_entry_price` to `execution_opportunity` via V66.
- Entry Quality now retains the actual BUY opportunity price base instead of relying only on a moving recent-signal window.
- `STOP_EXPOSED` remains a soft liquidity warning but now subtracts 15 points from Entry Quality, allowing the existing shared chase guard to reduce or reject late entries.
- Production and Replay continue to share `ExecutionIntelligenceService`; Replay stores the same price-memory fields in its scoped `ExecutionOpportunity` object without writing Production state.
- Added PEPEUSDT regression coverage for the 22 Aug 2026 late-breakout scenario.
- Hardened the Bug Fix Registry renderer so records using either `classes` or `files` cannot crash the page.
- No timestamp architecture change: MySQL/Binance/backend timestamps remain UTC; frontend time remains local/KSA presentation.

## FIX-056 — Fresh execution-price authority and stale wake-up protection
- Separated immutable signal/decision price from wallet execution fill price.
- Added `ExecutionPriceAuthorityService`: Production reads the newest canonical UTC Binance 1m `market_price_event`; Replay reads the newest already-consumed replay price event from `ExecutionReplayScope`.
- Fresh execution prices older than 15 seconds are rejected rather than silently filling from `TradeSignal.latestPrice`.
- Tightened `SETUP_CONFIRMATION_WAKEUP` 1m authority from 2 minutes to 45 seconds. SOL #617's 63-second-old signal is now explicitly rejected as a prior-cycle authority.
- Re-runs Entry Quality at execution time and cancels BUY when the live price is already at/below stop, at/above target, or has degraded into a chase.
- BUY risk sizing, wallet quantity, average entry and Replay shadow sizing now use the same execution-time price.
- Added wallet audit fields `decision_price_usdt` and `execution_price_observed_at`; `price_usdt` remains the actual fill price.
- Added Production/Replay regression coverage for stale setup wake-up and fresh-price revalidation.
- Database/Binance timestamps remain UTC; local/KSA conversion remains presentation-only.

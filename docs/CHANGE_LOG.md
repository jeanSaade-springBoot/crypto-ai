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


## FIX-058 — Trade Activity completed WIN/LOST couples
- Added exclusive COUPLE mode beside BUY/SELL.
- Added WIN/LOST result filters beside EXECUTED/BLOCKED.
- COUPLE mode resolves completed lifecycle pairs from CLOSED `wallet_managed_position`, not by loose timestamp pairing.
- The opening BUY is resolved from the position entry signal/open timestamp; the closing SELL is resolved from the same lifecycle close timestamp.
- WIN/LOST is classified from persisted SELL `wallet_trade.realized_pnl_usdt` (>0 WIN, <0 LOST).
- Both BUY and SELL rows are returned adjacent in the existing seven-column Trade Activity grid.
- Normal Trade Activity semantics remain `(BUY OR SELL) AND (EXECUTED OR BLOCKED) AND symbol`.
- Read-only audit feature only: no Production execution, Replay, scoring, wallet mutation or UTC/KSA architecture change.


## FIX-059 — Trade Activity forensic technical-analysis graph
- Added a chart only to the Trade Activity page; Dashboard, Trade Inspector and trading behavior are unchanged.
- The graph reads real CLOSED Binance 1m candles from `candle` and overlays every persisted `trade_signal` analysis in the selected symbol/time window, including BUY, SELL, WATCH and NEUTRAL across all analyzed timeframes.
- Added completed BUY→SELL lifecycle markers from CLOSED `wallet_managed_position` + real EXECUTED `wallet_trade` fills, with WIN/LOST outcome and realized P/L details.
- Trade pairs are visually highlighted across their real holding interval; clicking any analysis/trade marker opens persisted technical or execution details without covering the candle chart with permanent labels.
- The graph follows selected symbol + time range independently of grid filters so filtered couples retain their surrounding technical-analysis context.
- Database/MySQL/Binance timestamps remain UTC; Trade Activity parses them explicitly as UTC and displays chart/detail timestamps in KSA (`Asia/Riyadh`).
- Read-only audit feature: no signal generation, execution, wallet mutation, Replay or scoring logic changed.

## FIX-060 — Trade Activity SELL View on graph
- Added a **View on graph** action to Trade Activity SELL rows.
- Executed SELL rows now carry persisted `wallet_trade.id` and resolved `wallet_managed_position.id` so the UI can locate the exact completed lifecycle without timestamp/price guessing.
- Clicking the action loads the Trade Activity-only forensic graph, zooms from setup context through the SELL, highlights the persisted SELL fill, and keeps the related technical-analysis markers visible.
- If the SELL is blocked or has no completed pair, the graph focuses the SELL event and surrounding persisted analyses instead.
- Read-only/UI audit change only. Production, Replay, scoring, execution, and wallet behavior are unchanged.
- Database timestamps remain UTC; UI rendering remains KSA/Asia-Riyadh.


## FIX-061 — Trade Activity readable forensic price/indicator timeline
- Added persisted 1m `technical_indicator` snapshots to the Trade Activity forensic read model; the browser does not recalculate indicators.
- Graph now overlays close-price line, EMA20/50/200, SMA20, Bollinger upper/middle/lower and persisted ATR retracement levels against the real closed 1m candle path.
- Added an explicit KSA timeline label plus graph range metadata showing start/end in `Asia/Riyadh`; MySQL/Binance timestamps remain UTC internally.
- Completed BUY→SELL couples now show START and END labels directly on the graph and display WIN/FAIL percentage from persisted realized P/L divided by committed BUY gross.
- SELL **View on graph** preserves the pair START/END labels while focusing the selected trade.
- Reworked the Trade Activity grid to a responsive fixed-layout table with wrapping and selective low-priority column hiding, removing horizontal grid scrolling.
- Read-only UI/audit change only: no Production, Replay, scoring, execution, wallet, or indicator-calculation behavior changed.


## FIX-062 — Trade Activity forensic cockpit redesign
- Rebuilt only the Trade Activity presentation into a responsive operator cockpit; Dashboard/Inspector/trading behavior are unchanged.
- Replaced opaque/default chart styling with explicit high-contrast dark-mode colors for candles, price, EMA20/50/200, SMA20, Bollinger bands, ATR retracement and analysis/execution markers.
- Removed floating BUY/SELL START/END text boxes from the candle area. The selected completed trade now uses full-width horizontal BUY/SELL price lines with compact y-axis labels plus vertical start/end guides.
- Added selected-trade cards for START KSA, END KSA, duration, WIN/FAIL percentage and realized P/L, plus a persisted execution-facts strip and completed-window win-rate indicator.
- Added a synchronized real 1m volume chart below the price chart.
- Added entry and exit technical-summary cards resolved from persisted `trade_signal` evidence; mechanical exits use the nearest persisted pre-exit analysis rather than invented values.
- Added persisted fill price/P&L fields to the read-only Trade Activity projection so the compact list can show actual wallet values.
- Activity list is compact/responsive and fits the screen without horizontal scrolling.
- Database/Binance timestamps remain UTC; all visible Trade Activity times are rendered explicitly in KSA (`Asia/Riyadh`).
- Read-only audit/UI change only: no Production, Replay, scoring, indicator calculation, execution or wallet mutation behavior changed.

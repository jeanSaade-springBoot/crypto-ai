# FIX-101 — Trade Analysis compact redesign

## Scope
Read-only Trade Inspector redesign only. No Production trading, Replay, FinalDecision, ExecutionIntelligence, wallet execution, or position mutation behavior changed.

## Filters
- Symbol: all persisted symbols across signals, recent wallet trades, and open managed positions.
- Period: 15 minutes, 1 hour, 4 hours, 1 day, 1 week.
- Type: Blocked BUY, Blocked SELL, BUY / SELL done, BUY open.

## Data authority
- Blocked BUY reuses `TradeSignalRepository.findBlockedBuys(...)`.
- Blocked SELL reuses `TradeSignalRepository.findBlockedSells(...)`; this preserves the existing definition: original SELL/STRONG_SELL changed to a non-SELL final decision.
- BUY / SELL done reads completed executed SELL rows from `wallet_trade` and enriches with the linked exit signal when present.
- BUY open reads current `wallet_managed_position` rows with status OPEN and enriches with `entry_signal_id` when present.

## UI
The old low-level interval/decision/state/manual-date/row-count controls were replaced with the three requested dropdowns. Analyze still opens persisted evidence only.

## Compatibility
Static assets are cache-busted with `v=101`. Existing Trade Inspector completed-trade cards, charts, path analysis, and all prior FIX-091 through FIX-100 behavior remain intact.

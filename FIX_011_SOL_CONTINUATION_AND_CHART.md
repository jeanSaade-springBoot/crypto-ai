# FIX-011 — SOL TP continuation alignment + Trade Inspector hover price

## SOLUSDT scenario
- Entry: 2026-08-19 16:25:31 KSA @ 78.45, signal #94801.
- Historical TP exit: 17:17:23 KSA @ 78.77.
- At TP, Position Management said HOLD: trend pressure 2/8, momentum pressure 0/5.
- Old TP continuation failed because a separate binary floor required trend >= 18 while current trend was 16.

## Change
- Added `PositionThesisPressurePolicy` containing the exact immutable-BUY-thesis deterioration calculation previously embedded in `PositionManagementService`.
- `PositionManagementService` now calls the shared policy; its scoring formula is unchanged.
- `PositionContinuationPolicy` adds a narrow `THESIS_INTACT_CONSOLIDATION` path:
  - current 1m supportive (WATCH/BUY/STRONG_BUY),
  - 5m non-bearish,
  - 1h non-bearish,
  - trend pressure <= 2/8,
  - momentum pressure <= 1/5.
- SELL/STRONG_SELL on any monitored timeframe remains an absolute veto before this path is considered.
- Stop loss remains absolute and unchanged.

## Production / Replay parity
- Live protection passes entry trend, structure, momentum, volume, confidence and total score.
- Shadow Production Replay now stores entry structure and passes the exact same immutable thesis to the exact same `PositionContinuationPolicy`.
- No replay-only continuation formula was added.

## Regression
- Exact SOL state 21/5/13/19 -> 16/2/15/7 must PASS as `THESIS_INTACT_CONSOLIDATION` with pressure 2/8 and 0/5.
- Severe trend/structure breakdown must FAIL.
- Bearish 5m must FAIL.
- Existing continuation tests remain.

## Trade Inspector
- FIX-010 is retained and tightened: the price scale is explicitly placed on the right and ApexCharts Y-axis tooltip remains enabled/styled.
- Hover shows the exact cursor price on the right Y axis with the horizontal crosshair, Binance/TradingView style.

## Unchanged
BUY generation, confluence, ATR entry validation, wallet sizing, execution intelligence, normal SELL authority, stop loss, profit-lock logic, and opportunity/re-entry logic are unchanged by FIX-011.

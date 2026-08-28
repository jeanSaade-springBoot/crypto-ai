# FIX-094A — Catching Market interval accessor compile correction

- Corrected `PriceMoveMonitorService.eventChart()` from `TradeSignal.getIntervalCode()` to `TradeSignal.getInterval()`.
- `TradeSignal` maps Java field `interval` to database column `interval_code`; Lombok therefore generates `getInterval()`.
- No trading, Replay, FinalDecision, ExecutionIntelligence, or wallet behavior changed.
- FIX Registry updated.

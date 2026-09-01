# FIX-11Q — One-Candle Continuation Grace (Replay-Only Experiment)

## Scope
Replay/regression only. Production classes and Production trading behavior are unchanged.

## Rule
The existing `PositionContinuationPolicy` is evaluated first. If it returns PASS, Replay behaves exactly as before and the existing take-profit extension remains authoritative.

Only after the baseline policy returns FAIL at a reached take-profit checkpoint may FIX-11Q start a separate counterfactual position when all conditions hold:

- current 1m final decision is `NEUTRAL`;
- current 1h final decision is `BUY` or `STRONG_BUY`;
- current 5m final decision is not `SELL` or `STRONG_SELL`;
- current momentum remains at or above the existing continuation momentum floor;
- the baseline replay position has not already consumed grace.

The counterfactual holds for the remainder of that exact 1m candle without changing take profit. Stop loss and profit-lock protection remain active. Once a new 1m candle becomes current, the grace is permanently expired and the existing shared continuation/exit policies govern the counterfactual normally.

## Isolation
The baseline Shadow position, baseline cash, trade count, realized P/L, wallet test rows and PASS/FAIL outcome are not mutated by FIX-11Q. The experiment is stored only in `one_candle_continuation_grace_test`.

## Evidence
The primary evidence case is PEPEUSDT around 2026-08-30 16:13:24 UTC (19:13:24 KSA), where baseline continuation failed with 1m NEUTRAL, 5m NEUTRAL and 1h STRONG_BUY, followed by a fresh entry roughly 90 seconds later near the same price.

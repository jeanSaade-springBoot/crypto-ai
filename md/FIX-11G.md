# FIX-11G — BALANCED_NEUTRAL_5M_WATCH_1H transitional BUY authority

## Classification

**CONTROLLED PRODUCTION BEHAVIOR CHANGE**

This fix intentionally changes one narrow Production execution-authority state. It does not change signal generation, scoring, FinalDecision, ATR calculations, BTC classification, Order Book classification, SELL logic, or position-management behavior.

Golden rule: **Replay = Production**. Historical Replay supplies as-of historical signals through `ExecutionReplayScope`, but it invokes the exact same `TradeExecutionValidationService.validateBuy()` method and the same downstream `ExecutionIntelligenceService` guards as Production. There is no Replay-only copy or threshold.

## Confirmed gap

The BALANCED matrix already supports its established bullish/WATCH combinations and FIX-112B adds the separate `5m=NEUTRAL + 1h=BUY/STRONG_BUY` exception. It had no direct authority for the distinct transition:

- fresh 1m `BUY` / `STRONG_BUY`
- 5m `NEUTRAL`
- 1h `WATCH`

EDUUSDT signal 320341 on 2026-08-29 01:24:59.999 UTC demonstrated this gap. The signal was already an upstream-approved BUY (`final_entry_allowed=true`), while 5m remained NEUTRAL and 1h remained WATCH. Normal BALANCED rejected the combination and FIX-112B did not apply. The accumulated-evidence fallback later reused plain `balancedBuy()` through `validateBuyContext()`, so it could not repair the same authority gap.

## Implementation

The new branch is appended inside `balancedBuyWithNeutralFiveException()` only after:

1. the existing `balancedBuy()` result rejects; and
2. the existing FIX-112B `5m=NEUTRAL + bullish 1h` branch does not approve.

FIX-11G then checks the exact `5m=NEUTRAL + 1h=WATCH` state. It can grant `BALANCED_NEUTRAL_5M_WATCH_1H` with a maximum 25% initial authority only when:

- the current 1m signal is already a BUY/STRONG_BUY by virtue of the existing direct `validateBuy()` path;
- `final_entry_allowed=true` so the new branch cannot override an upstream FinalDecision veto;
- Entry Quality is at least 50, matching the existing global chase cutoff semantics;
- BTC status is neither `CONFLICT` nor `STRONG_CONFLICT`;
- the existing freshness and bearish 5m/1h vetoes have already passed before this branch is reached.

No new confidence threshold is introduced. Confidence remains owned by the upstream signal/FinalDecision pipeline; FIX-11G does not fit a new threshold to a single historical trade.

## Safety boundaries deliberately preserved

- `balancedBuy()` is unchanged.
- FIX-112B is unchanged and is evaluated first.
- `validateBuyContext()` is unchanged, so accumulated-evidence/recovery authority is not widened.
- Conservative and Aggressive profile behavior is unchanged.
- `5m=NEUTRAL + 1h=NEUTRAL` remains rejected.
- Any bearish 5m or 1h context still hits the existing veto before FIX-11G.
- `final_entry_allowed=false` cannot be rescued by FIX-11G.
- BTC `CONFLICT` / `STRONG_CONFLICT` cannot be rescued by FIX-11G.
- Entry Quality below 50 remains ineligible, and the normal downstream `applyInitialEntryQualityGuard()` still runs after FIX-11G grants authority.
- The 25% FIX-11G result is a maximum; downstream authority can only preserve or reduce it.

## Searchable application logs

Search for `FIX-11G` in the application log.

`TradeExecutionValidationService` logs when the exact transitional state is observed and records either:

- `FIX-11G transitional BUY authority granted`
- `FIX-11G transitional BUY authority not granted`

`ExecutionIntelligenceService` then logs the post-guard result:

- `FIX-11G post-guard result`

This distinguishes qualification for the new authority from the final result after the existing shared Entry Quality/authority guard.

## Regression coverage

`TradeExecutionValidationServiceTest` covers:

- an upstream-approved direct BUY in `5m=NEUTRAL + 1h=WATCH` at the existing Entry Quality boundary;
- Entry Quality below the boundary;
- `final_entry_allowed=false`;
- BTC `CONFLICT` and `STRONG_CONFLICT`;
- unchanged `validateBuyContext()` behavior;
- Replay using `ExecutionReplayScope` historical 5m/1h context while invoking the same Production `validateBuy()` method.

Existing FIX-112B and BALANCED regression coverage remains in place.

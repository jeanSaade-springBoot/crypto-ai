# SOL pressure-probe refinement — FIX-006

## Goal
Add a narrowly-scoped early exploratory entry for the analyzed SOLUSDT reversal without changing or weakening the existing normal BUY / 1m-5m-1h confirmation path.

## Historical production scenario
- Symbol: SOLUSDT
- Date: 2026-08-17
- Analyzed early window: ~00:43-01:00 UTC (03:43-04:00 KSA)
- Later production wallet entry: signal #80012 at 14:14:06 UTC (17:14:06 KSA), price 75.63
- Candidate early probe zone from the analyzed evidence: ~74.62-74.71 around the 00:58-01:00 UTC evaluation window

## Root cause
The existing engine saw the lower-timeframe reversal but had no safe vocabulary between a bearish higher timeframe and a fully confirmed normal BUY. The prior pressure implementation also required 5m to have already recovered and blocked 1h STRONG_SELL, so it could not represent the SOL sequence where:
1. A meaningful bullish burst occurred.
2. The burst was rejected by real sell pressure.
3. The retest held above the pre-burst structural low.
4. Buyers rebuilt pressure.
5. The 1m returned to a strong WATCH while the latest 5m was still the retest SELL and the old 1h remained STRONG_SELL.

## Production behavior after the change
PRESSURE_PROBE_ENTRY is an additive 15% exploratory route only. It requires:
- current 1m final decision WATCH or NEUTRAL (never a fallback for BUY),
- score/confidence/trend/volume/momentum minimum quality,
- all existing FinalDecision / strategy / ATR / BTC / liquidity / derivatives entry gates,
- a closed-candle sequence: burst -> rejection -> higher-low retest -> pressure rebuild -> reclaim,
- a recent 5m WATCH/BUY BREAKOUT setup with strong volume/momentum,
- latest 5m must not be STRONG_SELL.

A bearish 1h may coexist only with this 15% exploratory probe. It still blocks/controls the existing normal/full-size confirmation path.

## Replay / Proven Analysis parity
ShadowProductionReplayService still calls the exact production ExecutionIntelligenceService.evaluateBuy(...). PressureReadinessService is the same injected production service. It queries only candles whose `close_time <= signal.generated_at`, preventing future-candle leakage in historical replay.

## Regression protections
- Exact SOL candle fixture proves the first burst is not enough.
- Exact SOL fixture becomes ready only after rejection, higher-low retest and pressure rebuild.
- As-of repository query is verified against `generated_at` using candle close time.
- Execution test proves 15% SOL probe can coexist with bearish 1h only when a recent 5m BREAKOUT setup exists.
- Execution test proves candles alone cannot invent the missing 5m setup.
- Existing normal direct BUY retains priority over PRESSURE_PROBE_ENTRY.

## Validation note
JavaScript syntax validation passed and structural Java delimiter checks passed. Maven tests could not be executed in the provided container because neither `mvn` nor a Maven wrapper is installed. Run the full Maven/Jenkins suite before deployment.

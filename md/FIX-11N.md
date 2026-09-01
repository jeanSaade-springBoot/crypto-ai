# FIX-11N — Replay-only historical derivatives isolation

## Scope

FIX-11N is limited to Regression Replay signal generation. It does not change normal Production analysis, FIX-043 Production recovery, wallet execution, BUY/SELL thresholds, position management, or any Production decision rule.

## Evidence

FIX-11M measured the PEPEUSDT Replay analysis path at 626.496 seconds across 938 calls. The downstream derivatives stage alone consumed 497.128 seconds, averaging 529.987 ms per call and reaching 1561.222 ms maximum.

Source review showed that `AnalysisService.buildSignal(...)` always called `DerivativesPositioningService.evaluate(...)` in the downstream derivatives stage. That method reads live Binance futures funding and open-interest history. This happened even when `analyzeForRegression(...)` was reconstructing historical candles.

`DerivativesPositioningService` already contains `evaluateHistorical(...)` with an explicit contract that historical Replay must not call live Binance futures endpoints. Because derivatives history is not persisted by the application, that method returns the truthful historical state as `UNAVAILABLE`.

## Production isolation

The prior `historicalReplay` flag is shared by Regression Replay and FIX-043 Production recovery. Reusing it directly for the new routing would therefore change Production recovery semantics.

FIX-11N adds a separate private `regressionReplay` flag at the `buildSignal(...)` boundary:

- `analyze(...)` -> `historicalReplay=false`, `regressionReplay=false`
- `analyzeRecovered(...)` -> `historicalReplay=true`, `regressionReplay=false`
- `analyzeForRegression(...)` -> `historicalReplay=true`, `regressionReplay=true`

Only `regressionReplay=true` selects `derivativesPositioningService.evaluateHistorical(...)` in the downstream derivatives stage. Production and Production recovery continue calling `evaluate(...)` exactly as before.

## Replay behavior note

This is not only timing instrumentation. Replay derivatives context changes from live-current Binance futures data to the existing historical `UNAVAILABLE` result. That removes time-dependent live derivatives input from historical Replay and eliminates the associated external HTTP calls, but Replay parity/business output must be validated after deployment before Phase A work continues.

## Validation

Repeat the same PEPEUSDT DATASET window used for FIX-11M and compare:

- `FIX11M_REPLAY_ANALYSIS_PROFILE`, especially `derivativesMs`, `derivativesAvgMs`, and `totalMs`.
- Replay stage timings, especially `Generate fresh signals` and TOTAL.
- Replay parity/business results against the pre-FIX-11N run.
- Production logs/behavior separately to confirm normal Production and FIX-043 recovery still use the original live derivatives path.

If Replay parity or trading output changes materially, stop and review the result before proceeding to FIX-11K Phase A validation.

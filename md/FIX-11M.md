# FIX-11M — Replay Analysis-Service Deep Performance Profiler

## Evidence

FIX-11L measured the PEPEUSDT DATASET replay (`2026-08-30 17:00–20:30 UTC`) and proved that `AnalysisService.analyzeForRegression()` is the dominant cost inside Generate fresh signals:

- Generate fresh signals: `649.960 s`
- `analyzeForRegression()`: `559.235 s`
- Analysis calls: `938`
- Average analysis call: `596.199 ms`
- Maximum analysis call: `1297.111 ms`

Persistence, lineage lookup, progress writes and residual loop overhead were all small by comparison.

## Scope

FIX-11M is **Replay observability only**. It does not optimize anything yet.

`RegressionTestWorker` activates a ThreadLocal profiler in `AnalysisService` only around the fresh-signal replay stage. `AnalysisService.buildSignal()` keeps the existing call order and business logic while measuring these existing components:

- sentiment
- fundamentals
- previous technical snapshot
- trend structure
- local/base scoring
- ATR risk
- regime + replay regime state
- market context
- strategy selection/scoring/entry plan
- multi-timeframe confluence
- BTC context
- derivatives positioning
- historical order-book liquidity
- entry authority
- range-entry location
- final decision
- explanation / serialization / TradeSignal assembly

Each category records total time, calls, average time and maximum time. One aggregate log line is emitted when fresh-signal generation exits:

`FIX11M_REPLAY_ANALYSIS_PROFILE`

## Safety

FIX-11M makes **zero trading-behavior changes**:

- no Production profiler activation
- no cache
- no query rewrite
- no thresholds
- no BUY/SELL changes
- no execution-order changes
- no replay position mutation
- no persistence semantic changes
- no change to FIX-11K Phase A observe-only defensive-risk collection

The profiler is ThreadLocal so unrelated Production requests cannot contribute to Replay measurements. Diagnostic-summary failure is guarded in `RegressionTestWorker` and cannot turn an otherwise-valid Replay run into an error.

## Validation

Repeat the same PEPE DATASET replay window used for FIX-11L. Verify normal Replay parity/business results first, then collect:

```cmd
powershell -Command "Select-String -Path 'C:\apps\crypto-ai\crypto-ai.log' -Pattern 'FIX11M_REPLAY_ANALYSIS_PROFILE' | Select-Object -Last 20"
```

Compare FIX-11M `totalMs` with FIX-11L `analysisMs`. The largest internal category becomes the next Replay-only optimization target; no optimization is approved by this profiling fix itself.

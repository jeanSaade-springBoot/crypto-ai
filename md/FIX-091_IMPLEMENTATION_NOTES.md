# FIX-091 implementation notes

This package implements the approved pre-wallet scope only. Wallet classes and wallet behavior are unchanged.

## Implemented

1. BTC moderate CONFLICT exposure cap in the existing initial-entry quality sizing guard. STRONG_CONFLICT veto classification is unchanged.
2. Live order-book insufficient sampling is now `INSUFFICIENT_DATA_HOLD`; historical Replay continues to use `evaluateHistorical()` and never substitutes current/live Binance depth for an old candle.
3. Raw confidence, effective confidence and primary blocking stage are persisted and mirrored into Replay tables/API diagnostics.
4. Replay-only regime persistence (`detected` / `candidate` / `confirmed`) uses replay-run isolation and candle timestamps; Production regime behavior is unchanged until parity approval.
5. Replay-only `BREAKOUT_TRANSITION` / `EntryAuthority` is a maximum-size authority and still requires all normal downstream validation. The ordinary RANGE 55% rule remains unchanged; only the explicit transition overload may bypass it when structural and safety conditions are complete.
6. `ReplayParityValidator` compares the agreed pre-wallet fields.
7. Recent rejection-zone awareness is a soft Entry Quality penalty / sizing effect only and cannot by itself create a chase hard veto.
8. Accumulated evidence tracks a recent peak and returns `ACCUMULATED_EVIDENCE_DETERIORATED` on material decay, with 15-minute expiry and stronger-peak recovery.

## Replay parity

`trade_signal_test`, `analysis_test_signal`, `execution_opportunity_test` and their archives receive the new diagnostic/state columns through Flyway V72. Production-shaped Replay persistence continues to mirror `TradeSignal` fields automatically. Shadow replay opportunity persistence now mirrors peak-evidence state.

## Explicitly excluded

- No `balancedBuy()` relaxation.
- No `validateBuy()` bypass.
- No `BUY_CONTINUATION` change.
- No historical `OrderBookSnapshot` persistence/reconstruction subsystem.
- No wallet-layer behavior changes.

## Validation performed in this workspace

All changed/new Java files were parsed successfully with the JDK compiler parser (syntax validation). This workspace does not contain Maven or a Maven wrapper/dependency cache, so a full Spring/Maven compile and test suite could not be executed here. Run `mvn test` in the normal project build environment before deployment.

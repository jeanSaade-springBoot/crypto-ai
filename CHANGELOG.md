# Change Log

## FIX-064 — Immediate closed-candle price-action revalidation

- Added a surgical raw-price-action revalidation for the SCOUT_ENTRY, DEFERRED_CONTINUATION, and ACCUMULATED_EVIDENCE entry routes.
- Does **not** change WEAK_UPTREND, ACCUMULATED_EVIDENCE, scoring thresholds, market regimes, or BUY/SELL classification.
- Uses only fully closed 1m candles with `close_time <= signal.generated_at`, preserving Production/Replay parity and preventing look-ahead.
- Keeps an invalidated opportunity alive in WAIT/BUILDING state instead of cancelling it.
- Added regression tests for bearish rejection, exhausted-pop rejection, healthy continuation, and small normal pullback preservation.
- Historical regression anchors: ENA wallet #703, PEPE wallet #756, SUI wallet #802. Winning controls to replay: BNB #776/#788/#790, PEPE #833, XLM #811/#859, SHIB #814/#869.

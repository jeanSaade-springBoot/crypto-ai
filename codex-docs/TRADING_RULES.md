# FIX-154 higher-timeframe BUY rule — 2026-10-10

- `data_quality_valid=true` is required before a bearish 1h signal can be
  treated as trusted. `NULL` is unknown and fails closed for bearish evidence.
- A trusted `STRONG_SELL` at `confidence_score >= 70` blocks the BUY.
- A trusted `SELL`, or trusted `STRONG_SELL` below 70, is a SOFT conflict:
  profiles receive effective 1h `NEUTRAL`, and any allowed initial BUY is
  capped at 25%.
- A bearish 5m still returns `5M_BEARISH_VETO` before any 1h result.
- Existing progressive/add rules retain their raw 1h requirement; a SOFT 1h
  never authorizes an add.

# FIX-153 candle continuity rule — 2026-10-10

- Missing price evidence never becomes invented OHLCV data and never bypasses
  the data-quality gate.
- Automatic reconciliation may restore only one exact, final Binance candle.
  Its recovered/backfilled row has no close token and cannot directly cause
  analysis, a signal, or a trade.
- A later normal live close is the only route that resumes ordinary signal
  generation after a repaired quality window.

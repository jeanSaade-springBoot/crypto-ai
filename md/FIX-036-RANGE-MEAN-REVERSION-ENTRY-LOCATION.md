# FIX-036 — RANGE mean-reversion entry-location guard

## Proven scenario

ETH signal #109885 (21 Aug 2026, 16:09 KSA) produced BUY 87 under `RANGE_MEAN_REVERSION` at 2391.22. Bollinger geometry was lower 2383.2921, middle 2389.4605, upper 2395.6289, placing the entry about 64% from lower to upper band. Momentum (14/15), volume (19/20) and improving structure were genuinely bullish, but this was a poor ordinary mean-reversion location. The position stopped at approximately 16:30 KSA.

A historical review found six executed RANGE_MEAN_REVERSION bullish entries above the middle band: five lost and one SHIB STRONG_BUY transition won. A separate last-20 regression check showed all recent winners were TREND_FOLLOWING/BREAKOUT; the proposed RANGE-only guard would not have touched those winners.

## Surgical correction

`RangeEntryLocationService` runs only when the selected strategy is `RANGE_MEAN_REVERSION` and the strategy score is BUY/STRONG_BUY. It calculates current position from lower-to-upper Bollinger band:

- <= 55%: normal RANGE entry remains allowed.
- > 55%: ordinary RANGE entry is blocked.
- > 55% strict transition exception: entry remains allowed only when all of these are true: STRONG_BUY, normalized score >= 90, range-profile volume >= 85%, momentum >= 80%, bullish expansion structure confirmed, and RVOL >= 2.0x.

The technical decision and score are never rewritten. A blocked ETH-style signal remains `BUY 87`; only `final_entry_allowed` becomes false, with `RANGE_ENTRY_LOCATION` recorded in `decision_path`.

## Isolation guarantees

No change to TREND_FOLLOWING, BREAKOUT, DEFENSIVE, scoring weights, RSI/MACD/volume scoring, ATR, stop loss, take profit, SELL logic, continuation, Wallet, Binance or position management.

`AnalysisService.buildSignal(...)` is shared by Production and `analyzeForRegression(...)`, therefore the same guard runs in both Production and Replay/regression.

## Regression protection

Focused tests cover:

1. ETH #109885-style BUY 87 at ~64% of Bollinger range -> blocked.
2. Historical SHIB-style STRONG_BUY 93, RVOL ~2.99x, expansion confirmed -> allowed via strict transition exception.
3. Normal RANGE BUY at 40% -> allowed.
4. High-band TREND_FOLLOWING STRONG_BUY -> guard not applicable.

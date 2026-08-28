# Timeframe-aware liquidity and strategy-aware confluence

## Order book

A single live collector remains shared per configured symbol. Signal evaluation now selects an interval policy containing:

- rolling window length
- minimum observations
- minimum persistent-wall duration
- influence factor
- whether a strong conflict may veto entry

The immutable trade signal snapshot stores the actual window, wall persistence, influence and veto policy used.

## Multi-timeframe confluence

The final confluence evaluation now receives the selected strategy:

- TREND_FOLLOWING: ordinary opposing higher-timeframe direction can veto.
- RANGE_MEAN_REVERSION: a higher-timeframe range confirms the strategy; moderate directional opposition is contextual rather than an automatic veto.
- BREAKOUT: at least one aligned higher timeframe is required for a directional breakout entry.
- DEFENSIVE: bullish entry requires all available higher timeframes to confirm.
- NO_TRADE: new entry remains blocked.

The pre-strategy MarketContextService still performs a generic context read. After the strategy is selected, AnalysisService performs the final strategy-aware confluence evaluation.

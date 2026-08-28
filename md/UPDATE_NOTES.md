# Dashboard schedule and score normalization update

## Analysis score correction

`AnalysisService` now always converts the available raw category score to a 0-100 score:

- Sentiment enabled: maximum raw score is 85.
- Sentiment disabled: maximum raw score is 70.
- Formula: `round(rawScore * 100 / maximumAvailableScore)`.

The trade-signal explanation now includes both the raw and normalized score.

## Dashboard schedule panel

The dashboard now displays runtime configuration for:

- Binance historical bootstrap
- Binance WebSocket stream, reconnect delay, and health check
- Candle fallback collection
- Event-driven closed-candle analysis
- Scheduled analysis fallback
- Dashboard browser refresh
- Sentiment scheduler scan and active window
- Every configured sentiment provider and its configured weight
- Whale transaction collection
- Whale evaluation cadence, price interval, and horizons
- Whale aggregation cadence, active window, and horizon

The provider table remains the source for each sentiment provider's database-backed collection interval.

## Configuration alignment

The sentiment scheduler property was standardized to:

```yaml
sentiment.scheduler.fixed-delay-ms
```

The browser refresh is configurable with:

```yaml
dashboard.refresh-ms: ${DASHBOARD_REFRESH_MS:10000}
```

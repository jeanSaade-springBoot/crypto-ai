# Whale sentiment integration

## Added

- One new whale-specific table: `whale_activity`
- Adaptive wallet weight calculation
- 1h, 4h and 24h delayed evaluations
- Whale Alert collection scheduler
- Whale sentiment aggregation into existing `sentiment_signal`
- Existing `SentimentService` automatically includes `WHALE_ALERT` using the provider weight in `sentiment_provider`
- Manual ingestion endpoint: `POST /api/whales/transactions`
- Manual aggregation endpoint: `POST /api/whales/aggregate/{symbol}`

## Required environment variables to enable collection

```text
WHALE_ENABLED=true
WHALE_ALERT_ENABLED=true
WHALE_ALERT_API_KEY=<your key>
```

The Flyway V6 migration creates the table and sets the `WHALE_ALERT` provider weight to `0.20`.

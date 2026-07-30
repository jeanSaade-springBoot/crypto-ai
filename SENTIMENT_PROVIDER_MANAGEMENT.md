# Sentiment provider management

## Database tables

- `sentiment_signal`: collected/analyzed sentiment samples.
- `sentiment_provider`: enabled status, weight, collection interval, last run, last success, status and message.

API keys are intentionally not stored in MySQL. `api_key_env_var` shows the environment variable that must be configured.

## Scheduler

The scheduler checks due providers every 60 seconds. Each provider uses its own `collection_interval_seconds` value from `sentiment_provider`.

## REST API

- `GET /api/sentiment/providers/{symbol}`
- `PATCH /api/sentiment/providers/{provider}`
- `POST /api/sentiment/providers/{provider}/collect`
- `POST /api/sentiment/collect`

PATCH example:

```json
{
  "enabled": true,
  "weight": 0.25,
  "collectionIntervalSeconds": 300
}
```

The global `SENTIMENT_ENABLED` and `SENTIMENT_SCHEDULER_ENABLED` environment variables remain master switches.

# Sentiment enable/disable configuration

Sentiment is disabled by default, so the application can run without any API key.

## Run without sentiment

```yaml
sentiment:
  enabled: false
```

or set:

```text
SENTIMENT_ENABLED=false
```

When disabled:

- no provider collection is performed;
- the scheduler exits immediately;
- `AnalysisService` does not query sentiment rows;
- sentiment points are excluded;
- the remaining technical/fundamental score is normalized from 85 to 100, preserving the existing BUY thresholds.

## Enable Fear & Greed only (no API key)

```text
SENTIMENT_ENABLED=true
SENTIMENT_SCHEDULER_ENABLED=true
FEAR_GREED_ENABLED=true
CRYPTOPANIC_ENABLED=false
NEWS_API_ENABLED=false
```

## Enable providers later

```text
SENTIMENT_ENABLED=true
SENTIMENT_SCHEDULER_ENABLED=true
CRYPTOPANIC_ENABLED=true
CRYPTOPANIC_API_KEY=...
NEWS_API_ENABLED=true
NEWS_API_KEY=...
```

Status endpoint:

```http
GET /api/sentiment/status
```

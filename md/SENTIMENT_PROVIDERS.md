# Multi-provider sentiment

## Combined formula

1. Every sample is scored from -1 to +1.
2. Samples are weighted by confidence and recency inside their provider.
3. Each provider is averaged independently.
4. Enabled provider averages are combined using the configured provider weight and provider confidence.

This prevents a provider with many articles from dominating only because of volume.

## Automatic collectors included

- Alternative.me Fear & Greed: enabled by default; no key required.
- CryptoPanic: set `CRYPTOPANIC_ENABLED=true` and `CRYPTOPANIC_API_KEY`.
- NewsAPI: set `NEWS_API_ENABLED=true` and `NEWS_API_KEY`.

Collection runs every five minutes and can also be triggered with:

```http
POST /api/sentiment/collect
```

## Weighted ingestion supported

Rows from these source names are automatically grouped into their canonical provider:

- `CRYPTOPANIC`
- `NEWS_API` or `NEWSAPI`
- `REDDIT`
- `X` or `TWITTER`
- `FEAR_GREED` or `ALTERNATIVE_ME`
- `BINANCE_ANNOUNCEMENT`
- `WHALE_ALERT`
- `MANUAL_NEWS`

Reddit, X, Binance announcements, and Whale Alert are present in the weighting configuration. Their automatic adapters remain disabled until their API-specific credentials and access plan are configured. They can already contribute through `POST /api/sentiment` or `POST /api/sentiment/analyze` with the matching source name.

## Configuration

Provider weights are in `application.yml` under `sentiment.providers`. The numbers do not need to total 1 because the service normalises by the sum of effective enabled weights.

The dashboard shows provider score, configured weight, confidence, effective weight, sample count, and the final combined score.

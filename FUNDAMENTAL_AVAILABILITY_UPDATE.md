# Fundamental collection and availability normalization

- Automatically collects market cap, FDV, 24h volume and supply from CoinGecko for configured symbols.
- A category contributes to raw score and maximum only when fresh data is available.
- Sentiment availability requires at least one enabled provider with positive effective weight.
- Fundamental availability requires a fresh record with market cap, volume and circulating supply.
- Every trade signal stores category availability and exclusion reasons.

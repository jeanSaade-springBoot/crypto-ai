# Crypto AI Trader — Full Sentiment and Analysis Structure

## 1. End-to-end runtime flow

```text
Application startup
    |
    +--> Flyway validates/creates schema
    +--> SentimentProviderConfigService ensures provider configuration
    +--> MarketDataBootstrapService loads historical candles
    +--> BinanceWebSocketManager opens live streams

Live Binance kline
    |
    v
BinanceWebSocketHandler
    |
    v
BinanceKlineService.processKline()
    |
    +--> saves/updates candle
    |
    +--> when candle closes: publishes CandleClosedEvent
                              |
                              v
                    CandleClosedEventListener
                              |
                              v
                    TechnicalIndicatorService
                              |
                              +--> calculates indicators
                              +--> saves technical_indicator
                              |
                              v
                        AnalysisService
                              |
                              +--> trend score
                              +--> volume score
                              +--> momentum score
                              +--> combined sentiment score
                              +--> fundamental score
                              +--> stop-loss/take-profit
                              |
                              v
                        saves trade_signal
                              |
                              v
                       PaperTradingService
                              |
                              v
                       saves paper_position
```

Sentiment and whale processing run independently and provide the most recent data whenever `AnalysisService` runs.

---

## 2. Main package structure

```text
com.crypto
|
+-- CryptoAiTraderApplication
|
+-- client
|   +-- binance
|       +-- BinanceMarketDataClient
|       +-- dto/BinanceKline
|       +-- websocket
|           +-- BinanceWebSocketHandler
|           +-- BinanceWebSocketManager
|           +-- BinanceStreamUrlBuilder
|   +-- config/binance
|       +-- BinanceConfiguration
|       +-- BinanceMarketDataProperties
|
+-- config
|   +-- JacksonConfig
|   +-- SentimentProperties
|   +-- TradingProperties
|
+-- controller
|   +-- AnalysisController
|   +-- SentimentController
|   +-- FundamentalController
|   +-- TechnicalIndicatorController
|   +-- PaperTradingController
|   +-- MarketDataController
|   +-- BinanceStatusController
|   +-- DashboardController
|   +-- DashboardApiController
|   +-- ApiExceptionHandler
|
+-- domain
|   +-- Candle
|   +-- TechnicalIndicator
|   +-- SentimentSignal
|   +-- SentimentProviderConfig
|   +-- MarketFundamental
|   +-- TradeSignal
|   +-- PaperPosition
|   +-- SignalDecision
|   +-- PositionSide
|   +-- PositionStatus
|
+-- dto
|   +-- IndicatorSnapshot
|   +-- ProviderSentiment
|   +-- SentimentOverview
|   +-- SentimentProviderStatus
|   +-- SentimentProviderUpdateRequest
|   +-- SentimentRequest
|   +-- SentimentTextRequest
|   +-- FundamentalRequest
|
+-- indicator
|   +-- calculator
|   |   +-- SmaCalculator
|   |   +-- EmaCalculator
|   |   +-- RsiCalculator
|   |   +-- MacdCalculator
|   |   +-- BollingerBandsCalculator
|   |   +-- AtrCalculator
|   |   +-- RelativeVolumeCalculator
|   +-- event
|   |   +-- CandleClosedEvent
|   |   +-- CandleClosedEventListener
|   +-- model
|   |   +-- TechnicalIndicatorResult
|   |   +-- MacdResult
|   |   +-- BollingerBandsResult
|   +-- service
|       +-- TechnicalIndicatorService
|
+-- repository
|   +-- CandleRepository
|   +-- TechnicalIndicatorRepository
|   +-- SentimentSignalRepository
|   +-- SentimentProviderConfigRepository
|   +-- MarketFundamentalRepository
|   +-- TradeSignalRepository
|   +-- PaperPositionRepository
|
+-- service
|   +-- BinanceKlineService
|   +-- MarketDataService
|   +-- MarketDataBootstrapService
|   +-- SentimentProviderName
|   +-- SentimentProviderConfigService
|   +-- SentimentCollectionService
|   +-- SentimentTextAnalyzer
|   +-- SentimentService
|   +-- FundamentalService
|   +-- AnalysisService
|   +-- ScheduledAnalysisService
|   +-- PaperTradingService
|
+-- whale
    +-- client/WhaleApiClient
    +-- config/WhaleProperties
    +-- controller/WhaleController
    +-- domain
    |   +-- WhaleActivity
    |   +-- WhaleMovementType
    |   +-- WhaleEvaluationHorizon
    |   +-- WhaleEvaluationResult
    +-- dto
    |   +-- WhaleTransactionInput
    |   +-- WhaleSentimentResult
    +-- repository/WhaleActivityRepository
    +-- scheduler
    |   +-- WhaleCollectionScheduler
    |   +-- WhaleEvaluationScheduler
    |   +-- WhaleAggregationScheduler
    +-- service
        +-- WhaleTransactionService
        +-- WhaleEvaluationService
        +-- WhaleAggregationService
        +-- WhalePriceService
        +-- WhaleWeightCalculator
```

---

## 3. Sentiment providers

`SentimentProviderName` recognizes:

| Provider | Role | Current collection status |
|---|---|---|
| `FEAR_GREED` | Global market mood | Implemented |
| `CRYPTOPANIC` | Crypto headlines | Implemented when API key is configured |
| `NEWS_API` | General news headlines | Implemented when API key is configured |
| `REDDIT` | Social sentiment | Provider configuration exists; automatic adapter not yet implemented |
| `X` | Social sentiment | Provider configuration exists; automatic adapter not yet implemented |
| `BINANCE_ANNOUNCEMENT` | Exchange announcements | Provider configuration exists; automatic adapter not yet implemented |
| `WHALE_ALERT` | Weighted whale behavior | Implemented by the whale module |
| `MANUAL_NEWS` | Manually submitted news | Implemented through API |
| `MANUAL` | Generic manually submitted score | Implemented through API |

### Default configured weights

```text
CRYPTOPANIC           0.25
NEWS_API              0.18
REDDIT                0.14
X                     0.12
FEAR_GREED            0.15
BINANCE_ANNOUNCEMENT  0.08
WHALE_ALERT           0.20
MANUAL_NEWS           0.03
```

The combined score is normalized by the effective weight of providers that are enabled and have samples. The configured values therefore do not need to sum to exactly `1.00`.

---

## 4. Sentiment collection flow

```text
SentimentCollectionService scheduler
    |
    +--> checks sentiment.enabled
    +--> checks sentiment.scheduler.enabled
    +--> loads sentiment_provider_config rows
    +--> checks provider enabled + next due time
    |
    +--> FEAR_GREED
    |      +--> fetch index
    |      +--> convert 0..100 to -1..+1
    |      +--> save one signal for every configured trading symbol
    |
    +--> CRYPTOPANIC
    |      +--> fetch symbol-specific headlines
    |      +--> SentimentTextAnalyzer analyzes text
    |      +--> save sentiment_signal
    |
    +--> NEWS_API
           +--> fetch symbol-specific articles
           +--> SentimentTextAnalyzer analyzes title + description
           +--> save sentiment_signal
```

Provider collection success/failure is saved through `SentimentProviderConfigService.recordResult(...)`.

---

## 5. Sentiment signal storage

All providers, including whale sentiment, save into the existing `sentiment_signal` table.

Important fields:

```text
symbol
score              -1.00 to +1.00
confidence          0.00 to 1.00
source              provider code
summary
observed_at
```

Duplicate automatic entries are prevented using the combination of symbol, source, observed time, and summary in repository checks.

---

## 6. Text sentiment analysis

`SentimentTextAnalyzer` converts news text into:

```text
score       -1 to +1
confidence   0 to 1
positive match count
negative match count
```

It is used by:

```text
CRYPTOPANIC
NEWS_API
manual text sentiment endpoint
```

Provider-specific numeric signals such as Fear & Greed and whale aggregation bypass text analysis and use `saveProviderScore(...)`.

---

## 7. Provider-level aggregation

For each provider and symbol, `SentimentService.aggregateProvider(...)` processes all signals inside the active window.

### Sample time decay

```text
timeWeight = 1 / (1 + ageMinutes / 240)
```

### Sample weight

```text
sampleWeight = signalConfidence x timeWeight
```

### Provider score

```text
providerScore =
    sum(signalScore x sampleWeight)
    / sum(sampleWeight)
```

### Provider confidence

```text
providerConfidence =
    min(1, sum(signalConfidence x timeWeight) / numberOfSignals)
```

This produces one current score and confidence for each provider.

---

## 8. Final combined sentiment

For every enabled provider containing fresh samples:

```text
effectiveProviderWeight = configuredWeight x providerConfidence
providerContribution    = providerScore x effectiveProviderWeight
```

The final score is:

```text
combinedSentiment =
    sum(providerContribution)
    / sum(effectiveProviderWeight)
```

Range:

```text
-1.00 = strongly bearish
 0.00 = neutral
+1.00 = strongly bullish
```

Labels:

```text
>=  0.35  BULLISH
>=  0.10  SLIGHTLY_BULLISH
>  -0.10  NEUTRAL
>  -0.35  SLIGHTLY_BEARISH
otherwise BEARISH
```

The dashboard breakdown includes:

```text
provider
provider enabled status
configured weight
provider score
provider confidence
effective weight
sample count
latest observed time
```

---

## 9. Whale sentiment flow

Only one new whale-specific table is used: `whale_activity`.

```text
WhaleCollectionScheduler
    |
    v
WhaleApiClient
    |
    v
WhaleTransactionService
    |
    +--> normalize asset/symbol
    +--> identify relevant whale wallet
    +--> classify movement
    +--> calculate transaction score
    +--> calculate transaction confidence
    +--> get price at signal
    +--> create rows for configured horizons
    |
    v
save whale_activity
```

Default evaluation horizons:

```text
1 hour
4 hours
24 hours
```

### Typical movement interpretation

```text
unknown wallet -> exchange      bearish
exchange -> unknown wallet      bullish
mint                            mildly bearish
burn                            mildly bullish
unknown -> unknown              weak/neutral unless other context exists
exchange -> exchange            usually low confidence
```

### Whale evaluation

```text
WhaleEvaluationScheduler every minute
    |
    +--> find PENDING rows where evaluation_due_at <= now
    +--> load current market price
    +--> calculate market return
    +--> CORRECT / INCORRECT / INCONCLUSIVE
    +--> calculate prediction quality
    +--> calculate wallet history statistics from whale_activity
    +--> update learned weight
    +--> update the same whale_activity row
```

Small price moves below the configured threshold are marked `INCONCLUSIVE`.

### Learned whale weight

A new whale starts at:

```text
0.15
```

The target learned weight uses:

```text
accuracy
average prediction quality
sample confidence
prior sample size
minimum and maximum weight
```

The stored weight is smoothed:

```text
newStoredWeight =
    oldWeight x (1 - smoothingFactor)
    + calculatedTargetWeight x smoothingFactor
```

Default smoothing factor:

```text
0.20
```

This prevents unstable jumps after a small number of evaluations.

### Whale aggregation

```text
WhaleAggregationScheduler every 5 minutes
    |
    +--> load recent whale_activity rows for each symbol
    +--> obtain each wallet's latest learned weight
    +--> apply transaction confidence
    +--> apply recency
    +--> calculate weighted whale score
    +--> calculate provider confidence
    +--> save sentiment_signal with source WHALE_ALERT
```

The whale provider is configured with weight:

```text
0.20
```

That means it contributes 20% of the sentiment layer before confidence normalization. It is not 20% of the complete trade score.

---

## 10. Technical indicator calculation

When a candle closes, `TechnicalIndicatorService` calculates and saves:

```text
SMA20
EMA20
EMA50
EMA200
RSI14
MACD
MACD signal
MACD histogram
Bollinger middle
Bollinger upper
Bollinger lower
Bollinger bandwidth
ATR14
Volume SMA20
Relative volume
```

The indicator row is then passed directly into `AnalysisService`; the analysis layer does not recalculate candle history.

---

## 11. Analysis scoring

The total trading analysis is based on five categories.

| Category | Maximum |
|---|---:|
| Trend | 25 |
| Volume | 20 |
| Momentum | 15 |
| Sentiment | 15 |
| Fundamentals | 10 |
| **Total** | **85 raw points** |

When sentiment is enabled, the current implementation uses the raw category total directly. When sentiment is disabled, the remaining 85-point structure is normalized to 100 in the current code path. Review this behavior if the intended enabled maximum should also be normalized from 85 to 100.

### Trend score — 25

```text
latest price > EMA20        +5
EMA20 > EMA50               +8
EMA50 > EMA200              +8
latest price > EMA200       +4
```

### Volume score — 20

```text
RVOL >= 2.0     20
RVOL >= 1.5     16
RVOL >= 1.0     11
RVOL >= 0.7      6
otherwise        2
```

### Momentum score — 15

```text
RSI between 50 and 70       +8
otherwise RSI 40 to 75      +4
MACD > MACD signal          +7
```

### Sentiment score — 15

The combined sentiment range `[-1,+1]` is mapped to `[0,15]`:

```text
sentimentPoints = round((combinedSentiment + 1) x 7.5)
```

Examples:

```text
-1.00 ->  0 points
-0.50 ->  4 points approximately
 0.00 ->  8 points approximately
+0.50 -> 11 points approximately
+1.00 -> 15 points
```

The whale provider affects this 15-point category through the combined sentiment calculation.

### Fundamental score — 10

If no valid market-cap data exists:

```text
neutral default = 5
```

FDV / market-cap ratio:

```text
<= 1.5   +6
<= 3.0   +3
```

24-hour volume / market-cap ratio:

```text
>= 5%    +4
>= 1%    +2
```

Maximum is capped at 10.

---

## 12. Trading decision thresholds

```text
score >= 85   STRONG_BUY
score >= 75   BUY
score >= 60   WATCH
score >= 45   NEUTRAL
otherwise     REJECT
```

The final `trade_signal` saves:

```text
symbol
interval
decision
total score
trend score
volume score
momentum score
sentiment points
fundamental score
latest price
stop loss
take profit
explanation
generated time
```

---

## 13. Risk calculation

### Stop loss

```text
stopLoss = latestPrice - (ATR14 x 1.5)
```

The saved value is never below zero.

### Take profit

```text
riskDistance = latestPrice - stopLoss
takeProfit   = latestPrice + (riskDistance x 2)
```

This creates a default risk/reward ratio of approximately `1:2`.

---

## 14. Automatic versus manual analysis

### Event-driven automatic path

```text
closed candle
    -> technical indicator saved
    -> AnalysisService.analyze(indicator)
    -> trade signal saved
```

### Manual/recovery path

```text
AnalysisController
    -> AnalysisService.analyze(symbol, interval)
    -> load latest saved technical indicator
    -> trade signal saved
```

### Scheduled fallback path

`ScheduledAnalysisService` can periodically trigger analysis for configured symbols and intervals. This should be treated as a fallback/recovery path so it does not create unnecessary duplicate signals immediately after event-driven analysis.

---

## 15. Database structure

Flyway migrations currently include:

```text
V1__initial_schema.sql
V2__create_technical_indicator_table.sql
V3__extend_technical_indicator.sql
V4__add_created_at_to_trade_signal.sql
V5__create_sentiment_provider_table.sql
V6__create_whale_activity.sql
```

Core tables:

```text
candle
technical_indicator
sentiment_signal
sentiment_provider_config
market_fundamental
trade_signal
paper_position
whale_activity
```

### Whale table rule

`whale_activity` is the only new whale-specific table. It contains transaction data, evaluation data, wallet statistics, and the learned weight snapshot.

The aggregated whale result is saved in the existing `sentiment_signal` table using:

```text
source = WHALE_ALERT
```

---

## 16. Scheduler timing

| Scheduler | Default cadence | Purpose |
|---|---:|---|
| Candle collector | 60 seconds | REST/fallback candle collection |
| Sentiment collector | checks every 60 seconds | Runs due enabled sentiment providers |
| Scheduled analysis | 5 minutes | Recovery/manual-style analysis cycle |
| Whale collection | 5 minutes | Fetch new large transactions |
| Whale evaluation | 1 minute | Complete due 1h/4h/24h evaluations |
| Whale aggregation | 5 minutes | Publish latest `WHALE_ALERT` sentiment |
| Binance WebSocket health | 15 seconds | Stream health/reconnect support |

---

## 17. Important configuration switches

```yaml
sentiment:
  enabled: true
  scheduler:
    enabled: true

whale:
  enabled: false

sentiment:
  providers:
    whale_alert:
      enabled: false
      weight: 0.20
```

To enable whale collection, all of these must be available:

```text
WHALE_ENABLED=true
WHALE_ALERT_ENABLED=true
WHALE_ALERT_API_KEY=<key>
```

Manual whale transactions can still be submitted through the whale controller for testing, depending on the endpoint behavior.

---

## 18. Recommended production sequence

```text
1. Enable Fear & Greed and manual sentiment.
2. Validate combined sentiment dashboard output.
3. Enable CryptoPanic and NewsAPI one at a time.
4. Keep whale ingestion in paper-trading mode.
5. Accumulate enough whale evaluations before trusting learned weights.
6. Compare signals with and without WHALE_ALERT.
7. Backtest provider weight changes.
8. Keep WHALE_ALERT at 0.20 until evidence supports changing it.
9. Only enable live order execution after paper-trading and risk controls are validated.
```

---

## 19. Complete decision path example

```text
BTCUSDT 5m candle closes
    |
    +--> indicator row calculated
    |      EMA20 > EMA50
    |      EMA50 > EMA200
    |      RSI = 61
    |      MACD > signal
    |      RVOL = 1.6
    |
    +--> current sentiment requested
    |      FEAR_GREED = -0.20
    |      NEWS_API = +0.25
    |      CRYPTOPANIC = +0.15
    |      WHALE_ALERT = +0.40
    |      provider weights and confidence applied
    |      combined sentiment = +0.18
    |
    +--> latest fundamentals requested
    |
    +--> scores calculated
    |      trend       /25
    |      volume      /20
    |      momentum    /15
    |      sentiment   /15
    |      fundamentals/10
    |
    +--> stop loss and take profit calculated
    +--> decision selected
    +--> trade_signal saved
    +--> PaperTradingService decides whether to open a paper position
```

This document describes the full current sentiment, whale, indicator, analysis, risk, and paper-trading structure contained in the source package.

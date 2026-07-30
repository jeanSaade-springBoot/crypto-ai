# Crypto AI Trader — Spring Boot + MySQL

A runnable MVP for crypto market analysis and paper trading.

## Included

- Binance candle collection
- MySQL persistence with Flyway
- EMA 20/50/200, RSI 14, MACD, ATR and relative volume
- Sentiment and market-fundamental input endpoints
- Weighted 0–100 signal score
- Paper-trade position creation
- Risk-based position sizing
- Daily-loss and maximum-open-position controls
- REST API and scheduled analysis
- Docker Compose for MySQL

> The score is a signal-strength score, not a guaranteed probability of profit. Real order execution is deliberately not included.

## Requirements

- Java 21
- Maven 3.9+
- Docker Desktop, or an existing MySQL 8 server

## Run

```bash
docker compose up -d
mvn spring-boot:run
```

The application starts on:

```text
http://localhost:8080
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

## Typical flow

### 1. Import 1-hour candles from Binance

```bash
curl -X POST "http://localhost:8080/api/market-data/import?symbol=BTCUSDT&interval=1h&limit=500"
```

### 2. Add sentiment

Score must be between -1 and +1.

```bash
curl -X POST "http://localhost:8080/api/sentiment" \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "BTCUSDT",
    "score": 0.65,
    "confidence": 0.90,
    "source": "NEWS",
    "summary": "Positive institutional demand"
  }'
```

### 3. Add market-cap and FDV data

```bash
curl -X POST "http://localhost:8080/api/fundamentals" \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "BTCUSDT",
    "marketCap": 1300000000000,
    "fullyDilutedValuation": 1380000000000,
    "volume24h": 42000000000,
    "circulatingSupply": 19800000,
    "totalSupply": 21000000
  }'
```

### 4. Generate an analysis

```bash
curl -X POST "http://localhost:8080/api/analysis/BTCUSDT?interval=1h"
```

### 5. Open a paper trade from the latest eligible signal

```bash
curl -X POST "http://localhost:8080/api/paper-trades/BTCUSDT"
```

### 6. List positions

```bash
curl "http://localhost:8080/api/paper-trades"
```

## Configuration

Edit `src/main/resources/application.yml`.

Important settings:

- `trading.minimum-buy-score`
- `trading.risk-per-trade-percent`
- `trading.max-daily-loss-percent`
- `trading.max-open-positions`
- `trading.paper-account-balance`
- `trading.symbols`
- `trading.interval`

## Before real trading

1. Backtest on historical data.
2. Include fees and slippage.
3. Run paper trading for several weeks.
4. Keep exchange API withdrawal permissions disabled.
5. Add authentication, audit logging, encryption and an emergency kill switch.

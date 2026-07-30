# Crypto dashboard UI

Run the Spring Boot application and open:

- `http://localhost:8080/dashboard`
- or `http://localhost:8080/`

The dashboard uses the existing Spring MVC/Web dependency and a static HTML UI. It does not require React or Thymeleaf.

## Included views

- Latest price and closed-candle history progress toward the 210-candle minimum
- Event pipeline status
- Candlestick and volume charts
- Latest technical indicators
- Recent `trade_signal` rows
- Recent `paper_position` rows
- Symbol and interval filtering
- Automatic refresh every 10 seconds

The candlestick charts use ApexCharts from jsDelivr, so the browser needs internet access to load the chart library. All trading data is read locally from your Spring Boot API.

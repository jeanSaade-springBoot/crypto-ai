package com.crypto.service;

import com.crypto.client.config.binance.BinanceMarketDataProperties;
import com.crypto.config.SentimentProperties;
import com.crypto.config.TradingProperties;
import com.crypto.whale.config.WhaleProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScheduleConfigurationService {

    private final BinanceMarketDataProperties binanceProperties;
    private final TradingProperties tradingProperties;
    private final SentimentProperties sentimentProperties;
    private final WhaleProperties whaleProperties;
    private final boolean candleCollectorEnabled;
    private final long candleCollectorDelayMs;
    private final long dashboardRefreshMs;

    public ScheduleConfigurationService(
            BinanceMarketDataProperties binanceProperties,
            TradingProperties tradingProperties,
            SentimentProperties sentimentProperties,
            WhaleProperties whaleProperties,
            @Value("${collector.candle.enabled:true}") boolean candleCollectorEnabled,
            @Value("${collector.candle.fixed-delay-ms:60000}") long candleCollectorDelayMs,
            @Value("${dashboard.refresh-ms:10000}") long dashboardRefreshMs
    ) {
        this.binanceProperties = binanceProperties;
        this.tradingProperties = tradingProperties;
        this.sentimentProperties = sentimentProperties;
        this.whaleProperties = whaleProperties;
        this.candleCollectorEnabled = candleCollectorEnabled;
        this.candleCollectorDelayMs = candleCollectorDelayMs;
        this.dashboardRefreshMs = dashboardRefreshMs;
    }

    public Map<String, Object> dashboardSchedule() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("dashboardRefreshMs", dashboardRefreshMs);
        response.put("groups", List.of(
                marketDataSchedules(),
                analysisSchedules(),
                sentimentSchedules(),
                whaleSchedules()
        ));
        return response;
    }

    private Map<String, Object> marketDataSchedules() {
        List<Map<String, Object>> entries = new ArrayList<>();
        entries.add(entry(
                "Historical bootstrap",
                binanceProperties.getHistorical().isEnabled(),
                "On application startup",
                null,
                "Loads up to " + binanceProperties.getHistorical().getLimit() + " candles for each configured symbol and interval."
        ));
        entries.add(entry(
                "Binance WebSocket streams",
                binanceProperties.isEnabled() && binanceProperties.getWebsocket().isEnabled(),
                "Continuous",
                null,
                "Streams " + String.join(", ", binanceProperties.getIntervals())
                        + "; reconnect delay " + formatSeconds(binanceProperties.getWebsocket().getReconnectDelaySeconds())
                        + "; health check " + formatSeconds(binanceProperties.getWebsocket().getHealthCheckSeconds()) + "."
        ));
        entries.add(entry(
                "Candle fallback collector",
                candleCollectorEnabled,
                formatDelay(candleCollectorDelayMs),
                candleCollectorDelayMs,
                "Configured REST polling/fallback delay for candle collection."
        ));
        return group("Market data", entries);
    }

    private Map<String, Object> analysisSchedules() {
        List<Map<String, Object>> entries = new ArrayList<>();
        entries.add(entry(
                "Closed-candle analysis",
                true,
                "Event driven",
                null,
                "Runs immediately after a closed candle produces and saves technical indicators."
        ));
        entries.add(entry(
                "Scheduled analysis fallback",
                tradingProperties.scheduledAnalysisEnabled(),
                formatDelay(tradingProperties.analysisDelayMs()),
                tradingProperties.analysisDelayMs(),
                "Re-runs analysis for configured symbols and intervals as a recovery/fallback job."
        ));
        entries.add(entry(
                "Dashboard refresh",
                true,
                formatDelay(dashboardRefreshMs),
                dashboardRefreshMs,
                "Browser refresh interval for dashboard data."
        ));
        return group("Analysis and dashboard", entries);
    }

    private Map<String, Object> sentimentSchedules() {
        List<Map<String, Object>> entries = new ArrayList<>();
        entries.add(entry(
                "Sentiment scheduler scan",
                sentimentProperties.enabled() && sentimentProperties.scheduler().enabled(),
                formatDelay(sentimentProperties.scheduler().fixedDelayMs()),
                sentimentProperties.scheduler().fixedDelayMs(),
                "Checks which enabled provider is due for collection. Active window: "
                        + formatDuration(sentimentProperties.activeWindow().toMillis()) + "."
        ));
        sentimentProperties.providers().forEach((name, provider) -> entries.add(entry(
                "Provider: " + name.toUpperCase(),
                sentimentProperties.enabled() && provider.enabled(),
                "Interval stored in sentiment_provider_config",
                null,
                "Configured weight " + provider.weight() + "; provider intervals can be changed in the sentiment table below."
        )));
        return group("Sentiment", entries);
    }

    private Map<String, Object> whaleSchedules() {
        List<Map<String, Object>> entries = new ArrayList<>();
        entries.add(entry(
                "Whale transaction collection",
                whaleProperties.enabled(),
                formatDelay(whaleProperties.collection().fixedDelayMs()),
                whaleProperties.collection().fixedDelayMs(),
                "Collects transactions above $" + whaleProperties.collection().minimumUsdValue()
                        + "; maximum " + whaleProperties.collection().limit() + " records per run."
        ));
        entries.add(entry(
                "Whale signal evaluation",
                whaleProperties.enabled(),
                formatDelay(whaleProperties.evaluation().fixedDelayMs()),
                whaleProperties.evaluation().fixedDelayMs(),
                "Evaluates due signals using " + whaleProperties.evaluation().priceInterval()
                        + " prices at horizons " + String.join(", ", whaleProperties.evaluation().horizons()) + "."
        ));
        entries.add(entry(
                "Whale sentiment aggregation",
                whaleProperties.enabled(),
                formatDelay(whaleProperties.aggregation().fixedDelayMs()),
                whaleProperties.aggregation().fixedDelayMs(),
                "Builds the WHALE_ALERT provider score from the last "
                        + formatDuration(whaleProperties.aggregation().activeWindow().toMillis())
                        + " using horizon " + whaleProperties.aggregation().horizon() + "."
        ));
        return group("Whale monitoring", entries);
    }

    private Map<String, Object> group(String name, List<Map<String, Object>> entries) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("name", name);
        group.put("entries", entries);
        return group;
    }

    private Map<String, Object> entry(
            String name,
            boolean enabled,
            String cadence,
            Long delayMs,
            String detail
    ) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("enabled", enabled);
        entry.put("cadence", cadence);
        entry.put("delayMs", delayMs);
        entry.put("detail", detail);
        return entry;
    }

    private String formatDelay(long milliseconds) {
        return "Every " + formatDuration(milliseconds);
    }

    private String formatSeconds(long seconds) {
        return formatDuration(seconds * 1000L);
    }

    private String formatDuration(long milliseconds) {
        if (milliseconds % 86_400_000L == 0) {
            return (milliseconds / 86_400_000L) + " day(s)";
        }
        if (milliseconds % 3_600_000L == 0) {
            return (milliseconds / 3_600_000L) + " hour(s)";
        }
        if (milliseconds % 60_000L == 0) {
            return (milliseconds / 60_000L) + " minute(s)";
        }
        if (milliseconds % 1000L == 0) {
            return (milliseconds / 1000L) + " second(s)";
        }
        return milliseconds + " ms";
    }
}

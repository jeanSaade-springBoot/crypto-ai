package com.crypto.client.config.binance;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "binance.market-data")
public class BinanceMarketDataProperties {

    private boolean enabled = true;
    private String baseUrl = "https://data-api.binance.vision";
    private String websocketBaseUrl = "wss://data-stream.binance.vision:443";
    private int defaultLimit = 500;
    private int requestTimeoutSeconds = 30;
    private List<String> symbols = new ArrayList<>();
    private List<String> intervals = new ArrayList<>();
    private Historical historical = new Historical();
    private Websocket websocket = new Websocket();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getWebsocketBaseUrl() { return websocketBaseUrl; }
    public void setWebsocketBaseUrl(String websocketBaseUrl) { this.websocketBaseUrl = websocketBaseUrl; }
    public int getDefaultLimit() { return defaultLimit; }
    public void setDefaultLimit(int defaultLimit) { this.defaultLimit = defaultLimit; }
    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) { this.requestTimeoutSeconds = requestTimeoutSeconds; }
    public List<String> getSymbols() { return symbols; }
    public void setSymbols(List<String> symbols) { this.symbols = symbols; }
    public List<String> getIntervals() { return intervals; }
    public void setIntervals(List<String> intervals) { this.intervals = intervals; }
    public Historical getHistorical() { return historical; }
    public void setHistorical(Historical historical) { this.historical = historical; }
    public Websocket getWebsocket() { return websocket; }
    public void setWebsocket(Websocket websocket) { this.websocket = websocket; }

    public static class Historical {
        private boolean enabled = true;
        private int limit = 1000;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }
    }

    public static class Websocket {
        private boolean enabled = true;
        private long reconnectDelaySeconds = 10;
        private long healthCheckSeconds = 15;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getReconnectDelaySeconds() { return reconnectDelaySeconds; }
        public void setReconnectDelaySeconds(long reconnectDelaySeconds) { this.reconnectDelaySeconds = reconnectDelaySeconds; }
        public long getHealthCheckSeconds() { return healthCheckSeconds; }
        public void setHealthCheckSeconds(long healthCheckSeconds) { this.healthCheckSeconds = healthCheckSeconds; }
    }
}

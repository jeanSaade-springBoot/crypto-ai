package com.crypto.service;

import com.crypto.config.SentimentProperties;
import com.crypto.config.TradingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SentimentCollectionService {

    private final SentimentProperties properties;
    private final TradingProperties tradingProperties;
    private final SentimentService sentimentService;
    private final SentimentProviderConfigService providerConfigService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Scheduled(fixedDelayString = "${sentiment.scheduler.fixed-delay-ms:60000}")
    public void collectScheduled() {
        if (!properties.enabled() || !properties.scheduler().enabled()) return;
        Instant now = Instant.now();
        for (com.crypto.domain.SentimentProviderConfig provider : providerConfigService.findAll()) {
            if (provider.isDue(now) && !"MANUAL_NEWS".equals(provider.getProviderCode())) {
                collectProvider(provider.getProviderCode());
            }
        }
    }

    public Map<String, String> collectAll() {
        Map<String, String> result = new LinkedHashMap<>();
        if (!properties.enabled()) {
            result.put("SENTIMENT", "disabled by sentiment.enabled=false");
            return result;
        }
        for (com.crypto.domain.SentimentProviderConfig provider : providerConfigService.findAll()) {
            if (!provider.isEnabled() || "MANUAL_NEWS".equals(provider.getProviderCode())) continue;
            result.putAll(collectProvider(provider.getProviderCode()));
        }
        return result;
    }

    public Map<String, String> collectProvider(String providerCode) {
        String provider = SentimentProviderName.normalize(providerCode);
        Map<String, String> status = new LinkedHashMap<>();
        if (!properties.enabled()) {
            status.put(provider, "sentiment disabled");
            return status;
        }
        com.crypto.domain.SentimentProviderConfig config = providerConfigService.require(provider);
        if (!config.isEnabled()) {
            status.put(provider, "disabled");
            return status;
        }
        switch (provider) {
            case "FEAR_GREED" -> collectFearGreed(status);
            case "CRYPTOPANIC" -> collectCryptoPanic(status);
            case "NEWS_API" -> collectNewsApi(status);
            case "REDDIT", "X", "BINANCE_ANNOUNCEMENT", "WHALE_ALERT" -> status.put(provider, configuredOnly(provider.toLowerCase()));
            default -> status.put(provider, "no automatic collector");
        }
        String message = status.getOrDefault(provider, "completed");
        boolean success = !message.startsWith("failed") && !message.contains("missing") && !message.contains("not configured");
        String state = success ? "SUCCESS" : (message.contains("missing") ? "MISSING_KEY" : message.contains("not configured") ? "NOT_IMPLEMENTED" : "FAILED");
        providerConfigService.recordResult(provider, state, message, success);
        return status;
    }

    private void collectFearGreed(Map<String, String> status) {
        SentimentProperties.Provider config = properties.provider("fear_greed");
        if (!providerConfigService.enabled("FEAR_GREED")) { status.put("FEAR_GREED", "disabled"); return; }
        try {
            String base = blankToDefault(config.baseUrl(), "https://api.alternative.me");
            JsonNode root = getJson(base + "/fng/?limit=1&format=json", null);
            JsonNode item = root.path("data").path(0);
            int value = item.path("value").asInt(50);
            BigDecimal score = BigDecimal.valueOf((value - 50) / 50.0)
                    .max(BigDecimal.valueOf(-1)).min(BigDecimal.ONE);
            String classification = item.path("value_classification").asText("Unknown");
            Instant observedAt = item.hasNonNull("timestamp")
                    ? Instant.ofEpochSecond(item.path("timestamp").asLong())
                    : Instant.now();

            for (String symbol : symbols()) {
                sentimentService.saveProviderScore(
                        symbol,
                        "FEAR_GREED",
                        score,
                        BigDecimal.valueOf(0.90),
                        "Fear & Greed=" + value + " (" + classification + ")",
                        observedAt
                );
            }
            status.put("FEAR_GREED", "collected for " + symbols().size() + " symbols");
        } catch (Exception ex) {
            log.warn("Fear & Greed collection failed: {}", ex.getMessage());
            status.put("FEAR_GREED", "failed: " + ex.getMessage());
        }
    }

    private void collectCryptoPanic(Map<String, String> status) {
        SentimentProperties.Provider config = properties.provider("cryptopanic");
        if (!providerConfigService.enabled("CRYPTOPANIC")) { status.put("CRYPTOPANIC", "disabled"); return; }
        if (isBlank(config.apiKey())) {
            status.put("CRYPTOPANIC", "enabled but CRYPTOPANIC_API_KEY is missing");
            return;
        }
        try {
            String base = blankToDefault(config.baseUrl(), "https://cryptopanic.com/api/developer/v2");
            int saved = 0;
            for (String symbol : symbols()) {
                String currency = baseAsset(symbol);
                String url = base + "/posts/?auth_token=" + encode(config.apiKey())
                        + "&currencies=" + encode(currency)
                        + "&public=true";
                JsonNode results = getJson(url, null).path("results");
                if (!results.isArray()) continue;
                int limit = 0;
                for (JsonNode article : results) {
                    if (limit++ >= 10) break;
                    String title = article.path("title").asText("");
                    if (title.isBlank()) continue;
                    Instant published = parseInstant(article.path("published_at").asText(null));
                    sentimentService.analyzeAndSave(symbol, "CRYPTOPANIC", title, published);
                    saved++;
                }
            }
            status.put("CRYPTOPANIC", "saved " + saved + " headlines");
        } catch (Exception ex) {
            log.warn("CryptoPanic collection failed: {}", ex.getMessage());
            status.put("CRYPTOPANIC", "failed: " + ex.getMessage());
        }
    }

    private void collectNewsApi(Map<String, String> status) {
        SentimentProperties.Provider config = properties.provider("news_api");
        if (!providerConfigService.enabled("NEWS_API")) { status.put("NEWS_API", "disabled"); return; }
        if (isBlank(config.apiKey())) {
            status.put("NEWS_API", "enabled but NEWS_API_KEY is missing");
            return;
        }
        try {
            String base = blankToDefault(config.baseUrl(), "https://newsapi.org/v2");
            int saved = 0;
            for (String symbol : symbols()) {
                String query = assetQuery(symbol);
                String url = base + "/everything?q=" + encode(query)
                        + "&language=en&sortBy=publishedAt&pageSize=10";
                JsonNode articles = getJson(url, config.apiKey()).path("articles");
                if (!articles.isArray()) continue;
                for (JsonNode article : articles) {
                    String title = article.path("title").asText("");
                    String description = article.path("description").asText("");
                    String text = (title + ". " + description).trim();
                    if (text.isBlank()) continue;
                    Instant published = parseInstant(article.path("publishedAt").asText(null));
                    sentimentService.analyzeAndSave(symbol, "NEWS_API", text, published);
                    saved++;
                }
            }
            status.put("NEWS_API", "saved " + saved + " articles");
        } catch (Exception ex) {
            log.warn("NewsAPI collection failed: {}", ex.getMessage());
            status.put("NEWS_API", "failed: " + ex.getMessage());
        }
    }

    private JsonNode getJson(String url, String apiKeyHeader) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", "crypto-ai-trader/1.0")
                .GET();
        if (!isBlank(apiKeyHeader)) builder.header("X-Api-Key", apiKeyHeader);
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private String configuredOnly(String provider) {
        return providerConfigService.enabled(provider)
                ? "enabled but automatic adapter is not configured yet"
                : "disabled";
    }

    private List<String> symbols() {
        return tradingProperties.symbols() == null || tradingProperties.symbols().isEmpty()
                ? List.of("BTCUSDT")
                : tradingProperties.symbols();
    }

    private String baseAsset(String symbol) {
        String upper = symbol.toUpperCase(Locale.ROOT);
        for (String quote : List.of("USDT", "USDC", "BUSD", "USD", "BTC", "ETH")) {
            if (upper.endsWith(quote) && upper.length() > quote.length()) {
                return upper.substring(0, upper.length() - quote.length());
            }
        }
        return upper;
    }

    private String assetQuery(String symbol) {
        return switch (baseAsset(symbol)) {
            case "BTC" -> "Bitcoin OR BTC";
            case "ETH" -> "Ethereum OR ETH";
            case "BNB" -> "BNB OR Binance Coin";
            case "SOL" -> "Solana OR SOL";
            default -> baseAsset(symbol) + " cryptocurrency";
        };
    }

    private Instant parseInstant(String value) {
        try {
            return value == null || value.isBlank() ? Instant.now() : Instant.parse(value);
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String blankToDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

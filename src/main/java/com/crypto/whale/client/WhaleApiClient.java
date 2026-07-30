package com.crypto.whale.client;

import com.crypto.whale.config.WhaleProperties;
import com.crypto.whale.dto.WhaleTransactionInput;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class WhaleApiClient {
    private final RestClient restClient;
    private final WhaleProperties properties;

    public WhaleApiClient(RestClient.Builder builder, WhaleProperties properties) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.api().baseUrl()).build();
    }

    public List<WhaleTransactionInput> findRecentTransactions(Instant start) {
        if (properties.api().apiKey() == null || properties.api().apiKey().isBlank()) return List.of();
        JsonNode root = restClient.get().uri(uriBuilder -> uriBuilder
                        .path(properties.api().transactionsPath())
                        .queryParam("api_key", properties.api().apiKey())
                        .queryParam("start", start.getEpochSecond())
                        .queryParam("min_value", properties.collection().minimumUsdValue())
                        .queryParam("limit", properties.collection().limit())
                        .build())
                .retrieve().body(JsonNode.class);
        if (root == null) return List.of();
        JsonNode transactions = root.path("transactions");
        if (!transactions.isArray()) transactions = root.path("data");
        if (!transactions.isArray()) return List.of();

        List<WhaleTransactionInput> result = new ArrayList<>();
        for (JsonNode node : transactions) {
            BigDecimal usd = decimal(node, "amount_usd", "usd_value", "value_usd");
            if (usd == null || usd.compareTo(properties.collection().minimumUsdValue()) < 0) continue;
            String hash = text(node, "hash", "transaction_hash", "id");
            String blockchain = text(node, "blockchain", "chain");
            String asset = text(node, "symbol", "asset", "currency");
            JsonNode from = node.path("from");
            JsonNode to = node.path("to");
            String fromAddress = objectText(from, "address", "wallet") != null ? objectText(from, "address", "wallet") : text(node, "from_address");
            String toAddress = objectText(to, "address", "wallet") != null ? objectText(to, "address", "wallet") : text(node, "to_address");
            String fromLabel = objectText(from, "owner", "label", "name");
            String toLabel = objectText(to, "owner", "label", "name");
            BigDecimal amount = decimal(node, "amount", "quantity");
            long timestamp = longValue(node, "timestamp", "time");
            Instant observedAt = timestamp > 0 ? Instant.ofEpochSecond(timestamp) : Instant.now();
            if (hash != null && asset != null && (fromAddress != null || toAddress != null)) {
                result.add(new WhaleTransactionInput(blockchain == null ? "UNKNOWN" : blockchain, hash,
                        fromAddress, toAddress, fromLabel, toLabel, asset, amount, usd, observedAt));
            }
        }
        return result;
    }

    private String text(JsonNode node, String... names) {
        for (String name : names) if (node.hasNonNull(name) && !node.get(name).asText().isBlank()) return node.get(name).asText();
        return null;
    }
    private String objectText(JsonNode node, String... names) { return node == null || node.isMissingNode() ? null : text(node, names); }
    private BigDecimal decimal(JsonNode node, String... names) {
        String value = text(node, names); if (value == null) return null;
        try { return new BigDecimal(value); } catch (NumberFormatException ex) { return null; }
    }
    private long longValue(JsonNode node, String... names) {
        String value = text(node, names); if (value == null) return 0;
        try { return Long.parseLong(value); } catch (NumberFormatException ex) { return 0; }
    }
}

package com.crypto.client.binance.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BinanceOrderBook(
        @JsonProperty("lastUpdateId") long lastUpdateId,
        List<List<String>> bids,
        List<List<String>> asks
) {
}

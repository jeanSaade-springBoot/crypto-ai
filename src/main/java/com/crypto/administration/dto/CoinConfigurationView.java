package com.crypto.administration.dto;

public record CoinConfigurationView(Long id, String symbol, boolean enabled, boolean systemDefault, boolean removable) {
}

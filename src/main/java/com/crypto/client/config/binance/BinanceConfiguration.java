package com.crypto.client.config.binance;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BinanceMarketDataProperties.class)
public class BinanceConfiguration {
}
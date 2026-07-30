package com.crypto;

import com.crypto.config.TradingProperties;
import com.crypto.config.SentimentProperties;
import com.crypto.whale.config.WhaleProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({TradingProperties.class, SentimentProperties.class, WhaleProperties.class})
public class CryptoAiTraderApplication {

    public static void main(String[] args) {
        SpringApplication.run(CryptoAiTraderApplication.class, args);
    }
}

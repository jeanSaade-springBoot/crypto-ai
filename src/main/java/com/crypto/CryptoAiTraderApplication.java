package com.crypto;

import com.crypto.config.TradingProperties;
import com.crypto.config.AtrRiskProperties;
import com.crypto.config.AnalysisScoringProperties;
import com.crypto.config.BtcContextProperties;
import com.crypto.config.OrderBookProperties;
import com.crypto.config.DynamicStrategyProperties;
import com.crypto.config.DerivativesPositioningProperties;
import com.crypto.config.SentimentProperties;
import com.crypto.config.FundamentalCollectionProperties;
import com.crypto.whale.config.WhaleProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties({TradingProperties.class, AtrRiskProperties.class, SentimentProperties.class, WhaleProperties.class, AnalysisScoringProperties.class, BtcContextProperties.class, OrderBookProperties.class, DynamicStrategyProperties.class, DerivativesPositioningProperties.class, FundamentalCollectionProperties.class})
public class CryptoAiTraderApplication {

    public static void main(String[] args) {
        // FIX-132: explicit maintenance process, not a runtime toggle. The isolated
        // context has no component scan, wallet, WebSocket, scheduler or consumer.
        Class<?> application=java.util.Arrays.asList(args).contains("--fix132-approval-console=true")
            ? com.fix132.approval.CutoverApprovalApplication.class : CryptoAiTraderApplication.class;
        SpringApplication.run(application, args);
        
    }
}

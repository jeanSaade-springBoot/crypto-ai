package com.crypto.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "analysis.dynamic-strategy")
public class DynamicStrategyProperties {

    private boolean enabled = true;
    private String version = "1.0";
    private int minimumRegimeConfidence = 55;
    private Profile trendFollowing = new Profile(30, 20, 15, 10, 10, 85, 75, 60, 45, 30);
    private Profile rangeMeanReversion = new Profile(15, 18, 22, 15, 15, 88, 78, 65, 45, 30);
    private Profile breakout = new Profile(20, 30, 20, 10, 5, 90, 80, 65, 45, 30);
    private Profile defensive = new Profile(25, 20, 15, 10, 15, 90, 82, 68, 50, 35);

    @Getter
    @Setter
    public static class Profile {
        private int trendMaximum;
        private int volumeMaximum;
        private int momentumMaximum;
        private int sentimentMaximum;
        private int fundamentalMaximum;
        private int strongBuyThreshold;
        private int buyThreshold;
        private int watchThreshold;
        private int neutralThreshold;
        private int sellThreshold;

        public Profile() {}

        public Profile(int trendMaximum, int volumeMaximum, int momentumMaximum,
                       int sentimentMaximum, int fundamentalMaximum,
                       int strongBuyThreshold, int buyThreshold, int watchThreshold,
                       int neutralThreshold, int sellThreshold) {
            this.trendMaximum = trendMaximum;
            this.volumeMaximum = volumeMaximum;
            this.momentumMaximum = momentumMaximum;
            this.sentimentMaximum = sentimentMaximum;
            this.fundamentalMaximum = fundamentalMaximum;
            this.strongBuyThreshold = strongBuyThreshold;
            this.buyThreshold = buyThreshold;
            this.watchThreshold = watchThreshold;
            this.neutralThreshold = neutralThreshold;
            this.sellThreshold = sellThreshold;
        }
    }
}

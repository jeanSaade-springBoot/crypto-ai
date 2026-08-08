package com.crypto.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.wallet.domain.WalletSettings;
import com.crypto.wallet.repository.WalletSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeExecutionValidationServiceTest {

    @Mock private TradeSignalRepository signalRepository;
    @Mock private WalletSettingsRepository settingsRepository;

    private TradeExecutionValidationService service;
    private Instant now;

    @BeforeEach
    void setUp() {
        service = new TradeExecutionValidationService(signalRepository, settingsRepository);
        now = Instant.parse("2026-08-08T05:00:00Z");
    }

    @Test
    void buyMustComeFromOneMinuteExecutionFrame() {
        settings("BALANCED", true);
        var result = service.validateBuy(signal("BTCUSDT", "5m", SignalDecision.BUY, now, 80, 23, 18, 13));
        assertThat(result.allowed()).isFalse();
        assertThat(result.code()).isEqualTo("NON_EXECUTION_TIMEFRAME");
    }

    @Test
    void conservativeRequiresBullishFiveMinute() {
        settings("CONSERVATIVE", false);
        TradeSignal oneMinute = signal("BTCUSDT", "1m", SignalDecision.BUY, now, 80, 23, 18, 13);
        latest("BTCUSDT", "5m", signal("BTCUSDT", "5m", SignalDecision.WATCH, now.minusSeconds(120), 70, 20, 15, 10));
        latest("BTCUSDT", "1h", signal("BTCUSDT", "1h", SignalDecision.BUY, now.minusSeconds(1800), 80, 23, 18, 13));
        var result = service.validateBuy(oneMinute);
        assertThat(result.allowed()).isFalse();
        assertThat(result.code()).isEqualTo("5M_NOT_BULLISH");
    }

    @Test
    void balancedAllowsBullishFiveMinuteWithNeutralOneHourAtSeventyFivePercent() {
        settings("BALANCED", false);
        TradeSignal oneMinute = signal("BTCUSDT", "1m", SignalDecision.BUY, now, 80, 23, 18, 13);
        latest("BTCUSDT", "5m", signal("BTCUSDT", "5m", SignalDecision.BUY, now.minusSeconds(120), 80, 23, 18, 13));
        latest("BTCUSDT", "1h", signal("BTCUSDT", "1h", SignalDecision.NEUTRAL, now.minusSeconds(1800), 55, 15, 10, 8));
        var result = service.validateBuy(oneMinute);
        assertThat(result.allowed()).isTrue();
        assertThat(result.positionPercent()).isEqualTo(75);
    }

    @Test
    void balancedAllowsWatchWatchAtFiftyPercent() {
        settings("BALANCED", false);
        TradeSignal oneMinute = signal("BNBUSDT", "1m", SignalDecision.BUY, now, 79, 22, 17, 13);
        latest("BNBUSDT", "5m", signal("BNBUSDT", "5m", SignalDecision.WATCH, now.minusSeconds(120), 68, 18, 13, 10));
        latest("BNBUSDT", "1h", signal("BNBUSDT", "1h", SignalDecision.WATCH, now.minusSeconds(1800), 69, 19, 14, 10));
        var result = service.validateBuy(oneMinute);
        assertThat(result.allowed()).isTrue();
        assertThat(result.positionPercent()).isEqualTo(50);
    }

    @Test
    void aggressiveAllowsQualifiedNeutralOneHourProbe() {
        settings("AGGRESSIVE", false);
        TradeSignal oneMinute = signal("SOLUSDT", "1m", SignalDecision.BUY, now, 88, 23, 17, 13);
        latest("SOLUSDT", "5m", signal("SOLUSDT", "5m", SignalDecision.WATCH, now.minusSeconds(120), 68, 18, 13, 10));
        latest("SOLUSDT", "1h", signal("SOLUSDT", "1h", SignalDecision.NEUTRAL, now.minusSeconds(1800), 55, 15, 10, 8));
        var result = service.validateBuy(oneMinute);
        assertThat(result.allowed()).isTrue();
        assertThat(result.positionPercent()).isEqualTo(25);
        assertThat(result.code()).isEqualTo("AGGRESSIVE_PROBE");
    }

    @Test
    void aggressiveProbeStillRequiresHighQualityOneMinuteSignal() {
        settings("AGGRESSIVE", false);
        TradeSignal oneMinute = signal("SOLUSDT", "1m", SignalDecision.BUY, now, 80, 20, 12, 10);
        latest("SOLUSDT", "5m", signal("SOLUSDT", "5m", SignalDecision.WATCH, now.minusSeconds(120), 68, 18, 13, 10));
        latest("SOLUSDT", "1h", signal("SOLUSDT", "1h", SignalDecision.NEUTRAL, now.minusSeconds(1800), 55, 15, 10, 8));
        var result = service.validateBuy(oneMinute);
        assertThat(result.allowed()).isFalse();
        assertThat(result.code()).isEqualTo("AGGRESSIVE_PROBE_QUALITY");
    }

    @Test
    void bearishOneHourAlwaysVetoesBuy() {
        settings("AGGRESSIVE", false);
        TradeSignal oneMinute = signal("BTCUSDT", "1m", SignalDecision.BUY, now, 90, 25, 20, 15);
        latest("BTCUSDT", "5m", signal("BTCUSDT", "5m", SignalDecision.STRONG_BUY, now.minusSeconds(120), 90, 25, 20, 15));
        latest("BTCUSDT", "1h", signal("BTCUSDT", "1h", SignalDecision.SELL, now.minusSeconds(1800), 35, 8, 6, 5));
        var result = service.validateBuy(oneMinute);
        assertThat(result.allowed()).isFalse();
        assertThat(result.code()).isEqualTo("1H_BEARISH_VETO");
    }

    @Test
    void repeatedOneMinuteBuyIsNotANewOpportunity() {
        settings("BALANCED", true);
        TradeSignal oneMinute = signal("ETHUSDT", "1m", SignalDecision.BUY, now, 80, 23, 18, 13);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanOrderByGeneratedAtDesc(
                "ETHUSDT", "1m", now)).thenReturn(Optional.of(
                signal("ETHUSDT", "1m", SignalDecision.BUY, now.minusSeconds(60), 79, 22, 17, 13)));
        var result = service.validateBuy(oneMinute);
        assertThat(result.allowed()).isFalse();
        assertThat(result.code()).isEqualTo("BUY_CONTINUATION");
    }

    @Test
    void sellKeepsExistingStrictConfirmationRules() {
        settings("AGGRESSIVE", false);
        TradeSignal oneMinute = signal("ETHUSDT", "1m", SignalDecision.SELL, now, 30, 15, 5, 4);
        latest("ETHUSDT", "5m", signal("ETHUSDT", "5m", SignalDecision.SELL, now.minusSeconds(120), 35, 10, 7, 5));
        latest("ETHUSDT", "1h", signal("ETHUSDT", "1h", SignalDecision.WATCH, now.minusSeconds(1800), 65, 18, 12, 10));
        var result = service.validateSell(oneMinute);
        assertThat(result.allowed()).isTrue();
        assertThat(result.positionPercent()).isEqualTo(100);
    }

    private void settings(String profile, boolean requireTransition) {
        when(settingsRepository.findById(1L)).thenReturn(Optional.of(
                WalletSettings.builder().id(1L).executionProfile(profile)
                        .requireNewBuyTransition(requireTransition).build()));
    }

    private void latest(String symbol, String interval, TradeSignal value) {
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                symbol, interval, now)).thenReturn(Optional.of(value));
    }

    private TradeSignal signal(String symbol, String interval, SignalDecision decision, Instant generatedAt,
                               int confidence, int trend, int volume, int momentum) {
        return TradeSignal.builder()
                .id(Math.abs((symbol + interval + generatedAt).hashCode()) + 1L)
                .symbol(symbol)
                .interval(interval)
                .decision(decision)
                .confidenceScore(confidence)
                .trendScore(trend)
                .volumeScore(volume)
                .momentumScore(momentum)
                .generatedAt(generatedAt)
                .build();
    }
}

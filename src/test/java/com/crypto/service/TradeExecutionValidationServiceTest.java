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

    @Mock
    private TradeSignalRepository signalRepository;
    @Mock
    private WalletSettingsRepository settingsRepository;

    private TradeExecutionValidationService service;
    private Instant now;

    @BeforeEach
    void setUp() {
        service = new TradeExecutionValidationService(signalRepository, settingsRepository);
        now = Instant.parse("2026-08-07T09:00:00Z");
        when(settingsRepository.findById(1L)).thenReturn(Optional.of(
                WalletSettings.builder().id(1L).requireNewBuyTransition(true).build()));
    }

    @Test
    void buyMustComeFromOneMinuteExecutionFrame() {
        TradeSignal signal = signal("BTCUSDT", "5m", SignalDecision.BUY, now);

        var result = service.validateBuy(signal);

        assertThat(result.allowed()).isFalse();
        assertThat(result.code()).isEqualTo("NON_EXECUTION_TIMEFRAME");
    }

    @Test
    void buyRequiresFreshBullishFiveMinuteConfirmation() {
        TradeSignal oneMinute = signal("BTCUSDT", "1m", SignalDecision.BUY, now);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanOrderByGeneratedAtDesc(
                "BTCUSDT", "1m", now)).thenReturn(Optional.of(signal("BTCUSDT", "1m", SignalDecision.WATCH, now.minusSeconds(60))));
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "BTCUSDT", "5m", now)).thenReturn(Optional.of(signal("BTCUSDT", "5m", SignalDecision.WATCH, now.minusSeconds(120))));

        var result = service.validateBuy(oneMinute);

        assertThat(result.allowed()).isFalse();
        assertThat(result.code()).isEqualTo("5M_NOT_BULLISH");
    }

    @Test
    void neutralOneHourContextDoesNotBlockConfirmedBuy() {
        TradeSignal oneMinute = signal("BTCUSDT", "1m", SignalDecision.BUY, now);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanOrderByGeneratedAtDesc(
                "BTCUSDT", "1m", now)).thenReturn(Optional.of(signal("BTCUSDT", "1m", SignalDecision.WATCH, now.minusSeconds(60))));
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "BTCUSDT", "5m", now)).thenReturn(Optional.of(signal("BTCUSDT", "5m", SignalDecision.BUY, now.minusSeconds(120))));
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "BTCUSDT", "1h", now)).thenReturn(Optional.of(signal("BTCUSDT", "1h", SignalDecision.NEUTRAL, now.minusSeconds(1800))));

        var result = service.validateBuy(oneMinute);

        assertThat(result.allowed()).isTrue();
    }

    @Test
    void bearishOneHourContextVetoesBuy() {
        TradeSignal oneMinute = signal("BTCUSDT", "1m", SignalDecision.BUY, now);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanOrderByGeneratedAtDesc(
                "BTCUSDT", "1m", now)).thenReturn(Optional.of(signal("BTCUSDT", "1m", SignalDecision.WATCH, now.minusSeconds(60))));
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "BTCUSDT", "5m", now)).thenReturn(Optional.of(signal("BTCUSDT", "5m", SignalDecision.STRONG_BUY, now.minusSeconds(120))));
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "BTCUSDT", "1h", now)).thenReturn(Optional.of(signal("BTCUSDT", "1h", SignalDecision.SELL, now.minusSeconds(1800))));

        var result = service.validateBuy(oneMinute);

        assertThat(result.allowed()).isFalse();
        assertThat(result.code()).isEqualTo("1H_BEARISH_VETO");
    }

    @Test
    void repeatedOneMinuteBuyIsNotAnewOpportunity() {
        TradeSignal oneMinute = signal("ETHUSDT", "1m", SignalDecision.BUY, now);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanOrderByGeneratedAtDesc(
                "ETHUSDT", "1m", now)).thenReturn(Optional.of(signal("ETHUSDT", "1m", SignalDecision.BUY, now.minusSeconds(60))));

        var result = service.validateBuy(oneMinute);

        assertThat(result.allowed()).isFalse();
        assertThat(result.code()).isEqualTo("BUY_CONTINUATION");
    }

    @Test
    void sellRequiresBearishFiveMinuteAndNonBullishOneHour() {
        TradeSignal oneMinute = signal("ETHUSDT", "1m", SignalDecision.SELL, now);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "ETHUSDT", "5m", now)).thenReturn(Optional.of(signal("ETHUSDT", "5m", SignalDecision.SELL, now.minusSeconds(120))));
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "ETHUSDT", "1h", now)).thenReturn(Optional.of(signal("ETHUSDT", "1h", SignalDecision.WATCH, now.minusSeconds(1800))));

        var result = service.validateSell(oneMinute);

        assertThat(result.allowed()).isTrue();
    }

    private TradeSignal signal(String symbol, String interval, SignalDecision decision, Instant generatedAt) {
        return TradeSignal.builder()
                .id(Math.abs((symbol + interval + generatedAt).hashCode()) + 1L)
                .symbol(symbol)
                .interval(interval)
                .decision(decision)
                .generatedAt(generatedAt)
                .build();
    }
}

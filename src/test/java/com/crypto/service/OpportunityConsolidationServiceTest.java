package com.crypto.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.repository.TradeSignalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpportunityConsolidationServiceTest {

    @Mock private TradeSignalRepository signalRepository;

    private OpportunityConsolidationService service;
    private Instant now;

    @BeforeEach
    void setUp() {
        service = new OpportunityConsolidationService(signalRepository);
        now = Instant.parse("2026-08-08T07:00:00Z");
    }

    @Test
    void fivePersistentBuysCanApproveReducedEntryWithNeutralHigherFrames() {
        TradeSignal current = signal(5L, "BNBUSDT", "1m", SignalDecision.BUY, now, 81, 84);
        context("BNBUSDT", "5m", signal(50L, "BNBUSDT", "5m", SignalDecision.NEUTRAL, now.minusSeconds(60), 55, 72));
        context("BNBUSDT", "1h", signal(60L, "BNBUSDT", "1h", SignalDecision.NEUTRAL, now.minusSeconds(1200), 60, 70));
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("BNBUSDT", "1m"))
                .thenReturn(List.of(
                        current,
                        signal(4L, "BNBUSDT", "1m", SignalDecision.BUY, now.minusSeconds(300), 80, 82),
                        signal(3L, "BNBUSDT", "1m", SignalDecision.BUY, now.minusSeconds(600), 79, 80),
                        signal(2L, "BNBUSDT", "1m", SignalDecision.BUY, now.minusSeconds(900), 78, 79),
                        signal(1L, "BNBUSDT", "1m", SignalDecision.BUY, now.minusSeconds(1200), 77, 78)
                ));

        var result = service.evaluate(current);

        assertThat(result.allowed()).isTrue();
        assertThat(result.code()).isEqualTo("CONSOLIDATED_BUY");
        assertThat(result.consecutiveBuyCount()).isEqualTo(5);
        assertThat(result.positionPercent()).isEqualTo(50);
    }

    @Test
    void threePersistentBuysWithWatchWatchApproveSmallEntry() {
        TradeSignal current = signal(3L, "ETHUSDT", "1m", SignalDecision.BUY, now, 80, 80);
        context("ETHUSDT", "5m", signal(50L, "ETHUSDT", "5m", SignalDecision.WATCH, now.minusSeconds(60), 68, 74));
        context("ETHUSDT", "1h", signal(60L, "ETHUSDT", "1h", SignalDecision.WATCH, now.minusSeconds(1200), 69, 72));
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("ETHUSDT", "1m"))
                .thenReturn(List.of(
                        current,
                        signal(2L, "ETHUSDT", "1m", SignalDecision.BUY, now.minusSeconds(300), 79, 79),
                        signal(1L, "ETHUSDT", "1m", SignalDecision.BUY, now.minusSeconds(600), 78, 78)
                ));

        var result = service.evaluate(current);

        assertThat(result.allowed()).isTrue();
        assertThat(result.consecutiveBuyCount()).isEqualTo(3);
        assertThat(result.positionPercent()).isEqualTo(40);
    }

    @Test
    void nonBullishOneMinuteSignalBreaksPersistence() {
        TradeSignal current = signal(4L, "BTCUSDT", "1m", SignalDecision.BUY, now, 80, 80);
        context("BTCUSDT", "5m", signal(50L, "BTCUSDT", "5m", SignalDecision.WATCH, now.minusSeconds(60), 68, 74));
        context("BTCUSDT", "1h", signal(60L, "BTCUSDT", "1h", SignalDecision.WATCH, now.minusSeconds(1200), 69, 72));
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("BTCUSDT", "1m"))
                .thenReturn(List.of(
                        current,
                        signal(3L, "BTCUSDT", "1m", SignalDecision.NEUTRAL, now.minusSeconds(300), 55, 72),
                        signal(2L, "BTCUSDT", "1m", SignalDecision.BUY, now.minusSeconds(600), 79, 79),
                        signal(1L, "BTCUSDT", "1m", SignalDecision.BUY, now.minusSeconds(900), 78, 78)
                ));

        var result = service.evaluate(current);

        assertThat(result.allowed()).isFalse();
        assertThat(result.state()).isEqualTo("BUILDING");
        assertThat(result.consecutiveBuyCount()).isEqualTo(1);
    }

    @Test
    void bearishFiveMinuteCancelsConsolidationRegardlessOfPersistence() {
        TradeSignal current = signal(5L, "XRPUSDT", "1m", SignalDecision.BUY, now, 85, 85);
        context("XRPUSDT", "5m", signal(50L, "XRPUSDT", "5m", SignalDecision.SELL, now.minusSeconds(60), 35, 80));
        context("XRPUSDT", "1h", signal(60L, "XRPUSDT", "1h", SignalDecision.WATCH, now.minusSeconds(1200), 69, 72));

        var result = service.evaluate(current);

        assertThat(result.allowed()).isFalse();
        assertThat(result.code()).isEqualTo("5M_BEARISH_VETO");
        assertThat(result.state()).isEqualTo("CANCELLED");
    }

    private void context(String symbol, String interval, TradeSignal value) {
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                symbol, interval, now)).thenReturn(Optional.of(value));
    }

    private TradeSignal signal(Long id, String symbol, String interval, SignalDecision decision,
                               Instant generatedAt, int score, int confidence) {
        return TradeSignal.builder()
                .id(id)
                .symbol(symbol)
                .interval(interval)
                .decision(decision)
                .totalScore(score)
                .confidenceScore(confidence)
                .generatedAt(generatedAt)
                .build();
    }
}

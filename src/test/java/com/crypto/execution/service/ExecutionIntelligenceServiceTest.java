package com.crypto.execution.service;

import com.crypto.config.TradingProperties;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.execution.repository.ExecutionOpportunityRepository;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.service.OpportunityConsolidationService;
import com.crypto.service.TradeExecutionValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionIntelligenceServiceTest {

    @Mock TradingProperties properties;
    @Mock TradeExecutionValidationService validationService;
    @Mock OpportunityConsolidationService consolidationService;
    @Mock TradeSignalRepository signalRepository;
    @Mock ExecutionOpportunityRepository opportunityRepository;

    private ExecutionIntelligenceService service;
    private Instant now;

    @BeforeEach
    void setUp() {
        service = new ExecutionIntelligenceService(properties, validationService, consolidationService,
                signalRepository, opportunityRepository);
        now = Instant.parse("2026-08-08T10:00:00Z");
        when(opportunityRepository.findTopBySymbolAndDirectionAndStatusInOrderByUpdatedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void weakOneMinuteSellKeepsOpportunityMemoryWhenHigherFramesRemainWatch() {
        TradeSignal current = signal(10L, "BNBUSDT", "1m", SignalDecision.NEUTRAL, SignalDecision.SELL,
                now, 36, 58);
        TradeSignal w1 = signal(9L, "BNBUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(300), 71, 72);
        TradeSignal w2 = signal(8L, "BNBUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(600), 64, 73);
        TradeSignal w3 = signal(7L, "BNBUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(900), 62, 72);
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("BNBUSDT", "1m"))
                .thenReturn(List.of(current, w1, w2, w3));
        context("BNBUSDT", "5m", SignalDecision.WATCH, now.minusSeconds(60));
        context("BNBUSDT", "1h", SignalDecision.WATCH, now.minusSeconds(1200));

        var decision = service.evaluateBuy(current);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.state()).isEqualTo("WEAKENING");
        assertThat(decision.code()).isEqualTo("SOFT_BEARISH_INTERRUPTION");
        assertThat(decision.evidence().watchCount()).isEqualTo(3);
        assertThat(decision.evidence().bearishCount()).isEqualTo(1);
        assertThat(decision.evidence().evidenceScore()).isEqualTo(1);
        assertThat(decision.evidence().opportunityHealth()).isGreaterThan(20);
    }

    @Test
    void strongSellStillCancelsOpportunityImmediately() {
        TradeSignal current = signal(10L, "BNBUSDT", "1m", SignalDecision.STRONG_SELL, SignalDecision.STRONG_SELL,
                now, 18, 80);
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("BNBUSDT", "1m"))
                .thenReturn(List.of(current));
        context("BNBUSDT", "5m", SignalDecision.WATCH, now.minusSeconds(60));
        context("BNBUSDT", "1h", SignalDecision.WATCH, now.minusSeconds(1200));

        var decision = service.evaluateBuy(current);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo("BEARISH_REVERSAL");
        assertThat(decision.state()).isEqualTo("BLOCKED");
    }

    @Test
    void weakSellWithBearishFiveMinuteCancelsOpportunity() {
        TradeSignal current = signal(10L, "BNBUSDT", "1m", SignalDecision.NEUTRAL, SignalDecision.SELL,
                now, 36, 58);
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("BNBUSDT", "1m"))
                .thenReturn(List.of(current));
        context("BNBUSDT", "5m", SignalDecision.SELL, now.minusSeconds(60));
        context("BNBUSDT", "1h", SignalDecision.WATCH, now.minusSeconds(1200));

        var decision = service.evaluateBuy(current);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo("BEARISH_REVERSAL");
    }

    private void context(String symbol, String interval, SignalDecision decision, Instant generatedAt) {
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                symbol, interval, now)).thenReturn(Optional.of(
                signal(99L, symbol, interval, decision, decision, generatedAt, 65, 70)));
    }

    private TradeSignal signal(Long id, String symbol, String interval,
                               SignalDecision decision, SignalDecision originalDecision,
                               Instant generatedAt, int score, int confidence) {
        return TradeSignal.builder()
                .id(id)
                .symbol(symbol)
                .interval(interval)
                .decision(decision)
                .originalDecision(originalDecision)
                .totalScore(score)
                .confidenceScore(confidence)
                .latestPrice(BigDecimal.valueOf(595))
                .stopLoss(BigDecimal.valueOf(590))
                .takeProfit(BigDecimal.valueOf(602))
                .finalEntryAllowed(true)
                .atrImmediateEntryAllowed(true)
                .strategyEntryAllowed(true)
                .btcContextEntryAllowed(true)
                .derivativesEntryAllowed(true)
                .liquidityEntryAllowed(true)
                .generatedAt(generatedAt)
                .build();
    }
}

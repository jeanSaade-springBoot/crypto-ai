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

    @Test
    void higherTimeframeBuyStronglyRecoversGenericOpportunityHealth() {
        TradeSignal current = signal(20L, "TESTUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now, 67, 73);
        TradeSignal priorSell = signal(19L, "TESTUSDT", "1m", SignalDecision.NEUTRAL, SignalDecision.SELL,
                now.minusSeconds(300), 35, 65);
        TradeSignal priorWatch = signal(18L, "TESTUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(600), 66, 72);
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("TESTUSDT", "1m"))
                .thenReturn(List.of(current, priorSell, priorWatch));
        context("TESTUSDT", "5m", SignalDecision.BUY, now.minusSeconds(60));
        context("TESTUSDT", "1h", SignalDecision.WATCH, now.minusSeconds(1200));

        var decision = service.evaluateBuy(current);

        assertThat(decision.evidence().opportunityHealth()).isGreaterThanOrEqualTo(60);
        assertThat(decision.evidence().healthMomentum()).isPositive();
    }

    @Test
    void bearishCurrentSignalCannotExecuteEvenWhenHigherFramesAreSupportive() {
        TradeSignal current = signal(30L, "ANYUSDT", "1m", SignalDecision.NEUTRAL, SignalDecision.SELL,
                now, 40, 70);
        TradeSignal priorBuy = signal(29L, "ANYUSDT", "1m", SignalDecision.BUY, SignalDecision.BUY,
                now.minusSeconds(300), 80, 80);
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("ANYUSDT", "1m"))
                .thenReturn(List.of(current, priorBuy));
        context("ANYUSDT", "5m", SignalDecision.BUY, now.minusSeconds(60));
        context("ANYUSDT", "1h", SignalDecision.WATCH, now.minusSeconds(1200));

        var decision = service.evaluateBuy(current);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.state()).isEqualTo("WEAKENING");
    }

    @Test
    void improvingSequenceProducesPositiveEvidenceMomentum() {
        TradeSignal current = signal(40L, "MKTUSDT", "1m", SignalDecision.BUY, SignalDecision.BUY,
                now, 76, 75);
        TradeSignal watch2 = signal(39L, "MKTUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(300), 70, 72);
        TradeSignal watch1 = signal(38L, "MKTUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(600), 66, 70);
        TradeSignal sell = signal(37L, "MKTUSDT", "1m", SignalDecision.NEUTRAL, SignalDecision.SELL,
                now.minusSeconds(900), 38, 65);
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("MKTUSDT", "1m"))
                .thenReturn(List.of(current, watch2, watch1, sell));
        context("MKTUSDT", "5m", SignalDecision.WATCH, now.minusSeconds(60));
        context("MKTUSDT", "1h", SignalDecision.WATCH, now.minusSeconds(1200));
        when(properties.minimumBuyScore()).thenReturn(999);

        var decision = service.evaluateBuy(current);

        assertThat(decision.evidence().evidenceMomentum()).isPositive();
        assertThat(decision.evidence().opportunityHealth()).isGreaterThan(50);
    }

    @Test
    void deterioratingSequenceProducesNegativeEvidenceMomentum() {
        TradeSignal current = signal(50L, "MKT2USDT", "1m", SignalDecision.NEUTRAL, SignalDecision.SELL,
                now, 38, 66);
        TradeSignal watch = signal(49L, "MKT2USDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(300), 68, 72);
        TradeSignal buy = signal(48L, "MKT2USDT", "1m", SignalDecision.BUY, SignalDecision.BUY,
                now.minusSeconds(600), 78, 78);
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("MKT2USDT", "1m"))
                .thenReturn(List.of(current, watch, buy));
        context("MKT2USDT", "5m", SignalDecision.WATCH, now.minusSeconds(60));
        context("MKT2USDT", "1h", SignalDecision.WATCH, now.minusSeconds(1200));

        var decision = service.evaluateBuy(current);

        assertThat(decision.evidence().evidenceMomentum()).isNegative();
        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void transientReplaySignalsWithoutDatabaseIdsDoNotCrashEvidenceCollection() {
        TradeSignal current = signal(null, "BNBUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now, 68, 72);
        TradeSignal priorWatch = signal(null, "BNBUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(300), 66, 70);

        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("BNBUSDT", "1m"))
                .thenReturn(List.of(current, priorWatch));
        context("BNBUSDT", "5m", SignalDecision.WATCH, now.minusSeconds(60));
        context("BNBUSDT", "1h", SignalDecision.WATCH, now.minusSeconds(1200));

        var decision = service.evaluateBuy(current);

        assertThat(decision.evidence().watchCount()).isEqualTo(2);
        //assertThat(decision.evidence().signalIds()).isEmpty();
    }

    @Test
    void fiveMinuteBuyAtrAuthorityPreventsOneMinuteAtrFromPermanentlyBlockingEntry() {
        TradeSignal current = signal(65L, "BNBUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now, 69, 72);
        current.setLatestPrice(BigDecimal.valueOf(597.32));
        current.setStopLoss(BigDecimal.valueOf(596.50));
        current.setTakeProfit(BigDecimal.valueOf(598.80));
        current.setAtrImmediateEntryAllowed(false);
        current.setFinalEntryAllowed(false);
        current.setAtrEntryType("WAIT_FOR_RETRACEMENT");

        TradeSignal priorWatch = signal(64L, "BNBUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(60), 68, 70);
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("BNBUSDT", "1m"))
                .thenReturn(List.of(current, priorWatch));

        TradeSignal five = signal(66L, "BNBUSDT", "5m", SignalDecision.BUY, SignalDecision.BUY,
                now.minusSeconds(60), 75, 70);
        five.setAtrImmediateEntryAllowed(true);
        five.setFinalEntryAllowed(true);
        five.setAtrRecommendedPositionPercent(60);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "BNBUSDT", "5m", now)).thenReturn(Optional.of(five));
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "BNBUSDT", "1h", now)).thenReturn(Optional.empty());

        var decision = service.evaluateBuy(current);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.source()).isEqualTo("SETUP_TIMEFRAME_ATR");
        assertThat(decision.code()).isEqualTo("REDUCED_POSITION_ALLOWED");
        assertThat(decision.positionPercent()).isEqualTo(30);
    }

    @Test
    void bearishOneHourStillVetoesFiveMinuteAtrAuthorityFallback() {
        TradeSignal current = signal(67L, "BNBUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now, 69, 72);
        current.setAtrImmediateEntryAllowed(false);
        current.setFinalEntryAllowed(false);
        current.setAtrEntryType("WAIT_FOR_RETRACEMENT");
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("BNBUSDT", "1m"))
                .thenReturn(List.of(current));

        TradeSignal five = signal(68L, "BNBUSDT", "5m", SignalDecision.BUY, SignalDecision.BUY,
                now.minusSeconds(60), 75, 70);
        five.setAtrImmediateEntryAllowed(true);
        five.setFinalEntryAllowed(true);
        five.setAtrRecommendedPositionPercent(60);
        TradeSignal one = signal(69L, "BNBUSDT", "1h", SignalDecision.SELL, SignalDecision.SELL,
                now.minusSeconds(1200), 35, 70);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "BNBUSDT", "5m", now)).thenReturn(Optional.of(five));
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "BNBUSDT", "1h", now)).thenReturn(Optional.of(one));

        var decision = service.evaluateBuy(current);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo("ATR_ENTRY_BLOCKED");
    }

    @Test
    void atrDeferredBuyCanExecuteLaterAsReducedContinuationWhenFreshRiskPlanIsGood() {
        TradeSignal current = signal(70L, "TESTUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now, 68, 72);
        current.setLatestPrice(BigDecimal.valueOf(100));
        current.setStopLoss(BigDecimal.valueOf(99));
        current.setTakeProfit(BigDecimal.valueOf(101.5));
        current.setAtrRecommendedPositionPercent(60);
        current.setAtrImmediateEntryAllowed(false);
        current.setFinalEntryAllowed(false);
        current.setAtrEntryType("PULLBACK_ENTRY");

        TradeSignal priorBuy = signal(69L, "TESTUSDT", "1m", SignalDecision.BUY, SignalDecision.BUY,
                now.minusSeconds(600), 78, 55);
        priorBuy.setAtrImmediateEntryAllowed(false);
        priorBuy.setFinalEntryAllowed(false);
        priorBuy.setAtrEntryType("PULLBACK_ENTRY");

        TradeSignal priorWatch = signal(68L, "TESTUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(900), 70, 72);
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("TESTUSDT", "1m"))
                .thenReturn(List.of(current, priorBuy, priorWatch));
        when(properties.minimumBuyScore()).thenReturn(75);

        TradeSignal five = signal(99L, "TESTUSDT", "5m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(60), 73, 75);
        TradeSignal one = signal(100L, "TESTUSDT", "1h", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(1200), 68, 70);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "TESTUSDT", "5m", now)).thenReturn(Optional.of(five));
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "TESTUSDT", "1h", now)).thenReturn(Optional.of(one));

        var decision = service.evaluateBuy(current);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.source()).isEqualTo("DEFERRED_CONTINUATION");
        assertThat(decision.code()).isEqualTo("BREAKOUT_CONTINUATION_ENTRY");
        assertThat(decision.positionPercent()).isEqualTo(30);
    }

    @Test
    void deferredContinuationDoesNotChaseWhenCurrentRewardRiskIsPoor() {
        TradeSignal current = signal(80L, "TEST2USDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now, 68, 72);
        current.setLatestPrice(BigDecimal.valueOf(100));
        current.setStopLoss(BigDecimal.valueOf(98));
        current.setTakeProfit(BigDecimal.valueOf(100.5));
        current.setAtrImmediateEntryAllowed(false);
        current.setFinalEntryAllowed(false);
        current.setAtrEntryType("WAIT_FOR_RETRACEMENT");

        TradeSignal priorBuy = signal(79L, "TEST2USDT", "1m", SignalDecision.BUY, SignalDecision.BUY,
                now.minusSeconds(600), 78, 55);
        priorBuy.setAtrImmediateEntryAllowed(false);
        priorBuy.setFinalEntryAllowed(false);
        priorBuy.setAtrEntryType("WAIT_FOR_RETRACEMENT");
        TradeSignal priorWatch = signal(78L, "TEST2USDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(900), 70, 72);
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("TEST2USDT", "1m"))
                .thenReturn(List.of(current, priorBuy, priorWatch));
        when(properties.minimumBuyScore()).thenReturn(75);

        TradeSignal five = signal(101L, "TEST2USDT", "5m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(60), 73, 75);
        TradeSignal one = signal(102L, "TEST2USDT", "1h", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(1200), 68, 70);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "TEST2USDT", "5m", now)).thenReturn(Optional.of(five));
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "TEST2USDT", "1h", now)).thenReturn(Optional.of(one));

        var decision = service.evaluateBuy(current);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo("CONTINUATION_RISK_REWARD_LOW");
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

package com.crypto.execution.service;

import com.crypto.config.TradingProperties;
import com.crypto.domain.MarketRegime;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.domain.TradingStrategy;
import com.crypto.execution.repository.ExecutionOpportunityRepository;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.service.OpportunityConsolidationService;
import com.crypto.service.TradeExecutionValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
    void neutralOneMinuteTimingDoesNotKillHealthyFiveMinuteBuyAndUsesFiveMinuteAtrForChaseGuard() {
        TradeSignal current = signal(165L, "BNBUSDT", "1m", SignalDecision.NEUTRAL, SignalDecision.NEUTRAL,
                now, 58, 67);
        current.setLatestPrice(BigDecimal.valueOf(597.32));
        current.setStopLoss(BigDecimal.valueOf(596.50));
        current.setTakeProfit(BigDecimal.valueOf(597.80)); // intentionally sub-1:1 to exercise Entry Quality
        current.setAtrAtSignal(BigDecimal.valueOf(0.16616));
        current.setAtrImmediateEntryAllowed(false);
        current.setFinalEntryAllowed(false);
        current.setAtrEntryType("WAIT_FOR_RETRACEMENT");

        TradeSignal w1 = signal(164L, "BNBUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(60), 69, 72);
        TradeSignal w2 = signal(163L, "BNBUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(120), 68, 71);
        TradeSignal w3 = signal(162L, "BNBUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(180), 67, 70);
        TradeSignal w4 = signal(161L, "BNBUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(240), 66, 70);
        TradeSignal w5 = signal(160L, "BNBUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(300), 65, 70);
        TradeSignal w6 = signal(159L, "BNBUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(360), 65, 70);
        TradeSignal w7 = signal(158L, "BNBUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(420), 65, 70);
        // Lower recent price forms the opportunity base used by Entry Quality.
        w7.setLatestPrice(BigDecimal.valueOf(595.70));
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("BNBUSDT", "1m"))
                .thenReturn(List.of(current, w1, w2, w3, w4, w5, w6, w7));

        TradeSignal five = signal(166L, "BNBUSDT", "5m", SignalDecision.BUY, SignalDecision.BUY,
                now.minusSeconds(60), 75, 70);
        five.setAtrAtSignal(BigDecimal.valueOf(0.40));
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
        // Entry Quality caps the 30% setup fallback to 25%, but it must no longer
        // become CHASE_ENTRY_BLOCKED from the tiny 1m ATR.
        assertThat(decision.positionPercent()).isEqualTo(25);
    }

    @Test
    void watchFiveMinutePlusStrongOneHourBuyCanUseReducedTransitionEntryWhenAtrBlocksOneMinute() {
        TradeSignal current = signal(170L, "BNBUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now, 65, 67);
        current.setLatestPrice(BigDecimal.valueOf(602.99));
        current.setStopLoss(BigDecimal.valueOf(601.80));
        current.setTakeProfit(BigDecimal.valueOf(604.80));
        current.setAtrAtSignal(BigDecimal.valueOf(0.31));
        current.setAtrImmediateEntryAllowed(false);
        current.setFinalEntryAllowed(false);
        current.setAtrEntryType("WAIT_FOR_RETRACEMENT");
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("BNBUSDT", "1m"))
                .thenReturn(List.of(current));

        TradeSignal five = signal(171L, "BNBUSDT", "5m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(60), 68, 70);
        five.setAtrAtSignal(BigDecimal.valueOf(0.64));
        five.setAtrImmediateEntryAllowed(false);
        five.setFinalEntryAllowed(false);
        five.setAtrRecommendedPositionPercent(60);
        TradeSignal one = signal(172L, "BNBUSDT", "1h", SignalDecision.BUY, SignalDecision.BUY,
                now.minusSeconds(120), 81, 60);
        one.setAtrAtSignal(BigDecimal.valueOf(2.15));

        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "BNBUSDT", "5m", now)).thenReturn(Optional.of(five));
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "BNBUSDT", "1h", now)).thenReturn(Optional.of(one));

        var decision = service.evaluateBuy(current);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.source()).isEqualTo("HTF_TRANSITION");
        assertThat(decision.code()).isEqualTo("HTF_TRANSITION_REDUCED_ENTRY");
        assertThat(decision.positionPercent()).isBetween(15, 25);
    }

    @Test
    void fiveMinuteWatchDoesNotBypassAtrWithoutStrongOneHourBuy() {
        TradeSignal current = signal(180L, "BNBUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now, 65, 67);
        current.setAtrImmediateEntryAllowed(false);
        current.setFinalEntryAllowed(false);
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("BNBUSDT", "1m"))
                .thenReturn(List.of(current));

        TradeSignal five = signal(181L, "BNBUSDT", "5m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(60), 68, 70);
        five.setAtrImmediateEntryAllowed(false);
        five.setFinalEntryAllowed(false);
        TradeSignal one = signal(182L, "BNBUSDT", "1h", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(120), 75, 70);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "BNBUSDT", "5m", now)).thenReturn(Optional.of(five));
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "BNBUSDT", "1h", now)).thenReturn(Optional.of(one));

        var decision = service.evaluateBuy(current);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo("ATR_ENTRY_BLOCKED");
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

    @Test
    void atrDeferredQualityBuyExecutesWhenRequestedRetracementIsReachedAndFiveMinuteMomentumHolds() {
        TradeSignal current = signal(205L, "ETHUSDT", "1m", SignalDecision.NEUTRAL, SignalDecision.NEUTRAL,
                now, 57, 67);
        current.setLatestPrice(new BigDecimal("1868.82"));
        current.setStopLoss(new BigDecimal("1865.50"));
        current.setTakeProfit(new BigDecimal("1876.00"));

        TradeSignal origin = signal(204L, "ETHUSDT", "1m", SignalDecision.WATCH, SignalDecision.BUY,
                now.minusSeconds(180), 79, 49);
        origin.setLatestPrice(new BigDecimal("1871.69"));
        origin.setTrendScore(21);
        origin.setMomentumScore(9);
        origin.setVolumeScore(18);
        origin.setAtrAtSignal(new BigDecimal("0.911488390635"));
        origin.setAtrOverextended(true);
        origin.setAtrImmediateEntryAllowed(false);
        origin.setAtrEntryType("NO_ENTRY");
        origin.setAtrRetracementEntryPrice(new BigDecimal("1868.625976781270"));

        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("ETHUSDT", "1m"))
                .thenReturn(List.of(current, origin));

        TradeSignal five = signal(206L, "ETHUSDT", "5m", SignalDecision.NEUTRAL, SignalDecision.NEUTRAL,
                now.minusSeconds(330), 48, 72);
        five.setMomentumScore(15);
        five.setMacdScore(8);
        five.setRsiScore(7);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "ETHUSDT", "5m", now)).thenReturn(Optional.of(five));
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "ETHUSDT", "1h", now)).thenReturn(Optional.empty());

        var decision = service.evaluateBuy(current);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.source()).isEqualTo("REVERSAL_RETRACEMENT");
        assertThat(decision.code()).isEqualTo("ATR_RETRACEMENT_REACHED");
        assertThat(decision.positionPercent()).isEqualTo(20);
    }

    @Test
    void atrRetracementDoesNotExecuteWhenFiveMinuteMomentumHasDeteriorated() {
        TradeSignal current = signal(208L, "ETHUSDT", "1m", SignalDecision.NEUTRAL, SignalDecision.NEUTRAL,
                now, 57, 67);
        current.setLatestPrice(new BigDecimal("1868.82"));

        TradeSignal origin = signal(207L, "ETHUSDT", "1m", SignalDecision.WATCH, SignalDecision.BUY,
                now.minusSeconds(180), 79, 49);
        origin.setLatestPrice(new BigDecimal("1871.69"));
        origin.setTrendScore(21);
        origin.setVolumeScore(18);
        origin.setAtrAtSignal(new BigDecimal("0.911488390635"));
        origin.setAtrOverextended(true);
        origin.setAtrImmediateEntryAllowed(false);
        origin.setAtrRetracementEntryPrice(new BigDecimal("1868.625976781270"));
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("ETHUSDT", "1m"))
                .thenReturn(List.of(current, origin));

        TradeSignal five = signal(209L, "ETHUSDT", "5m", SignalDecision.NEUTRAL, SignalDecision.NEUTRAL,
                now.minusSeconds(330), 48, 72);
        five.setMomentumScore(8);
        five.setMacdScore(4);
        five.setRsiScore(4);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "ETHUSDT", "5m", now)).thenReturn(Optional.of(five));
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "ETHUSDT", "1h", now)).thenReturn(Optional.empty());

        var decision = service.evaluateBuy(current);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.source()).isNotEqualTo("REVERSAL_RETRACEMENT");
    }

    @Test
    void replayScopeUsesSameAtrRetracementDecisionPathAsProduction() {
        ExecutionReplayScope scope = new ExecutionReplayScope();
        ReflectionTestUtils.setField(service, "replayScope", scope);

        TradeSignal origin = signal(240L, "ETHUSDT", "1m", SignalDecision.WATCH, SignalDecision.BUY,
                now.minusSeconds(180), 79, 49);
        origin.setLatestPrice(new BigDecimal("1871.69"));
        origin.setTrendScore(21);
        origin.setVolumeScore(18);
        origin.setAtrAtSignal(new BigDecimal("0.911488390635"));
        origin.setAtrOverextended(true);
        origin.setAtrImmediateEntryAllowed(false);
        origin.setAtrRetracementEntryPrice(new BigDecimal("1868.625976781270"));

        TradeSignal five = signal(241L, "ETHUSDT", "5m", SignalDecision.NEUTRAL, SignalDecision.NEUTRAL,
                now.minusSeconds(330), 48, 72);
        five.setMomentumScore(15);
        five.setMacdScore(8);
        five.setRsiScore(7);

        TradeSignal current = signal(242L, "ETHUSDT", "1m", SignalDecision.NEUTRAL, SignalDecision.NEUTRAL,
                now, 57, 67);
        current.setLatestPrice(new BigDecimal("1868.82"));
        current.setStopLoss(new BigDecimal("1865.50"));
        current.setTakeProfit(new BigDecimal("1876.00"));

        try (ExecutionReplayScope.Scope ignored = scope.open(1L, List.of(five, origin, current), o -> {})) {
            scope.reference(now);
            var decision = service.evaluateBuy(current);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.source()).isEqualTo("REVERSAL_RETRACEMENT");
            assertThat(decision.code()).isEqualTo("ATR_RETRACEMENT_REACHED");
        }
    }

    @Test
    void exceptionalStrengthCanUseTinyProbeWhenOnlyBtcVetoRemains() {
        TradeSignal current = signal(210L, "ETHUSDT", "1m", SignalDecision.WATCH, SignalDecision.STRONG_BUY,
                now, 90, 49);
        current.setTrendScore(18);
        current.setMomentumScore(15);
        current.setVolumeScore(20);
        current.setBtcContextEntryAllowed(false);
        current.setBtcContextDecision(SignalDecision.STRONG_SELL);
        TradeSignal priorBearish = signal(209L, "ETHUSDT", "1m", SignalDecision.SELL, SignalDecision.SELL,
                now.minusSeconds(60), 31, 75);
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("ETHUSDT", "1m"))
                .thenReturn(List.of(current, priorBearish));

        TradeSignal five = signal(211L, "ETHUSDT", "5m", SignalDecision.NEUTRAL, SignalDecision.NEUTRAL,
                now.minusSeconds(60), 55, 67);
        TradeSignal one = signal(212L, "ETHUSDT", "1h", SignalDecision.NEUTRAL, SignalDecision.NEUTRAL,
                now.minusSeconds(1200), 59, 63);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "ETHUSDT", "5m", now)).thenReturn(Optional.of(five));
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "ETHUSDT", "1h", now)).thenReturn(Optional.of(one));

        var decision = service.evaluateBuy(current);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.source()).isEqualTo("EXCEPTIONAL_STRENGTH_PROBE");
        assertThat(decision.code()).isEqualTo("BTC_CONFLICT_REDUCED_PROBE");
        assertThat(decision.positionPercent()).isEqualTo(10);
    }

    @Test
    void exceptionalStrengthDoesNotBypassBearishFiveMinuteContext() {
        TradeSignal current = signal(215L, "ETHUSDT", "1m", SignalDecision.WATCH, SignalDecision.STRONG_BUY,
                now, 90, 49);
        current.setTrendScore(18);
        current.setMomentumScore(15);
        current.setVolumeScore(20);
        current.setBtcContextEntryAllowed(false);
        current.setBtcContextDecision(SignalDecision.STRONG_SELL);
        TradeSignal priorBearish = signal(214L, "ETHUSDT", "1m", SignalDecision.SELL, SignalDecision.SELL,
                now.minusSeconds(60), 31, 75);
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("ETHUSDT", "1m"))
                .thenReturn(List.of(current, priorBearish));

        TradeSignal five = signal(216L, "ETHUSDT", "5m", SignalDecision.SELL, SignalDecision.SELL,
                now.minusSeconds(60), 43, 67);
        TradeSignal one = signal(217L, "ETHUSDT", "1h", SignalDecision.NEUTRAL, SignalDecision.NEUTRAL,
                now.minusSeconds(1200), 59, 63);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "ETHUSDT", "5m", now)).thenReturn(Optional.of(five));
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "ETHUSDT", "1h", now)).thenReturn(Optional.of(one));

        var decision = service.evaluateBuy(current);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo("BTC_CONTEXT_BLOCKED");
    }

    @Test
    void replayScopeUsesTheSameHighConvictionReversalDecisionPathAsProduction() {
        ExecutionReplayScope scope = new ExecutionReplayScope();
        ReflectionTestUtils.setField(service, "replayScope", scope);

        TradeSignal priorBearish = signal(230L, "ETHUSDT", "1m", SignalDecision.SELL, SignalDecision.SELL,
                now.minusSeconds(60), 31, 75);
        TradeSignal current = signal(231L, "ETHUSDT", "1m", SignalDecision.WATCH, SignalDecision.STRONG_BUY,
                now, 90, 49);
        current.setTrendScore(18);
        current.setMomentumScore(15);
        current.setVolumeScore(20);
        current.setBtcContextEntryAllowed(false);
        current.setBtcContextDecision(SignalDecision.STRONG_SELL);
        TradeSignal five = signal(232L, "ETHUSDT", "5m", SignalDecision.NEUTRAL, SignalDecision.NEUTRAL,
                now.minusSeconds(30), 55, 67);
        TradeSignal one = signal(233L, "ETHUSDT", "1h", SignalDecision.NEUTRAL, SignalDecision.NEUTRAL,
                now.minusSeconds(1200), 59, 63);

        try (ExecutionReplayScope.Scope ignored = scope.open(1L, List.of(priorBearish, five, one, current), o -> {})) {
            scope.reference(now);
            var decision = service.evaluateBuy(current);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.source()).isEqualTo("EXCEPTIONAL_STRENGTH_PROBE");
            assertThat(decision.code()).isEqualTo("BTC_CONFLICT_REDUCED_PROBE");
            assertThat(decision.positionPercent()).isEqualTo(10);
        }
    }

    @Test
    void exceptionalStrengthDoesNotFireWithoutFreshBearishToBullishAcceleration() {
        TradeSignal current = signal(218L, "ETHUSDT", "1m", SignalDecision.WATCH, SignalDecision.STRONG_BUY,
                now, 90, 49);
        current.setTrendScore(18);
        current.setMomentumScore(15);
        current.setVolumeScore(20);
        current.setBtcContextEntryAllowed(false);
        current.setBtcContextDecision(SignalDecision.STRONG_SELL);
        TradeSignal priorWatch = signal(219L, "ETHUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(60), 73, 69);
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("ETHUSDT", "1m"))
                .thenReturn(List.of(current, priorWatch));
        context("ETHUSDT", "5m", SignalDecision.NEUTRAL, now.minusSeconds(60));
        context("ETHUSDT", "1h", SignalDecision.NEUTRAL, now.minusSeconds(1200));

        var decision = service.evaluateBuy(current);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo("BTC_CONTEXT_BLOCKED");
    }

    @Test
    void watchOnlyAccumulationWaitsWhenFiveMinuteIsNeutralAndOneHourOnlyWatch() {
        TradeSignal current = signal(220L, "GLOBALUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now, 66, 67);
        List<TradeSignal> evidence = new java.util.ArrayList<>();
        evidence.add(current);
        for (int i = 1; i < 10; i++) {
            evidence.add(signal(220L - i, "GLOBALUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                    now.minusSeconds(i * 60L), 66, 67));
        }
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("GLOBALUSDT", "1m"))
                .thenReturn(evidence);
        context("GLOBALUSDT", "5m", SignalDecision.NEUTRAL, now.minusSeconds(60));
        context("GLOBALUSDT", "1h", SignalDecision.WATCH, now.minusSeconds(1200));

        var decision = service.evaluateBuy(current);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.state()).isEqualTo("BUILDING");
        assertThat(decision.code()).isEqualTo("WATCH_ONLY_NEEDS_FRESH_CONFIRMATION");
        assertThat(decision.evidence().buyCount()).isZero();
        assertThat(decision.evidence().watchCount()).isEqualTo(10);
    }

    @Test
    void watchOnlyAccumulationCanStillExecuteWhenFiveMinuteFreshlySupportsIt() {
        TradeSignal current = signal(240L, "SUPPORTUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now, 66, 67);
        List<TradeSignal> evidence = new java.util.ArrayList<>();
        evidence.add(current);
        for (int i = 1; i < 10; i++) {
            evidence.add(signal(240L - i, "SUPPORTUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                    now.minusSeconds(i * 60L), 66, 67));
        }
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("SUPPORTUSDT", "1m"))
                .thenReturn(evidence);
        context("SUPPORTUSDT", "5m", SignalDecision.WATCH, now.minusSeconds(60));
        context("SUPPORTUSDT", "1h", SignalDecision.WATCH, now.minusSeconds(1200));

        var decision = service.evaluateBuy(current);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.source()).isEqualTo("ACCUMULATED_EVIDENCE");
        assertThat(decision.code()).isEqualTo("OPPORTUNITY_CONFIRMED");
        assertThat(decision.evidence().buyCount()).isZero();
    }

    @Test
    void accumulatedEvidenceWithRealBuyObservationRemainsEligibleWithNeutralFiveMinute() {
        TradeSignal current = signal(260L, "REALBUYUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now, 66, 67);
        List<TradeSignal> evidence = new java.util.ArrayList<>();
        evidence.add(current);
        evidence.add(signal(259L, "REALBUYUSDT", "1m", SignalDecision.BUY, SignalDecision.BUY,
                now.minusSeconds(60), 78, 78));
        for (int i = 2; i < 7; i++) {
            evidence.add(signal(260L - i, "REALBUYUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                    now.minusSeconds(i * 60L), 67, 68));
        }
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("REALBUYUSDT", "1m"))
                .thenReturn(evidence);
        context("REALBUYUSDT", "5m", SignalDecision.NEUTRAL, now.minusSeconds(60));
        context("REALBUYUSDT", "1h", SignalDecision.WATCH, now.minusSeconds(1200));

        var decision = service.evaluateBuy(current);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.source()).isEqualTo("ACCUMULATED_EVIDENCE");
        assertThat(decision.code()).isEqualTo("OPPORTUNITY_CONFIRMED");
        assertThat(decision.evidence().buyCount()).isEqualTo(1);
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
    @Test
    void atrRetracementDoesNotExecuteWhenNewBearishOneHourAppearsAfterOrigin() {
        TradeSignal current = signal(260L, "ETHUSDT", "1m", SignalDecision.NEUTRAL, SignalDecision.NEUTRAL,
                now, 62, 70);
        current.setLatestPrice(new BigDecimal("1868.82"));
        current.setStopLoss(new BigDecimal("1865.50"));
        current.setTakeProfit(new BigDecimal("1876.00"));

        TradeSignal origin = signal(259L, "ETHUSDT", "1m", SignalDecision.WATCH, SignalDecision.BUY,
                now.minusSeconds(180), 79, 49);
        origin.setLatestPrice(new BigDecimal("1871.69"));
        origin.setTrendScore(21);
        origin.setVolumeScore(18);
        origin.setAtrAtSignal(new BigDecimal("0.911488390635"));
        origin.setAtrOverextended(true);
        origin.setAtrImmediateEntryAllowed(false);
        origin.setAtrRetracementEntryPrice(new BigDecimal("1868.625976781270"));
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("ETHUSDT", "1m"))
                .thenReturn(List.of(current, origin));

        TradeSignal five = signal(261L, "ETHUSDT", "5m", SignalDecision.NEUTRAL, SignalDecision.NEUTRAL,
                now.minusSeconds(60), 60, 72);
        five.setMomentumScore(15);
        five.setMacdScore(8);
        five.setRsiScore(7);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "ETHUSDT", "5m", now)).thenReturn(Optional.of(five));

        TradeSignal originHour = signal(262L, "ETHUSDT", "1h", SignalDecision.NEUTRAL, SignalDecision.NEUTRAL,
                origin.getGeneratedAt().minusSeconds(600), 55, 68);
        TradeSignal newBearishHour = signal(263L, "ETHUSDT", "1h", SignalDecision.STRONG_SELL, SignalDecision.STRONG_SELL,
                now.minusSeconds(60), 15, 68);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "ETHUSDT", "1h", origin.getGeneratedAt())).thenReturn(Optional.of(originHour));
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "ETHUSDT", "1h", now)).thenReturn(Optional.of(newBearishHour));

        var decision = service.evaluateBuy(current);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.source()).isNotEqualTo("REVERSAL_RETRACEMENT");
    }

    @Test
    void atrRetracementDoesNotExecuteWhenCurrentRiskRewardHasCollapsed() {
        TradeSignal current = signal(270L, "ETHUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now, 66, 70);
        current.setLatestPrice(new BigDecimal("1868.82"));
        current.setStopLoss(new BigDecimal("1865.50"));
        current.setTakeProfit(new BigDecimal("1869.20"));

        TradeSignal origin = signal(269L, "ETHUSDT", "1m", SignalDecision.WATCH, SignalDecision.BUY,
                now.minusSeconds(180), 79, 49);
        origin.setLatestPrice(new BigDecimal("1871.69"));
        origin.setTrendScore(21);
        origin.setVolumeScore(18);
        origin.setAtrAtSignal(new BigDecimal("0.911488390635"));
        origin.setAtrOverextended(true);
        origin.setAtrImmediateEntryAllowed(false);
        origin.setAtrRetracementEntryPrice(new BigDecimal("1868.625976781270"));
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("ETHUSDT", "1m"))
                .thenReturn(List.of(current, origin));

        TradeSignal five = signal(271L, "ETHUSDT", "5m", SignalDecision.NEUTRAL, SignalDecision.NEUTRAL,
                now.minusSeconds(60), 60, 72);
        five.setMomentumScore(15);
        five.setMacdScore(8);
        five.setRsiScore(7);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "ETHUSDT", "5m", now)).thenReturn(Optional.of(five));
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "ETHUSDT", "1h", now)).thenReturn(Optional.empty());

        var decision = service.evaluateBuy(current);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.source()).isNotEqualTo("REVERSAL_RETRACEMENT");
    }


    @Test
    void breakoutRetracementUsesRememberedBreakoutVolumeInsteadOfDemandingSecondVolumeSpike() {
        TradeSignal current = signal(920L, "ETHUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now, 66, 68);
        current.setLatestPrice(new BigDecimal("1878.93"));
        current.setTrendScore(20);
        current.setMomentumScore(15);
        current.setVolumeScore(6); // pullback volume cooled; this must not erase prior breakout confirmation
        current.setAtrAtSignal(new BigDecimal("0.47982283513"));
        current.setStopLoss(new BigDecimal("1877.50"));
        current.setTakeProfit(new BigDecimal("1881.20"));
        current.setAtrImmediateEntryAllowed(true);
        current.setFinalEntryAllowed(true);
        current.setSelectedStrategy(TradingStrategy.TREND_FOLLOWING);
        current.setMarketRegime(MarketRegime.WEAK_UPTREND);

        TradeSignal origin = signal(921L, "ETHUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(360), 71, 72);
        origin.setLatestPrice(new BigDecimal("1880.20"));
        origin.setTrendScore(17);
        origin.setMomentumScore(11);
        origin.setVolumeScore(16);
        origin.setSelectedStrategy(TradingStrategy.BREAKOUT);
        origin.setMarketRegime(MarketRegime.BREAKOUT);
        origin.setAtrEntryType("WAIT_FOR_RETRACEMENT");
        origin.setAtrAtSignal(new BigDecimal("0.53234294964"));
        origin.setAtrRetracementEntryPrice(new BigDecimal("1879.218357"));
        origin.setAtrImmediateEntryAllowed(false);

        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("ETHUSDT", "1m"))
                .thenReturn(List.of(current, origin));

        TradeSignal five = signal(922L, "ETHUSDT", "5m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(120), 67, 74);
        five.setMomentumScore(15);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "ETHUSDT", "5m", now)).thenReturn(Optional.of(five));

        TradeSignal one = signal(923L, "ETHUSDT", "1h", SignalDecision.SELL, SignalDecision.SELL,
                now.minusSeconds(3300), 36, 68);
        one.setTrendScore(6);
        one.setMomentumScore(9);
        TradeSignal priorOne = signal(924L, "ETHUSDT", "1h", SignalDecision.SELL, SignalDecision.SELL,
                now.minusSeconds(6900), 31, 65);
        priorOne.setTrendScore(3);
        priorOne.setMomentumScore(9);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "ETHUSDT", "1h", origin.getGeneratedAt())).thenReturn(Optional.of(one));
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "ETHUSDT", "1h", now)).thenReturn(Optional.of(one));
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("ETHUSDT", "1h"))
                .thenReturn(List.of(one, priorOne));

        var evidence = new ExecutionIntelligenceService.Evidence(20, 2, 12, 1, 5, 8, 72, 4, 3, 70, 68,
                SignalDecision.WATCH, SignalDecision.SELL, now.minusSeconds(600), List.of(920L, 921L));

        ExecutionIntelligenceService.ExecutionDecision decision = ReflectionTestUtils.invokeMethod(
                service, "breakoutRetracementDecision", current, evidence);

        assertThat(decision).isNotNull();
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.source()).isEqualTo("BREAKOUT_RETRACEMENT");
        assertThat(decision.code()).isEqualTo("BREAKOUT_RETRACEMENT_ENTRY");
        assertThat(decision.positionPercent()).isEqualTo(20);
        assertThat(decision.explanation()).contains("Current-candle volume is not required to repeat the breakout spike");
    }

    @Test
    void breakoutRetracementDoesNotBypassBearishFiveMinuteContext() {
        TradeSignal current = signal(930L, "ETHUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now, 66, 68);
        current.setLatestPrice(new BigDecimal("1878.93"));
        current.setTrendScore(20);
        current.setMomentumScore(15);
        current.setAtrAtSignal(new BigDecimal("0.47982283513"));
        current.setStopLoss(new BigDecimal("1877.50"));
        current.setTakeProfit(new BigDecimal("1881.20"));

        TradeSignal origin = signal(931L, "ETHUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(360), 71, 72);
        origin.setLatestPrice(new BigDecimal("1880.20"));
        origin.setTrendScore(17);
        origin.setMomentumScore(11);
        origin.setVolumeScore(16);
        origin.setSelectedStrategy(TradingStrategy.BREAKOUT);
        origin.setMarketRegime(MarketRegime.BREAKOUT);
        origin.setAtrEntryType("WAIT_FOR_RETRACEMENT");
        origin.setAtrAtSignal(new BigDecimal("0.53234294964"));
        origin.setAtrRetracementEntryPrice(new BigDecimal("1879.218357"));
        origin.setAtrImmediateEntryAllowed(false);
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("ETHUSDT", "1m"))
                .thenReturn(List.of(current, origin));

        TradeSignal five = signal(932L, "ETHUSDT", "5m", SignalDecision.SELL, SignalDecision.SELL,
                now.minusSeconds(120), 38, 70);
        five.setMomentumScore(4);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                "ETHUSDT", "5m", now)).thenReturn(Optional.of(five));

        var evidence = new ExecutionIntelligenceService.Evidence(20, 2, 12, 1, 5, 8, 72, 4, 3, 70, 68,
                SignalDecision.SELL, SignalDecision.WATCH, now.minusSeconds(600), List.of(930L, 931L));

        ExecutionIntelligenceService.ExecutionDecision decision = ReflectionTestUtils.invokeMethod(
                service, "breakoutRetracementDecision", current, evidence);
        assertThat(decision).isNull();
    }

    @Test
    void replayUsesSameSharedBreakoutRetracementPathAsProduction() {
        ExecutionReplayScope scope = new ExecutionReplayScope();
        ReflectionTestUtils.setField(service, "replayScope", scope);

        TradeSignal origin = signal(940L, "ETHUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(360), 71, 72);
        origin.setLatestPrice(new BigDecimal("1880.20"));
        origin.setTrendScore(17);
        origin.setMomentumScore(11);
        origin.setVolumeScore(16);
        origin.setSelectedStrategy(TradingStrategy.BREAKOUT);
        origin.setMarketRegime(MarketRegime.BREAKOUT);
        origin.setAtrEntryType("WAIT_FOR_RETRACEMENT");
        origin.setAtrAtSignal(new BigDecimal("0.53234294964"));
        origin.setAtrRetracementEntryPrice(new BigDecimal("1879.218357"));
        origin.setAtrImmediateEntryAllowed(false);

        TradeSignal five = signal(941L, "ETHUSDT", "5m", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(120), 67, 74);
        five.setMomentumScore(15);
        TradeSignal one = signal(942L, "ETHUSDT", "1h", SignalDecision.WATCH, SignalDecision.WATCH,
                now.minusSeconds(1200), 60, 70);

        TradeSignal current = signal(943L, "ETHUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH,
                now, 66, 68);
        current.setLatestPrice(new BigDecimal("1878.93"));
        current.setTrendScore(20);
        current.setMomentumScore(15);
        current.setVolumeScore(6);
        current.setAtrAtSignal(new BigDecimal("0.47982283513"));
        current.setStopLoss(new BigDecimal("1877.50"));
        current.setTakeProfit(new BigDecimal("1881.20"));
        current.setAtrImmediateEntryAllowed(true);
        current.setFinalEntryAllowed(true);

        // Enough supportive replay history to keep opportunity health above the new
        // breakout-retracement minimum while still using the exact production service.
        TradeSignal w1 = signal(944L, "ETHUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH, now.minusSeconds(60), 68, 70);
        TradeSignal w2 = signal(945L, "ETHUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH, now.minusSeconds(120), 69, 70);
        TradeSignal w3 = signal(946L, "ETHUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH, now.minusSeconds(180), 70, 70);
        TradeSignal w4 = signal(947L, "ETHUSDT", "1m", SignalDecision.WATCH, SignalDecision.WATCH, now.minusSeconds(240), 70, 70);
        w1.setLatestPrice(new BigDecimal("1879.10"));
        w2.setLatestPrice(new BigDecimal("1879.20"));
        w3.setLatestPrice(new BigDecimal("1879.30"));
        w4.setLatestPrice(new BigDecimal("1879.40"));

        try (ExecutionReplayScope.Scope ignored = scope.open(1L, List.of(one, origin, w4, w3, w2, w1, five, current), o -> {})) {
            scope.reference(now);
            var decision = service.evaluateBuy(current);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.source()).isEqualTo("BREAKOUT_RETRACEMENT");
            assertThat(decision.code()).isEqualTo("BREAKOUT_RETRACEMENT_ENTRY");
        }
    }

    @Test
    void balancedEarlyBlocksImmediatelyAfterFreshFiveMinuteSellWhenRecoveryIsWeak() {
        TradeSignal current = signal(900L, "ETHUSDT", "1m", SignalDecision.BUY, SignalDecision.BUY, now, 75, 67);
        TradeSignal recoveredWatch = signal(901L, "ETHUSDT", "5m", SignalDecision.WATCH, SignalDecision.WATCH, now, 61, 67);
        TradeSignal recentSell = signal(902L, "ETHUSDT", "5m", SignalDecision.SELL, SignalDecision.SELL, now.minusSeconds(300), 37, 67);
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("ETHUSDT", "5m"))
                .thenReturn(List.of(recoveredWatch, recentSell));

        var evidence = new ExecutionIntelligenceService.Evidence(20, 1, 0, 15, 4, 0, 38, 0, 0, 60, 67,
                SignalDecision.WATCH, SignalDecision.WATCH, now.minusSeconds(180), List.of(900L));
        var quality = new ExecutionIntelligenceService.EntryQuality(70, "GOOD_ENTRY", 0.1, 0.5, 1.5, 1);
        var validation = TradeExecutionValidationService.ValidationResult.allow(50, "BALANCED_EARLY", "both WATCH");

        ExecutionIntelligenceService.ExecutionDecision decision = ReflectionTestUtils.invokeMethod(
                service, "balancedEarlyPostBearishGuard", current, evidence, quality, validation);

        assertThat(decision).isNotNull();
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo("BALANCED_EARLY_POST_BEARISH_RECOVERY_REQUIRED");
    }

    @Test
    void balancedEarlyCanRecoverAfterTwoFiveMinuteConfirmationsButCapsInitialRisk() {
        TradeSignal current = signal(910L, "ETHUSDT", "1m", SignalDecision.BUY, SignalDecision.BUY, now, 82, 74);
        TradeSignal watch2 = signal(911L, "ETHUSDT", "5m", SignalDecision.WATCH, SignalDecision.WATCH, now, 68, 70);
        TradeSignal watch1 = signal(912L, "ETHUSDT", "5m", SignalDecision.WATCH, SignalDecision.WATCH, now.minusSeconds(300), 66, 70);
        TradeSignal recentSell = signal(913L, "ETHUSDT", "5m", SignalDecision.SELL, SignalDecision.SELL, now.minusSeconds(600), 38, 68);
        when(signalRepository.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("ETHUSDT", "5m"))
                .thenReturn(List.of(watch2, watch1, recentSell));

        var evidence = new ExecutionIntelligenceService.Evidence(20, 3, 4, 9, 4, 6, 67, 8, 7, 72, 70,
                SignalDecision.WATCH, SignalDecision.WATCH, now.minusSeconds(600), List.of(910L));
        var quality = new ExecutionIntelligenceService.EntryQuality(72, "GOOD_ENTRY", 0.1, 0.5, 1.6, 5);
        var validation = TradeExecutionValidationService.ValidationResult.allow(50, "BALANCED_EARLY", "both WATCH");

        ExecutionIntelligenceService.ExecutionDecision decision = ReflectionTestUtils.invokeMethod(
                service, "balancedEarlyPostBearishGuard", current, evidence, quality, validation);

        assertThat(decision).isNotNull();
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.code()).isEqualTo("BALANCED_EARLY_POST_BEARISH_RECOVERED");
        assertThat(decision.positionPercent()).isEqualTo(25);
    }

}

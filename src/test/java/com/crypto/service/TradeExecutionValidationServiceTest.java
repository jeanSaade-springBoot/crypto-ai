package com.crypto.service;

import com.crypto.domain.BtcContextStatus;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.execution.service.EntryConsumptionPolicy;
import com.crypto.execution.service.ExecutionReplayScope;
import com.crypto.execution.domain.EntryConsumptionState;
import com.crypto.wallet.domain.WalletSettings;
import com.crypto.wallet.repository.WalletSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeExecutionValidationServiceTest {

    @Mock private TradeSignalRepository signalRepository;
    @Mock private WalletSettingsRepository settingsRepository;
    @Mock private EntryConsumptionPolicy entryConsumptionPolicy;

    private TradeExecutionValidationService service;
    private Instant now;

    @BeforeEach
    void setUp() {
        service = new TradeExecutionValidationService(signalRepository, settingsRepository, entryConsumptionPolicy);
        now = Instant.parse("2026-08-08T05:00:00Z");
    }

    @Test
    void buyMustComeFromOneMinuteExecutionFrame() {

        // FIX-112A test alignment:
        // A non-1m BUY is rejected before wallet execution settings are consulted,
        // so this test must not stub settingsRepository.
        var result = service.validateBuy(
                signal("BTCUSDT", "5m", SignalDecision.BUY, now, 80, 23, 18, 13), 100
        );

        assertThat(result.allowed()).isFalse();
        assertThat(result.code()).isEqualTo("NON_EXECUTION_TIMEFRAME");
    }

    @Test
    void conservativeRequiresBullishFiveMinute() {
        settings("CONSERVATIVE", false);
        TradeSignal oneMinute = signal("BTCUSDT", "1m", SignalDecision.BUY, now, 80, 23, 18, 13);
        latest("BTCUSDT", "5m", signal("BTCUSDT", "5m", SignalDecision.WATCH, now.minusSeconds(120), 70, 20, 15, 10));
        latest("BTCUSDT", "1h", signal("BTCUSDT", "1h", SignalDecision.BUY, now.minusSeconds(1800), 80, 23, 18, 13));
        var result = service.validateBuy(oneMinute, 100);
        assertThat(result.allowed()).isFalse();
        assertThat(result.code()).isEqualTo("5M_NOT_BULLISH");
    }

    @Test
    void balancedAllowsBullishFiveMinuteWithNeutralOneHourAtSeventyFivePercent() {
        settings("BALANCED", false);
        TradeSignal oneMinute = signal("BTCUSDT", "1m", SignalDecision.BUY, now, 80, 23, 18, 13);
        latest("BTCUSDT", "5m", signal("BTCUSDT", "5m", SignalDecision.BUY, now.minusSeconds(120), 80, 23, 18, 13));
        latest("BTCUSDT", "1h", signal("BTCUSDT", "1h", SignalDecision.NEUTRAL, now.minusSeconds(1800), 55, 15, 10, 8));
        var result = service.validateBuy(oneMinute, 100);
        assertThat(result.allowed()).isTrue();
        assertThat(result.positionPercent()).isEqualTo(75);
    }

    @Test
    void balancedAllowsWatchWatchAtFiftyPercent() {
        settings("BALANCED", false);
        TradeSignal oneMinute = signal("BNBUSDT", "1m", SignalDecision.BUY, now, 79, 22, 17, 13);
        latest("BNBUSDT", "5m", signal("BNBUSDT", "5m", SignalDecision.WATCH, now.minusSeconds(120), 68, 18, 13, 10));
        latest("BNBUSDT", "1h", signal("BNBUSDT", "1h", SignalDecision.WATCH, now.minusSeconds(1800), 69, 19, 14, 10));
        var result = service.validateBuy(oneMinute, 100);
        assertThat(result.allowed()).isTrue();
        assertThat(result.positionPercent()).isEqualTo(50);
    }

    @Test
    void aggressiveAllowsQualifiedNeutralOneHourProbe() {
        settings("AGGRESSIVE", false);
        TradeSignal oneMinute = signal("SOLUSDT", "1m", SignalDecision.BUY, now, 88, 23, 17, 13);
        latest("SOLUSDT", "5m", signal("SOLUSDT", "5m", SignalDecision.WATCH, now.minusSeconds(120), 68, 18, 13, 10));
        latest("SOLUSDT", "1h", signal("SOLUSDT", "1h", SignalDecision.NEUTRAL, now.minusSeconds(1800), 55, 15, 10, 8));
        var result = service.validateBuy(oneMinute, 100);
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
        var result = service.validateBuy(oneMinute, 100);
        assertThat(result.allowed()).isFalse();
        assertThat(result.code()).isEqualTo("AGGRESSIVE_PROBE_QUALITY");
    }

    @Test
    void bearishOneHourAlwaysVetoesBuy() {
        settings("AGGRESSIVE", false);
        TradeSignal oneMinute = signal("BTCUSDT", "1m", SignalDecision.BUY, now, 90, 25, 20, 15);
        latest("BTCUSDT", "5m", signal("BTCUSDT", "5m", SignalDecision.STRONG_BUY, now.minusSeconds(120), 90, 25, 20, 15));
        latest("BTCUSDT", "1h", signal("BTCUSDT", "1h", SignalDecision.SELL, now.minusSeconds(1800), 35, 8, 6, 5));
        var result = service.validateBuy(oneMinute, 100);
        assertThat(result.allowed()).isFalse();
        assertThat(result.code()).isEqualTo("1H_BEARISH_VETO");
    }

    @Test
    void repeatedOneMinuteBuyRemainsProtectedAfterRealEntryConsumption() {
        settings("BALANCED", true);
        TradeSignal oneMinute = signal("ETHUSDT", "1m", SignalDecision.BUY, now, 80, 23, 18, 13);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanOrderByGeneratedAtDesc(
                "ETHUSDT", "1m", now)).thenReturn(Optional.of(
                signal("ETHUSDT", "1m", SignalDecision.BUY, now.minusSeconds(60), 79, 22, 17, 13)));
        when(entryConsumptionPolicy.resolve(org.mockito.ArgumentMatchers.anyLong())).thenReturn(EntryConsumptionState.CONSUMED);
        var result = service.validateBuy(oneMinute, 100);
        assertThat(result.allowed()).isFalse();
        assertThat(result.code()).isEqualTo("BUY_CONTINUATION");
    }

    @Test
    void blockedPreviousBullishLabelDoesNotManufactureBuyContinuation() {
        settings("BALANCED", true);
        TradeSignal oneMinute = signal("SHIBUSDT", "1m", SignalDecision.BUY, now, 80, 23, 18, 13);
        when(signalRepository.findTopBySymbolAndIntervalAndGeneratedAtLessThanOrderByGeneratedAtDesc(
                "SHIBUSDT", "1m", now)).thenReturn(Optional.of(
                signal("SHIBUSDT", "1m", SignalDecision.STRONG_BUY, now.minusSeconds(60), 79, 22, 17, 13)));
        when(entryConsumptionPolicy.resolve(org.mockito.ArgumentMatchers.anyLong())).thenReturn(EntryConsumptionState.NOT_CONSUMED);
        latest("SHIBUSDT", "5m", signal("SHIBUSDT", "5m", SignalDecision.BUY, now.minusSeconds(120), 80, 23, 18, 13));
        latest("SHIBUSDT", "1h", signal("SHIBUSDT", "1h", SignalDecision.BUY, now.minusSeconds(1800), 80, 23, 18, 13));
        var result = service.validateBuy(oneMinute, 100);
        assertThat(result.allowed()).isTrue();
        assertThat(result.code()).isEqualTo("BALANCED_FULL");
    }

    @Test
    void accumulatedEvidenceContextUsesSameBalancedAuthorityAsDirectBuy() {
        settings("BALANCED", false);
        TradeSignal reference = signal("BICOUSDT", "1m", SignalDecision.WATCH, now, 76, 22, 6, 7);
        latest("BICOUSDT", "1m", reference);
        latest("BICOUSDT", "5m", signal("BICOUSDT", "5m", SignalDecision.NEUTRAL, now.minusSeconds(120), 79, 21, 14, 9));
        latest("BICOUSDT", "1h", signal("BICOUSDT", "1h", SignalDecision.NEUTRAL, now.minusSeconds(1800), 75, 12, 16, 9));

        var result = service.validateBuyContext(reference);

        assertThat(result.allowed()).isFalse();
        assertThat(result.code()).isEqualTo("BALANCED_CONFIRMATION_INSUFFICIENT");
    }

    @Test
    void accumulatedEvidenceContextAllowsFreshBullishFiveMinuteWithNeutralOneHour() {
        settings("BALANCED", false);
        TradeSignal reference = signal("ETHUSDT", "5m", SignalDecision.BUY, now, 78, 19, 15, 14);
        latest("ETHUSDT", "1m", signal("ETHUSDT", "1m", SignalDecision.WATCH, now.minusSeconds(3), 68, 19, 7, 13));
        latest("ETHUSDT", "5m", reference);
        latest("ETHUSDT", "1h", signal("ETHUSDT", "1h", SignalDecision.NEUTRAL, now.minusSeconds(1800), 69, 19, 7, 1));

        var result = service.validateBuyContext(reference);

        assertThat(result.allowed()).isTrue();
        assertThat(result.code()).isEqualTo("BALANCED_STRONG");
        assertThat(result.positionPercent()).isEqualTo(75);
    }

    @Test
    void balancedNeutralFiveWithBullishOneHourAllowsExploratoryTwentyFivePercent() {
        settings("BALANCED", false);
        TradeSignal oneMinute = signal("SHIBUSDT", "1m", SignalDecision.BUY, now, 77, 23, 18, 13);
        latest("SHIBUSDT", "5m", signal("SHIBUSDT", "5m", SignalDecision.NEUTRAL, now.minusSeconds(120), 60, 15, 10, 8));
        latest("SHIBUSDT", "1h", signal("SHIBUSDT", "1h", SignalDecision.STRONG_BUY, now.minusSeconds(1800), 90, 25, 18, 14));

        var result = service.validateBuy(oneMinute, 75);

        assertThat(result.allowed()).isTrue();
        assertThat(result.code()).isEqualTo("BALANCED_NEUTRAL_5M");
        assertThat(result.positionPercent()).isEqualTo(25);
    }

    @Test
    void balancedNeutralFiveExceptionHonorsInclusiveQualityBoundaries() {
        settings("BALANCED", false);
        TradeSignal oneMinute = signal("SHIBUSDT", "1m", SignalDecision.BUY, now, 72, 23, 18, 13);
        latest("SHIBUSDT", "5m", signal("SHIBUSDT", "5m", SignalDecision.NEUTRAL, now.minusSeconds(120), 60, 15, 10, 8));
        latest("SHIBUSDT", "1h", signal("SHIBUSDT", "1h", SignalDecision.BUY, now.minusSeconds(1800), 82, 23, 17, 13));

        var result = service.validateBuy(oneMinute, 70);

        assertThat(result.allowed()).isTrue();
        assertThat(result.code()).isEqualTo("BALANCED_NEUTRAL_5M");
    }

    @Test
    void balancedNeutralFiveExceptionRejectsBelowConfidenceOrEntryQualityAndBtcConflict() {
        settings("BALANCED", false);
        TradeSignal five = signal("SHIBUSDT", "5m", SignalDecision.NEUTRAL, now.minusSeconds(120), 60, 15, 10, 8);
        TradeSignal one = signal("SHIBUSDT", "1h", SignalDecision.BUY, now.minusSeconds(1800), 82, 23, 17, 13);
        latest("SHIBUSDT", "5m", five);
        latest("SHIBUSDT", "1h", one);

        TradeSignal lowConfidence = signal("SHIBUSDT", "1m", SignalDecision.BUY, now, 71, 23, 18, 13);
        assertThat(service.validateBuy(lowConfidence, 75).code()).isEqualTo("BALANCED_CONFIRMATION_INSUFFICIENT");

        TradeSignal enoughConfidence = signal("SHIBUSDT", "1m", SignalDecision.BUY, now, 72, 23, 18, 13);
        assertThat(service.validateBuy(enoughConfidence, 69).code()).isEqualTo("BALANCED_CONFIRMATION_INSUFFICIENT");

        enoughConfidence.setBtcContextStatus(BtcContextStatus.CONFLICT);
        assertThat(service.validateBuy(enoughConfidence, 75).code()).isEqualTo("BALANCED_CONFIRMATION_INSUFFICIENT");

        enoughConfidence.setBtcContextStatus(BtcContextStatus.STRONG_CONFLICT);
        assertThat(service.validateBuy(enoughConfidence, 75).code()).isEqualTo("BALANCED_CONFIRMATION_INSUFFICIENT");
    }

    @Test
    void fix11gAllowsApprovedDirectBuyWithNeutralFiveAndWatchOneAtTwentyFivePercent() {
        settings("BALANCED", false);
        TradeSignal oneMinute = signal("EDUUSDT", "1m", SignalDecision.BUY, now, 68, 15, 20, 12);
        oneMinute.setFinalEntryAllowed(true);
        latest("EDUUSDT", "5m", signal("EDUUSDT", "5m", SignalDecision.NEUTRAL,
                now.minusSeconds(120), 72, 14, 10, 8));
        latest("EDUUSDT", "1h", signal("EDUUSDT", "1h", SignalDecision.WATCH,
                now.minusSeconds(1800), 73, 24, 14, 10));

        var result = service.validateBuy(oneMinute, 50);

        assertThat(result.allowed()).isTrue();
        assertThat(result.code()).isEqualTo("BALANCED_NEUTRAL_5M_WATCH_1H");
        assertThat(result.positionPercent()).isEqualTo(25);
    }

    @Test
    void fix11gKeepsExistingEntryQualityFinalDecisionAndBtcSafeguards() {
        settings("BALANCED", false);
        TradeSignal five = signal("EDUUSDT", "5m", SignalDecision.NEUTRAL,
                now.minusSeconds(120), 72, 14, 10, 8);
        TradeSignal one = signal("EDUUSDT", "1h", SignalDecision.WATCH,
                now.minusSeconds(1800), 73, 24, 14, 10);
        latest("EDUUSDT", "5m", five);
        latest("EDUUSDT", "1h", one);

        TradeSignal belowEntryQuality = signal("EDUUSDT", "1m", SignalDecision.BUY, now, 90, 15, 20, 12);
        belowEntryQuality.setFinalEntryAllowed(true);
        assertThat(service.validateBuy(belowEntryQuality, 49).code())
                .isEqualTo("BALANCED_CONFIRMATION_INSUFFICIENT");

        TradeSignal upstreamBlocked = signal("EDUUSDT", "1m", SignalDecision.BUY, now, 90, 15, 20, 12);
        upstreamBlocked.setFinalEntryAllowed(false);
        assertThat(service.validateBuy(upstreamBlocked, 80).code())
                .isEqualTo("BALANCED_CONFIRMATION_INSUFFICIENT");

        TradeSignal btcConflict = signal("EDUUSDT", "1m", SignalDecision.BUY, now, 90, 15, 20, 12);
        btcConflict.setFinalEntryAllowed(true);
        btcConflict.setBtcContextStatus(BtcContextStatus.CONFLICT);
        assertThat(service.validateBuy(btcConflict, 80).code())
                .isEqualTo("BALANCED_CONFIRMATION_INSUFFICIENT");

        btcConflict.setBtcContextStatus(BtcContextStatus.STRONG_CONFLICT);
        assertThat(service.validateBuy(btcConflict, 80).code())
                .isEqualTo("BALANCED_CONFIRMATION_INSUFFICIENT");
    }

    @Test
    void fix11gDoesNotBroadenAccumulatedEvidenceContextAuthority() {
        settings("BALANCED", false);
        TradeSignal reference = signal("EDUUSDT", "1m", SignalDecision.WATCH, now, 80, 15, 20, 12);
        latest("EDUUSDT", "1m", reference);
        latest("EDUUSDT", "5m", signal("EDUUSDT", "5m", SignalDecision.NEUTRAL,
                now.minusSeconds(120), 72, 14, 10, 8));
        latest("EDUUSDT", "1h", signal("EDUUSDT", "1h", SignalDecision.WATCH,
                now.minusSeconds(1800), 73, 24, 14, 10));

        var result = service.validateBuyContext(reference);

        assertThat(result.allowed()).isFalse();
        assertThat(result.code()).isEqualTo("BALANCED_CONFIRMATION_INSUFFICIENT");
    }

    @Test
    void fix11gReplayUsesTheSameProductionValidationMethodWithHistoricalContext() {
        settings("BALANCED", false);
        ExecutionReplayScope scope = new ExecutionReplayScope();
        ReflectionTestUtils.setField(service, "replayScope", scope);

        TradeSignal oneMinute = signal("EDUUSDT", "1m", SignalDecision.BUY, now, 68, 15, 20, 12);
        oneMinute.setFinalEntryAllowed(true);
        TradeSignal five = signal("EDUUSDT", "5m", SignalDecision.NEUTRAL,
                now.minusSeconds(120), 72, 14, 10, 8);
        TradeSignal one = signal("EDUUSDT", "1h", SignalDecision.WATCH,
                now.minusSeconds(1800), 73, 24, 14, 10);

        // Golden rule: Replay = Production. The replay scope supplies historical signals,
        // but validateBuy() is the exact same Production business method tested above.
        try (ExecutionReplayScope.Scope ignored = scope.open(11L, java.util.List.of(one, five, oneMinute), o -> {})) {
            scope.reference(now);
            var result = service.validateBuy(oneMinute, 50);

            assertThat(result.allowed()).isTrue();
            assertThat(result.code()).isEqualTo("BALANCED_NEUTRAL_5M_WATCH_1H");
            assertThat(result.positionPercent()).isEqualTo(25);
        }
    }

    @Test
    void balancedNeutralFiveExceptionDoesNotBroadenOtherRejectedCombinations() {
        settings("BALANCED", false);
        TradeSignal oneMinute = signal("BTCUSDT", "1m", SignalDecision.BUY, now, 80, 23, 18, 13);

        latest("BTCUSDT", "5m", signal("BTCUSDT", "5m", SignalDecision.NEUTRAL, now.minusSeconds(120), 60, 15, 10, 8));
        latest("BTCUSDT", "1h", signal("BTCUSDT", "1h", SignalDecision.WATCH, now.minusSeconds(1800), 68, 18, 13, 10));
        assertThat(service.validateBuy(oneMinute, 80).code()).isEqualTo("BALANCED_CONFIRMATION_INSUFFICIENT");

        latest("BTCUSDT", "1h", signal("BTCUSDT", "1h", SignalDecision.NEUTRAL, now.minusSeconds(1800), 60, 15, 10, 8));
        assertThat(service.validateBuy(oneMinute, 80).code()).isEqualTo("BALANCED_CONFIRMATION_INSUFFICIENT");
    }

    @Test
    void existingBalancedAuthorityIgnoresNewExceptionThresholds() {
        settings("BALANCED", false);
        TradeSignal oneMinute = signal("ETHUSDT", "1m", SignalDecision.BUY, now, 60, 15, 10, 8);
        latest("ETHUSDT", "5m", signal("ETHUSDT", "5m", SignalDecision.BUY, now.minusSeconds(120), 80, 23, 18, 13));
        latest("ETHUSDT", "1h", signal("ETHUSDT", "1h", SignalDecision.STRONG_BUY, now.minusSeconds(1800), 90, 25, 20, 15));

        var full = service.validateBuy(oneMinute, 50);
        assertThat(full.code()).isEqualTo("BALANCED_FULL");
        assertThat(full.positionPercent()).isEqualTo(100);

        latest("ETHUSDT", "5m", signal("ETHUSDT", "5m", SignalDecision.WATCH, now.minusSeconds(120), 68, 18, 13, 10));
        var strong = service.validateBuy(oneMinute, 50);
        assertThat(strong.code()).isEqualTo("BALANCED_STRONG");
        assertThat(strong.positionPercent()).isEqualTo(75);
    }

    @Test
    void sellKeepsExistingStrictConfirmationRules() {

        // FIX-112A test alignment:
        // SELL validation follows the existing SELL confirmation path and does not
        // consume BUY execution-profile settings in this scenario.
        TradeSignal oneMinute =
                signal("ETHUSDT", "1m", SignalDecision.SELL, now, 30, 15, 5, 4);

        latest(
                "ETHUSDT",
                "5m",
                signal(
                        "ETHUSDT",
                        "5m",
                        SignalDecision.SELL,
                        now.minusSeconds(120),
                        35,
                        10,
                        7,
                        5
                )
        );

        latest(
                "ETHUSDT",
                "1h",
                signal(
                        "ETHUSDT",
                        "1h",
                        SignalDecision.WATCH,
                        now.minusSeconds(1800),
                        65,
                        18,
                        12,
                        10
                )
        );

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
                .btcContextStatus(BtcContextStatus.CONFIRMED)
                .generatedAt(generatedAt)
                .build();
    }
}

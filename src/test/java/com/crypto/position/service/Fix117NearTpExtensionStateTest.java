package com.crypto.position.service;

import com.crypto.audit.service.ProductionExitAuditService;
import com.crypto.position.repository.PositionManagementEventRepository;
import com.crypto.repository.PaperPositionRepository;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.wallet.domain.WalletManagedPosition;
import com.crypto.wallet.repository.WalletManagedPositionRepository;
import com.crypto.wallet.service.WalletAutoExecutionService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * FIX-117 regression coverage for TP-extension interaction with persisted Near-TP state.
 * These tests intentionally exercise the extension-reset boundary separately from the
 * existing FIX-11T policy tests, which remain unchanged.
 */
class Fix117NearTpExtensionStateTest {

    private final NearTpFailureProtectionPolicy policy = new NearTpFailureProtectionPolicy();

    @Test
    void armedExtensionPreservesEarnedStateAndHistoricalBestPrice() throws Exception {
        WalletManagedPosition position = position(
                NearTpState.NEAR_TP_ARMED, "0.167600", 0, null, false);

        applyProductionExtensionStateRule(position);

        assertThat(position.getNearTpState()).isEqualTo(NearTpState.NEAR_TP_ARMED);
        assertThat(position.getNearTpBestPrice()).isEqualByComparingTo("0.167600");
        assertThat(position.getNearTpBearishStreak()).isZero();
        assertThat(position.getNearTpLastOneMinuteSignalId()).isNull();
    }

    @Test
    void rejectedExtensionWithNewGivebackBelowTwentyRecoversOnNextEvaluation() throws Exception {
        WalletManagedPosition position = position(
                NearTpState.NEAR_TP_REJECTION_DETECTED, "6.395", 2, 401L, false);

        applyProductionExtensionStateRule(position);

        // Immediate post-extension state: protection/best survive; old-geometry evidence does not.
        assertThat(position.getNearTpState()).isEqualTo(NearTpState.NEAR_TP_REJECTION_DETECTED);
        assertThat(position.getNearTpBestPrice()).isEqualByComparingTo("6.395");
        assertThat(position.getNearTpBearishStreak()).isZero();
        assertThat(position.getNearTpLastOneMinuteSignalId()).isNull();

        NearTpFailureProtectionPolicy.Evaluation next = policy.evaluate(
                state(position), new BigDecimal("6.266"), new BigDecimal("6.432"),
                new BigDecimal("6.380"), Instant.parse("2026-09-05T18:30:00Z"), null, null);

        assertThat(next.code()).isEqualTo("NEAR_TP_RECOVERY");
        assertThat(next.harvestEligible()).isFalse();
        assertThat(next.state().nearTpState()).isEqualTo(NearTpState.NEAR_TP_ARMED);
        assertThat(next.state().consecutiveBearishOneMinute()).isZero();
        assertThat(next.state().lastEvaluatedOneMinuteSignalId()).isNull();
    }

    @Test
    void rejectedExtensionWithGivebackStillAtLeastTwentyKeepsRejectionAndStartsEvidenceFresh() throws Exception {
        WalletManagedPosition position = position(
                NearTpState.NEAR_TP_REJECTION_DETECTED, "6.395", 2, 401L, false);

        applyProductionExtensionStateRule(position);

        NearTpFailureProtectionPolicy.Evaluation next = policy.evaluate(
                state(position), new BigDecimal("6.266"), new BigDecimal("6.366"),
                new BigDecimal("6.370"), Instant.parse("2026-09-05T18:30:00Z"), null, null);

        assertThat(next.state().nearTpState()).isEqualTo(NearTpState.NEAR_TP_REJECTION_DETECTED);
        assertThat(next.state().consecutiveBearishOneMinute()).isZero();
        assertThat(next.state().lastEvaluatedOneMinuteSignalId()).isNull();
        assertThat(next.harvestEligible()).isFalse();
    }

    @Test
    void inactiveExtensionKeepsExistingResetBehavior() throws Exception {
        WalletManagedPosition position = position(
                NearTpState.INACTIVE, "0.167000", 1, 100L, false);

        applyProductionExtensionStateRule(position);

        assertThat(position.getNearTpState()).isEqualTo(NearTpState.INACTIVE);
        assertThat(position.getNearTpBestPrice()).isNull();
        assertThat(position.getNearTpBearishStreak()).isZero();
        assertThat(position.getNearTpLastOneMinuteSignalId()).isNull();
    }

    @Test
    void failureConfirmedExtensionKeepsExistingResetBehavior() throws Exception {
        WalletManagedPosition position = position(
                NearTpState.NEAR_TP_FAILURE_CONFIRMED, "0.167600", 2, 999L, false);

        applyProductionExtensionStateRule(position);

        assertThat(position.getNearTpState()).isEqualTo(NearTpState.INACTIVE);
        assertThat(position.getNearTpBestPrice()).isNull();
        assertThat(position.getNearTpBearishStreak()).isZero();
        assertThat(position.getNearTpLastOneMinuteSignalId()).isNull();
    }

    @Test
    void productionAndReplayUseIdenticalTpExtensionStateTransitions() throws Exception {
        for (NearTpState input : NearTpState.values()) {
            boolean harvested = input == NearTpState.NEAR_TP_PARTIAL_HARVESTED;
            WalletManagedPosition production = position(input, "0.167600", 2, 777L, harvested);
            applyProductionExtensionStateRule(production);

            Object replay = replayPosition(input, harvested, new BigDecimal("0.167600"), 2, 777L);
            Method withTakeProfit = replay.getClass().getDeclaredMethod("withTakeProfit", BigDecimal.class);
            withTakeProfit.setAccessible(true);
            Object replayAfter = withTakeProfit.invoke(replay, new BigDecimal("0.168000"));

            assertThat(readReplay(replayAfter, "nearTpState")).isEqualTo(production.getNearTpState());
            assertDecimalEquals((BigDecimal) readReplay(replayAfter, "nearTpBestPrice"), production.getNearTpBestPrice());
            assertThat(readReplay(replayAfter, "nearTpBearishStreak")).isEqualTo(production.getNearTpBearishStreak());
            assertThat(readReplay(replayAfter, "nearTpLastOneMinuteSignalId")).isEqualTo(production.getNearTpLastOneMinuteSignalId());
            assertThat(readReplay(replayAfter, "nearTpHarvestUsed")).isEqualTo(production.isNearTpHarvestUsed());
        }
    }

    private WalletManagedPosition position(NearTpState state, String bestPrice, int streak,
                                           Long lastSignalId, boolean harvestUsed) {
        return WalletManagedPosition.builder()
                .id(978L)
                .symbol("ENAUSDT")
                .quantity(new BigDecimal("3020.703520629890"))
                .averageEntryPriceUsdt(new BigDecimal("0.165524354372"))
                .totalCostUsdt(new BigDecimal("500.000000001490"))
                .stopLossUsdt(new BigDecimal("0.165070500000"))
                .takeProfitUsdt(new BigDecimal("0.167083234749"))
                .nearTpState(state)
                .nearTpBestPrice(bestPrice == null ? null : new BigDecimal(bestPrice))
                .nearTpBearishStreak(streak)
                .nearTpLastOneMinuteSignalId(lastSignalId)
                .nearTpHarvestUsed(harvestUsed)
                .nearTpHarvestedQuantity(BigDecimal.ZERO)
                .status("OPEN")
                .openedAt(Instant.parse("2026-09-05T17:52:23Z"))
                .updatedAt(Instant.parse("2026-09-05T18:20:52Z"))
                .build();
    }

    private NearTpFailureProtectionPolicy.State state(WalletManagedPosition position) {
        return new NearTpFailureProtectionPolicy.State(
                position.getNearTpState(), position.getNearTpBestPrice(),
                position.getNearTpBearishStreak(), position.getNearTpLastOneMinuteSignalId(),
                position.isNearTpHarvestUsed());
    }

    private void applyProductionExtensionStateRule(WalletManagedPosition position) throws Exception {
        LivePositionProtectionService service = new LivePositionProtectionService(
                mock(WalletManagedPositionRepository.class), mock(PaperPositionRepository.class),
                mock(DynamicProfitLockService.class), mock(PositionContinuationPolicy.class),
                mock(PositionExitPolicy.class), mock(TradeSignalRepository.class),
                mock(WalletAutoExecutionService.class), mock(ProductionExitAuditService.class),
                mock(PositionManagementEventRepository.class), mock(NearTpFailureProtectionPolicy.class));
        Method method = LivePositionProtectionService.class
                .getDeclaredMethod("resetNearTpTrackingForNewRiskGeometry", WalletManagedPosition.class);
        method.setAccessible(true);
        method.invoke(service, position);
    }

    private Object replayPosition(NearTpState state, boolean harvestUsed, BigDecimal bestPrice,
                                  int streak, Long lastSignalId) throws Exception {
        Class<?> type = Class.forName("com.crypto.regression.service.ShadowProductionReplayService$ShadowPosition");
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        RecordComponent[] components = type.getRecordComponents();
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            args[i] = replayValue(components[i].getName(), state, harvestUsed, bestPrice, streak, lastSignalId);
        }
        return constructor.newInstance(args);
    }

    private Object replayValue(String name, NearTpState state, boolean harvestUsed, BigDecimal bestPrice,
                               int streak, Long lastSignalId) {
        return switch (name) {
            case "positionId" -> 978L;
            case "entryTime" -> Instant.parse("2026-09-05T17:52:23Z");
            case "entryPrice" -> new BigDecimal("0.165524354372");
            case "quantity" -> new BigDecimal("3020.703520629890");
            case "cost" -> new BigDecimal("500.000000001490");
            case "positionPercent" -> 100;
            case "stopLoss" -> new BigDecimal("0.165070500000");
            case "takeProfit" -> new BigDecimal("0.167083234749");
            case "highest" -> new BigDecimal("0.167600");
            case "profitLockActive" -> false;
            case "profitLockPrice" -> null;
            // FIX-121 validation repair: the FIX-118/120 record carries explicit lock lifecycle
            // fields. Keep this Near-TP fixture inactive; retain strict handling of unknown fields.
            case "profitLockState" -> ProfitLockState.INACTIVE;
            case "profitLockRebaseStartedAt" -> null;
            case "entryScore" -> 75;
            case "entryConfidence" -> 84;
            case "entryTrend", "entryStructure", "entryMomentum", "entryVolume" -> 0;
            case "nearTpState" -> state;
            case "nearTpBestPrice" -> bestPrice;
            case "nearTpBearishStreak" -> streak;
            case "nearTpLastOneMinuteSignalId" -> lastSignalId;
            case "nearTpHarvestUsed" -> harvestUsed;
            case "nearTpHarvestedQuantity", "partialRealizedPnl", "partialHarvestCostBasis" -> BigDecimal.ZERO;
            default -> throw new IllegalArgumentException("Unhandled ShadowPosition component: " + name);
        };
    }

    private Object readReplay(Object replay, String accessor) throws Exception {
        Method method = replay.getClass().getDeclaredMethod(accessor);
        method.setAccessible(true);
        return method.invoke(replay);
    }

    private void assertDecimalEquals(BigDecimal actual, BigDecimal expected) {
        if (expected == null) {
            assertThat(actual).isNull();
        } else {
            assertThat(actual).isNotNull();
            assertThat(actual).isEqualByComparingTo(expected);
        }
    }
}

package com.crypto.position.service;

import com.crypto.domain.TradeSignal;
import com.crypto.wallet.domain.WalletManagedPosition;
import com.crypto.wallet.domain.WalletSettings;
import com.crypto.wallet.repository.WalletManagedPositionRepository;
import com.crypto.wallet.repository.WalletSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicProfitLockServiceTest {

    @Mock private WalletManagedPositionRepository positionRepository;
    @Mock private WalletSettingsRepository settingsRepository;

    private DynamicProfitLockService service;
    private WalletManagedPosition position;

    @BeforeEach
    void setUp() {
        service = new DynamicProfitLockService(positionRepository, settingsRepository, new ProfitLockPolicy());
        position = WalletManagedPosition.builder()
                .id(1L)
                .symbol("ETHUSDT")
                .quantity(BigDecimal.ONE)
                .averageEntryPriceUsdt(new BigDecimal("1912.62"))
                .totalCostUsdt(new BigDecimal("1912.62"))
                .stopLossUsdt(new BigDecimal("1908.00"))
                .takeProfitUsdt(new BigDecimal("1918.79430386"))
                .highestPriceUsdt(new BigDecimal("1912.62"))
                .profitLockActive(false)
                .profitLockState(ProfitLockState.INACTIVE)
                .profitLockProgressPercent(BigDecimal.ZERO)
                .status("OPEN")
                .openedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(settingsRepository.findById(1L)).thenReturn(Optional.of(WalletSettings.builder()
                .id(1L)
                .dynamicProfitLockEnabled(true)
                .profitLockActivationPercent(new BigDecimal("70"))
                .profitLockInitialPercent(new BigDecimal("40"))
                .profitLockTrailStepPercent(new BigDecimal("10"))
                .build()));
    }

    private void stubEthOpenPosition() {
        // Test fixture only: stub the default ETH open position only in tests that actually use it.
        // This keeps Mockito strict-stubbing enabled without hiding unused setup with lenient().
        when(positionRepository.findFirstBySymbolAndStatusOrderByOpenedAtDesc("ETHUSDT", "OPEN"))
                .thenReturn(Optional.of(position));
    }

    @Test
    void activatesAndTrailsAfterEightyPercentOfTargetDistance() {
        stubEthOpenPosition();
        TradeSignal signal = TradeSignal.builder()
                .id(100L).symbol("ETHUSDT").latestPrice(new BigDecimal("1917.67")).build();

        var result = service.evaluate(signal);

        assertThat(result.active()).isTrue();
        assertThat(result.triggered()).isFalse();
        assertThat(result.progressPercent()).isGreaterThan(new BigDecimal("80"));
        assertThat(result.lockPrice()).isGreaterThan(new BigDecimal("1915.70"));
        assertThat(position.isProfitLockActive()).isTrue();
    }

    @Test
    void entryQualityDoesNotOverrideAdministrationProfitLockPercentages() {
        position.setEntryTotalScore(75);
        position.setEntryConfidence(77);
        position.setAverageEntryPriceUsdt(new BigDecimal("0.036200"));
        position.setTakeProfitUsdt(new BigDecimal("0.036449366297"));
        position.setHighestPriceUsdt(new BigDecimal("0.036200"));

        when(positionRepository.findFirstBySymbolAndStatusOrderByOpenedAtDesc("EDUUSDT", "OPEN"))
                .thenReturn(Optional.of(position));
        position.setSymbol("EDUUSDT");

        // ~40% progress: old adaptive logic activated here. Admin says 70%, so it must remain inactive.
        var result = service.evaluatePrice("EDUUSDT", new BigDecimal("0.036300"));

        assertThat(result.active()).isFalse();
        assertThat(result.activationPercent()).isEqualByComparingTo("70");
        assertThat(result.explanation()).contains("ADMIN_CONFIG").contains("activation=70%");
    }

    @Test
    void highEntryQualityStillUsesAdministrationPercentages() {
        stubEthOpenPosition();
        position.setEntryTotalScore(88);
        position.setEntryConfidence(90);

        var result = service.evaluatePrice("ETHUSDT", new BigDecimal("1916.95"));

        assertThat(result.activationPercent()).isEqualByComparingTo("70");
        assertThat(result.explanation()).contains("ADMIN_CONFIG");
    }

    @Test
    void triggersWhenPriceFallsThroughPreviouslyProtectedLevel() {
        stubEthOpenPosition();
        position.setHighestPriceUsdt(new BigDecimal("1917.67"));
        position.setProfitLockActive(true);
        position.setProfitLockPriceUsdt(new BigDecimal("1915.70715193"));
        position.setProfitLockProgressPercent(new BigDecimal("81.79"));
        position.setProfitLockActivatedAt(Instant.now().minusSeconds(60));
        TradeSignal signal = TradeSignal.builder()
                .id(101L).symbol("ETHUSDT").latestPrice(new BigDecimal("1915.40")).build();

        var result = service.evaluate(signal);

        assertThat(result.active()).isTrue();
        assertThat(result.triggered()).isTrue();
        assertThat(result.lockPrice()).isEqualByComparingTo(new BigDecimal("1915.70715193"));
    }
    @Test
    void remainsTriggeredWhenPriceFallsBelowMinimumProfitFloorAfterLockWasActive() {
        stubEthOpenPosition();
        position.setHighestPriceUsdt(new BigDecimal("1917.67"));
        position.setProfitLockActive(true);
        position.setProfitLockPriceUsdt(new BigDecimal("1915.70715193"));
        position.setProfitLockProgressPercent(new BigDecimal("81.79"));
        position.setProfitLockActivatedAt(Instant.now().minusSeconds(60));

        var result = service.evaluatePrice("ETHUSDT", new BigDecimal("1912.80"));

        assertThat(result.active()).isTrue();
        assertThat(result.triggered()).isTrue();
    }

    @Test
    void fix118ActiveLockEntersRebaseWhenExtendedGeometryFallsBelowActivation() {
        position.setAverageEntryPriceUsdt(new BigDecimal("2.700"));
        position.setTakeProfitUsdt(new BigDecimal("2.719244613453"));
        position.setHighestPriceUsdt(new BigDecimal("2.719"));
        position.setProfitLockActive(true);
        position.setProfitLockState(ProfitLockState.ACTIVE);
        position.setProfitLockPriceUsdt(new BigDecimal("2.711546768072"));
        position.setProfitLockProgressPercent(new BigDecimal("103.925000"));
        Instant changedAt = Instant.parse("2026-09-06T13:00:55.693804Z");

        var transition = service.onTakeProfitExtended(
                position, new BigDecimal("2.728866920180"), new BigDecimal("2.720"), changedAt);

        assertThat(transition.previousState()).isEqualTo(ProfitLockState.ACTIVE);
        assertThat(transition.state()).isEqualTo(ProfitLockState.TP_EXTENSION_REBASE);
        assertThat(transition.progressPercent()).isLessThan(new BigDecimal("70"));
        assertThat(position.getProfitLockState()).isEqualTo(ProfitLockState.TP_EXTENSION_REBASE);
        assertThat(position.getHighestPriceUsdt()).isEqualByComparingTo("2.720");
        assertThat(position.isProfitLockActive()).isTrue();
        assertThat(position.getProfitLockPriceUsdt()).isEqualByComparingTo("2.711546768072");
        assertThat(position.getProfitLockRebaseStartedAt()).isEqualTo(changedAt);
    }

    @Test
    void fix118RebaseMakesHistoricalLockNonExecutableUntilNewGeometryReactivates() {
        stubEthOpenPosition();
        position.setAverageEntryPriceUsdt(new BigDecimal("100"));
        position.setTakeProfitUsdt(new BigDecimal("130"));
        position.setHighestPriceUsdt(new BigDecimal("115"));
        position.setProfitLockActive(true);
        position.setProfitLockState(ProfitLockState.TP_EXTENSION_REBASE);
        position.setProfitLockPriceUsdt(new BigDecimal("112"));
        position.setProfitLockProgressPercent(new BigDecimal("50"));
        position.setProfitLockRebaseStartedAt(Instant.parse("2026-09-06T13:00:55Z"));

        var result = service.evaluatePrice("ETHUSDT", new BigDecimal("111"));

        assertThat(result.rebasing()).isTrue();
        assertThat(result.active()).isFalse();
        assertThat(result.triggered()).isFalse();
        assertThat(result.lockPrice()).isEqualByComparingTo("112");
        assertThat(position.isProfitLockActive()).isTrue();
        assertThat(position.getProfitLockState()).isEqualTo(ProfitLockState.TP_EXTENSION_REBASE);
    }

    @Test
    void fix118RebaseReactivatesAtConfiguredNewGeometryActivation() {
        stubEthOpenPosition();
        position.setAverageEntryPriceUsdt(new BigDecimal("100"));
        position.setTakeProfitUsdt(new BigDecimal("130"));
        position.setHighestPriceUsdt(new BigDecimal("115"));
        position.setProfitLockActive(true);
        position.setProfitLockState(ProfitLockState.TP_EXTENSION_REBASE);
        // Deliberately stale/tighter historical lock. Reactivation must rebuild the lock
        // from the current TP geometry instead of keeping this superseded value.
        position.setProfitLockPriceUsdt(new BigDecimal("115"));
        position.setProfitLockRebaseStartedAt(Instant.parse("2026-09-06T13:00:55Z"));

        var result = service.evaluatePrice("ETHUSDT", new BigDecimal("121"));

        assertThat(result.progressPercent()).isGreaterThanOrEqualTo(new BigDecimal("70"));
        assertThat(result.state()).isEqualTo(ProfitLockState.ACTIVE);
        assertThat(result.active()).isTrue();
        assertThat(result.lockPrice()).isEqualByComparingTo("112");
        assertThat(position.getProfitLockPriceUsdt()).isEqualByComparingTo("112");
        assertThat(position.getProfitLockState()).isEqualTo(ProfitLockState.ACTIVE);
        assertThat(position.getProfitLockRebaseStartedAt()).isNull();
    }

    @Test
    void fix118RebaseHasStandaloneProtectedProfitFloor() {
        stubEthOpenPosition();
        position.setAverageEntryPriceUsdt(new BigDecimal("100"));
        position.setTakeProfitUsdt(new BigDecimal("130"));
        position.setHighestPriceUsdt(new BigDecimal("115"));
        position.setProfitLockActive(true);
        position.setProfitLockState(ProfitLockState.TP_EXTENSION_REBASE);
        position.setProfitLockPriceUsdt(new BigDecimal("112"));
        position.setProfitLockRebaseStartedAt(Instant.parse("2026-09-06T13:00:55Z"));

        var result = service.evaluatePrice("ETHUSDT", new BigDecimal("100.01"));

        assertThat(result.rebasing()).isTrue();
        assertThat(result.triggered()).isFalse();
        assertThat(result.hardProfitFloor()).isEqualByComparingTo("100.0500");
        assertThat(result.hardProfitFloorTriggered()).isTrue();
    }


    @Test
    void fix118HistoricalIcp1010ExtendedTpMakesOldLockNonExecutableAtActualExitPrice() {
        BigDecimal newTarget = new BigDecimal("2.728866920180");
        Instant extensionAt = Instant.parse("2026-09-06T13:00:55.693804Z");

        position.setId(1010L);
        position.setSymbol("ICPUSDT");
        position.setAverageEntryPriceUsdt(new BigDecimal("2.700"));
        position.setTakeProfitUsdt(new BigDecimal("2.719244613453"));
        position.setHighestPriceUsdt(new BigDecimal("2.719"));
        position.setProfitLockActive(true);
        position.setProfitLockState(ProfitLockState.ACTIVE);
        position.setProfitLockPriceUsdt(new BigDecimal("2.711546768072"));
        position.setProfitLockProgressPercent(new BigDecimal("103.925000"));
        position.setProfitLockActivatedAt(Instant.parse("2026-09-06T13:00:19.595302Z"));

        var transition = service.onTakeProfitExtended(
                position, newTarget, new BigDecimal("2.720"), extensionAt);
        position.setTakeProfitUsdt(newTarget);

        when(positionRepository.findFirstBySymbolAndStatusOrderByOpenedAtDesc("ICPUSDT", "OPEN"))
                .thenReturn(Optional.of(position));

        // Historical production exit was 2.710 at 13:01:03Z. Under FIX-118 the lock
        // earned against the superseded TP must be persisted but non-executable here.
        var result = service.evaluatePrice("ICPUSDT", new BigDecimal("2.710"));

        assertThat(transition.state()).isEqualTo(ProfitLockState.TP_EXTENSION_REBASE);
        assertThat(transition.progressPercent()).isEqualByComparingTo("69.283456");
        assertThat(position.getProfitLockState()).isEqualTo(ProfitLockState.TP_EXTENSION_REBASE);
        assertThat(position.getProfitLockPriceUsdt()).isEqualByComparingTo("2.711546768072");
        assertThat(result.rebasing()).isTrue();
        assertThat(result.active()).isFalse();
        assertThat(result.triggered()).isFalse();
        assertThat(result.hardProfitFloorTriggered()).isFalse();
    }

    @Test
    void fix118HistoricalIcp1014FourthExtensionMakesOldLockNonExecutableAtActualExitPrice() {
        BigDecimal finalTarget = new BigDecimal("2.845204291157");
        Instant extensionAt = Instant.parse("2026-09-06T14:05:27Z");

        position.setId(1014L);
        position.setSymbol("ICPUSDT");
        position.setAverageEntryPriceUsdt(new BigDecimal("2.727"));
        position.setTakeProfitUsdt(new BigDecimal("2.805802860771"));
        position.setHighestPriceUsdt(new BigDecimal("2.806"));
        position.setProfitLockActive(true);
        position.setProfitLockState(ProfitLockState.ACTIVE);
        position.setProfitLockPriceUsdt(new BigDecimal("2.774281716463"));
        position.setProfitLockProgressPercent(new BigDecimal("100.250000"));
        position.setProfitLockActivatedAt(Instant.parse("2026-09-06T13:47:11Z"));

        var transition = service.onTakeProfitExtended(
                position, finalTarget, new BigDecimal("2.806"), extensionAt);
        position.setTakeProfitUsdt(finalTarget);

        when(positionRepository.findFirstBySymbolAndStatusOrderByOpenedAtDesc("ICPUSDT", "OPEN"))
                .thenReturn(Optional.of(position));

        // Historical production exit was 2.766. It is below the old lock 2.774281716463,
        // but still safely above the FIX-118 protected-profit floor for the rebase window.
        var result = service.evaluatePrice("ICPUSDT", new BigDecimal("2.766"));

        assertThat(transition.state()).isEqualTo(ProfitLockState.TP_EXTENSION_REBASE);
        assertThat(transition.progressPercent()).isEqualByComparingTo("66.833445");
        assertThat(position.getProfitLockPriceUsdt()).isEqualByComparingTo("2.774281716463");
        assertThat(result.rebasing()).isTrue();
        assertThat(result.active()).isFalse();
        assertThat(result.triggered()).isFalse();
        assertThat(result.hardProfitFloorTriggered()).isFalse();
    }


    @Test
    void fix120HistoricalBico1055ExtensionRecalculatesActiveLockAgainstNewTargetGeometry() {
        BigDecimal finalTarget = new BigDecimal("0.023446298101");
        Instant extensionAt = Instant.parse("2026-09-07T13:25:56.572756Z");

        position.setId(1055L);
        position.setSymbol("BICOUSDT");
        position.setAverageEntryPriceUsdt(new BigDecimal("0.022990"));
        position.setTakeProfitUsdt(new BigDecimal("0.023294198734"));
        position.setHighestPriceUsdt(new BigDecimal("0.023310"));
        position.setProfitLockActive(true);
        position.setProfitLockState(ProfitLockState.ACTIVE);
        position.setProfitLockPriceUsdt(new BigDecimal("0.023218149051"));
        position.setProfitLockProgressPercent(new BigDecimal("105.193000"));
        position.setProfitLockActivatedAt(Instant.parse("2026-09-07T13:25:34.084899Z"));

        var transition = service.onTakeProfitExtended(
                position, finalTarget, new BigDecimal("0.023310"), extensionAt);
        position.setTakeProfitUsdt(finalTarget);

        when(positionRepository.findFirstBySymbolAndStatusOrderByOpenedAtDesc("BICOUSDT", "OPEN"))
                .thenReturn(Optional.of(position));

        // FIX-120 historical BICO #1055 final extension left progress at 70.129593%, barely above
        // activation. The old lock (0.023218149051) was earned against the previous TP and
        // must be replaced by a fresh current-geometry lock (40% of the new TP distance).
        assertThat(transition.previousState()).isEqualTo(ProfitLockState.ACTIVE);
        assertThat(transition.state()).isEqualTo(ProfitLockState.ACTIVE);
        assertThat(transition.progressPercent()).isEqualByComparingTo("70.129593");
        assertThat(position.getProfitLockPriceUsdt()).isEqualByComparingTo("0.023172519240");
        assertThat(position.getProfitLockRebaseStartedAt()).isNull();

        // Production historically exited around 0.023200 because the stale old lock was
        // 0.023218149051. Under the corrected geometry that same price must not trigger DPL.
        var result = service.evaluatePrice("BICOUSDT", new BigDecimal("0.023200"));

        assertThat(result.state()).isEqualTo(ProfitLockState.ACTIVE);
        assertThat(result.active()).isTrue();
        assertThat(result.lockPrice()).isEqualByComparingTo("0.023172519240");
        assertThat(result.triggered()).isFalse();
    }



    @Test
    void fix120GeometryBoundaryMayLegitimatelyLowerFreshLock() {
        position.setAverageEntryPriceUsdt(new BigDecimal("100"));
        position.setTakeProfitUsdt(new BigDecimal("110"));
        position.setHighestPriceUsdt(new BigDecimal("111"));
        position.setProfitLockActive(true);
        position.setProfitLockState(ProfitLockState.ACTIVE);
        position.setProfitLockPriceUsdt(new BigDecimal("108")); // valid 80% lock under old 110 target
        position.setProfitLockProgressPercent(new BigDecimal("110"));

        var transition = service.onTakeProfitExtended(
                position, new BigDecimal("115"), new BigDecimal("111"), Instant.parse("2026-09-07T18:00:00Z"));

        assertThat(transition.state()).isEqualTo(ProfitLockState.ACTIVE);
        assertThat(transition.progressPercent()).isEqualByComparingTo("73.333333");
        assertThat(position.getProfitLockPriceUsdt()).isEqualByComparingTo("106.000000000000");
        assertThat(position.getProfitLockPriceUsdt()).isLessThan(new BigDecimal("108"));
    }

    @Test
    void fix120GeometryBoundaryMayLegitimatelyRaiseFreshLock() {
        position.setAverageEntryPriceUsdt(new BigDecimal("100"));
        position.setTakeProfitUsdt(new BigDecimal("110"));
        position.setHighestPriceUsdt(new BigDecimal("107"));
        position.setProfitLockActive(true);
        position.setProfitLockState(ProfitLockState.ACTIVE);
        position.setProfitLockPriceUsdt(new BigDecimal("104")); // valid initial lock at old 70% activation
        position.setProfitLockProgressPercent(new BigDecimal("70"));

        var transition = service.onTakeProfitExtended(
                position, new BigDecimal("112"), new BigDecimal("111"), Instant.parse("2026-09-07T18:01:00Z"));

        assertThat(transition.state()).isEqualTo(ProfitLockState.ACTIVE);
        assertThat(transition.progressPercent()).isEqualByComparingTo("91.666667");
        assertThat(position.getProfitLockPriceUsdt()).isEqualByComparingTo("107.200000000000");
        assertThat(position.getProfitLockPriceUsdt()).isGreaterThan(new BigDecimal("104"));
    }

    @Test
    void fix120NormalMonotonicTrailingResumesAfterFreshGeometryBaseline() {
        position.setSymbol("ETHUSDT");
        position.setAverageEntryPriceUsdt(new BigDecimal("100"));
        position.setTakeProfitUsdt(new BigDecimal("110"));
        position.setHighestPriceUsdt(new BigDecimal("109"));
        position.setProfitLockActive(true);
        position.setProfitLockState(ProfitLockState.ACTIVE);
        position.setProfitLockPriceUsdt(new BigDecimal("106"));
        position.setProfitLockProgressPercent(new BigDecimal("90"));

        service.onTakeProfitExtended(
                position, new BigDecimal("112"), new BigDecimal("109"), Instant.parse("2026-09-07T18:02:00Z"));
        position.setTakeProfitUsdt(new BigDecimal("112"));
        stubEthOpenPosition();

        BigDecimal freshBaseline = position.getProfitLockPriceUsdt();
        var retrace = service.evaluatePrice("ETHUSDT", new BigDecimal("108"));
        assertThat(retrace.lockPrice()).isEqualByComparingTo(freshBaseline);

        var advance = service.evaluatePrice("ETHUSDT", new BigDecimal("110.8"));
        assertThat(advance.lockPrice()).isGreaterThan(freshBaseline);
        BigDecimal tightened = advance.lockPrice();

        var secondRetrace = service.evaluatePrice("ETHUSDT", new BigDecimal("107"));
        assertThat(secondRetrace.lockPrice()).isEqualByComparingTo(tightened);
    }

    @Test
    void fix118RepeatedExtensionWhileAlreadyRebasingKeepsOriginalRebaseTimestamp() {
        Instant firstExtensionAt = Instant.parse("2026-09-06T13:48:13Z");
        Instant secondExtensionAt = Instant.parse("2026-09-06T13:51:37Z");

        position.setId(1014L);
        position.setSymbol("ICPUSDT");
        position.setAverageEntryPriceUsdt(new BigDecimal("2.727"));
        position.setTakeProfitUsdt(new BigDecimal("2.750348995784"));
        position.setHighestPriceUsdt(new BigDecimal("2.750348995784"));
        position.setProfitLockActive(true);
        position.setProfitLockState(ProfitLockState.ACTIVE);
        position.setProfitLockPriceUsdt(new BigDecimal("2.741009397470"));

        var first = service.onTakeProfitExtended(
                position,
                new BigDecimal("2.762023493676"),
                new BigDecimal("2.750348995784"),
                firstExtensionAt);
        position.setTakeProfitUsdt(new BigDecimal("2.762023493676"));

        var second = service.onTakeProfitExtended(
                position,
                new BigDecimal("2.779535240514"),
                new BigDecimal("2.762023493676"),
                secondExtensionAt);

        assertThat(first.state()).isEqualTo(ProfitLockState.TP_EXTENSION_REBASE);
        assertThat(second.previousState()).isEqualTo(ProfitLockState.TP_EXTENSION_REBASE);
        assertThat(second.state()).isEqualTo(ProfitLockState.TP_EXTENSION_REBASE);
        assertThat(second.progressPercent()).isEqualByComparingTo("66.666667");
        assertThat(position.getProfitLockRebaseStartedAt()).isEqualTo(firstExtensionAt);
        assertThat(position.getHighestPriceUsdt()).isEqualByComparingTo("2.762023493676");
        assertThat(position.isProfitLockActive()).isTrue();
    }

}

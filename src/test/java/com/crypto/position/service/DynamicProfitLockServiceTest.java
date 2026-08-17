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
        when(positionRepository.findFirstBySymbolAndStatusOrderByOpenedAtDesc("ETHUSDT", "OPEN"))
                .thenReturn(Optional.of(position));
    }

    @Test
    void activatesAndTrailsAfterEightyPercentOfTargetDistance() {
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
        position.setEntryTotalScore(88);
        position.setEntryConfidence(90);

        var result = service.evaluatePrice("ETHUSDT", new BigDecimal("1916.95"));

        assertThat(result.activationPercent()).isEqualByComparingTo("70");
        assertThat(result.explanation()).contains("ADMIN_CONFIG");
    }

    @Test
    void triggersWhenPriceFallsThroughPreviouslyProtectedLevel() {
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
        position.setHighestPriceUsdt(new BigDecimal("1917.67"));
        position.setProfitLockActive(true);
        position.setProfitLockPriceUsdt(new BigDecimal("1915.70715193"));
        position.setProfitLockProgressPercent(new BigDecimal("81.79"));
        position.setProfitLockActivatedAt(Instant.now().minusSeconds(60));

        var result = service.evaluatePrice("ETHUSDT", new BigDecimal("1912.80"));

        assertThat(result.active()).isTrue();
        assertThat(result.triggered()).isTrue();
    }

}

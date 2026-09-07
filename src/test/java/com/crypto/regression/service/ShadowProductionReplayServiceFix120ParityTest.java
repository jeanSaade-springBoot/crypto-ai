package com.crypto.regression.service;

import com.crypto.execution.service.ExecutionIntelligenceService;
import com.crypto.execution.service.ExecutionPriceAuthorityService;
import com.crypto.execution.service.ExecutionReplayScope;
import com.crypto.position.service.DynamicProfitLockService;
import com.crypto.position.service.NearTpFailureProtectionPolicy;
import com.crypto.position.service.NearTpState;
import com.crypto.position.service.PositionContinuationPolicy;
import com.crypto.position.service.PositionExitPolicy;
import com.crypto.position.service.PositionPriceAuthorityPolicy;
import com.crypto.position.service.ProfitLockPolicy;
import com.crypto.position.service.ProfitLockState;
import com.crypto.service.TradeExecutionValidationService;
import com.crypto.wallet.domain.WalletManagedPosition;
import com.crypto.wallet.domain.WalletSettings;
import com.crypto.wallet.repository.WalletManagedPositionRepository;
import com.crypto.wallet.repository.WalletSettingsRepository;
import com.crypto.wallet.service.BinanceMinimumExecutionPolicy;
import com.crypto.wallet.service.WalletExecutionSizingPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShadowProductionReplayServiceFix120ParityTest {

    private final ProfitLockPolicy policy = new ProfitLockPolicy();

    @Test
    void fix120ProductionAndReplayMatchForBicoActiveToActiveFreshGeometryRebase() throws Exception {
        WalletSettingsRepository settingsRepository = settingsRepository();
        WalletManagedPositionRepository positionRepository = mock(WalletManagedPositionRepository.class);
        DynamicProfitLockService production = new DynamicProfitLockService(positionRepository, settingsRepository, policy);

        WalletManagedPosition position = WalletManagedPosition.builder()
                .id(1055L)
                .symbol("BICOUSDT")
                .averageEntryPriceUsdt(new BigDecimal("0.022990"))
                .takeProfitUsdt(new BigDecimal("0.023294198734"))
                .highestPriceUsdt(new BigDecimal("0.023310"))
                .profitLockActive(true)
                .profitLockState(ProfitLockState.ACTIVE)
                .profitLockPriceUsdt(new BigDecimal("0.023218149051"))
                .profitLockProgressPercent(new BigDecimal("105.193000"))
                .status("OPEN")
                .build();

        BigDecimal newTarget = new BigDecimal("0.023446298101");
        BigDecimal extensionPrice = new BigDecimal("0.023310");
        Instant at = Instant.parse("2026-09-07T13:25:56.572756Z");
        var productionTransition = production.onTakeProfitExtended(position, newTarget, extensionPrice, at);

        ShadowProductionReplayService replay = replayService(settingsRepository);
        Object replayPosition = shadowPosition(
                1055L, new BigDecimal("0.022990"), new BigDecimal("0.023294198734"),
                new BigDecimal("0.023310"), true, new BigDecimal("0.023218149051"),
                ProfitLockState.ACTIVE, null);
        Object replayResult = invokeRebase(replay, replayPosition, newTarget, extensionPrice, at);

        assertThat(productionTransition.state()).isEqualTo(ProfitLockState.ACTIVE);
        assertThat(position.getProfitLockPriceUsdt()).isEqualByComparingTo("0.023172519240");
        assertThat(readState(replayResult)).isEqualTo(productionTransition.state());
        assertThat(readDecimal(replayResult, "profitLockPrice")).isEqualByComparingTo(position.getProfitLockPriceUsdt());
        assertThat(readDecimal(replayResult, "highest")).isEqualByComparingTo(position.getHighestPriceUsdt());
    }

    @Test
    void fix120ProductionAndReplayMatchForActiveToRebase() throws Exception {
        WalletSettingsRepository settingsRepository = settingsRepository();
        WalletManagedPositionRepository positionRepository = mock(WalletManagedPositionRepository.class);
        DynamicProfitLockService production = new DynamicProfitLockService(positionRepository, settingsRepository, policy);

        Instant at = Instant.parse("2026-09-06T13:00:55.693804Z");
        BigDecimal newTarget = new BigDecimal("2.728866920180");
        BigDecimal extensionPrice = new BigDecimal("2.720");
        WalletManagedPosition position = WalletManagedPosition.builder()
                .id(1010L).symbol("ICPUSDT")
                .averageEntryPriceUsdt(new BigDecimal("2.700"))
                .takeProfitUsdt(new BigDecimal("2.719244613453"))
                .highestPriceUsdt(new BigDecimal("2.719"))
                .profitLockActive(true).profitLockState(ProfitLockState.ACTIVE)
                .profitLockPriceUsdt(new BigDecimal("2.711546768072"))
                .status("OPEN").build();

        var productionTransition = production.onTakeProfitExtended(position, newTarget, extensionPrice, at);

        ShadowProductionReplayService replay = replayService(settingsRepository);
        Object replayPosition = shadowPosition(
                1010L, new BigDecimal("2.700"), new BigDecimal("2.719244613453"),
                new BigDecimal("2.719"), true, new BigDecimal("2.711546768072"),
                ProfitLockState.ACTIVE, null);
        Object replayResult = invokeRebase(replay, replayPosition, newTarget, extensionPrice, at);

        assertThat(readState(replayResult)).isEqualTo(productionTransition.state());
        assertThat(readState(replayResult)).isEqualTo(ProfitLockState.TP_EXTENSION_REBASE);
        assertThat(readDecimal(replayResult, "profitLockPrice")).isEqualByComparingTo(position.getProfitLockPriceUsdt());
        assertThat(readInstant(replayResult, "profitLockRebaseStartedAt")).isEqualTo(at);
    }

    private WalletSettingsRepository settingsRepository() {
        WalletSettingsRepository repository = mock(WalletSettingsRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.of(WalletSettings.builder()
                .id(1L)
                .dynamicProfitLockEnabled(true)
                .profitLockActivationPercent(new BigDecimal("70"))
                .profitLockInitialPercent(new BigDecimal("40"))
                .profitLockTrailStepPercent(new BigDecimal("10"))
                .build()));
        return repository;
    }

    private ShadowProductionReplayService replayService(WalletSettingsRepository settingsRepository) {
        return new ShadowProductionReplayService(
                mock(JdbcTemplate.class),
                mock(ExecutionIntelligenceService.class),
                mock(ExecutionReplayScope.class),
                mock(ExecutionPriceAuthorityService.class),
                mock(PositionContinuationPolicy.class),
                mock(PositionExitPolicy.class),
                policy,
                mock(TradeExecutionValidationService.class),
                settingsRepository,
                mock(WalletExecutionSizingPolicy.class),
                mock(BinanceMinimumExecutionPolicy.class),
                mock(PositionPriceAuthorityPolicy.class),
                mock(DefensiveRiskReductionReplayObserver.class),
                mock(OneCandleContinuationGraceReplayObserver.class),
                mock(NearTpFailureProtectionPolicy.class));
    }

    private Object shadowPosition(long id, BigDecimal entry, BigDecimal target, BigDecimal highest,
                                  boolean active, BigDecimal lock, ProfitLockState state, Instant rebaseAt) throws Exception {
        Class<?> type = Class.forName("com.crypto.regression.service.ShadowProductionReplayService$ShadowPosition");
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(
                id, Instant.parse("2026-09-07T13:20:00Z"), entry, BigDecimal.ONE,
                entry, 25, entry.multiply(new BigDecimal("0.99")), target,
                highest, active, lock, state, rebaseAt,
                70, 72, 20, 6, 10, 10,
                NearTpState.INACTIVE, null, 0, null, false,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private Object invokeRebase(ShadowProductionReplayService service, Object position,
                                BigDecimal newTarget, BigDecimal extensionPrice, Instant at) throws Exception {
        Method method = ShadowProductionReplayService.class.getDeclaredMethod(
                "rebaseForExtendedTarget", position.getClass(), BigDecimal.class, BigDecimal.class, Instant.class);
        method.setAccessible(true);
        return method.invoke(service, position, newTarget, extensionPrice, at);
    }

    private ProfitLockState readState(Object position) throws Exception {
        return (ProfitLockState) accessor(position, "profitLockState");
    }

    private BigDecimal readDecimal(Object position, String name) throws Exception {
        return (BigDecimal) accessor(position, name);
    }

    private Instant readInstant(Object position, String name) throws Exception {
        return (Instant) accessor(position, name);
    }

    private Object accessor(Object position, String name) throws Exception {
        Method method = position.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(position);
    }
}

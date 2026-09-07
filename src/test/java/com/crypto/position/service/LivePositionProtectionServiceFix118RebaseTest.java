package com.crypto.position.service;

import com.crypto.audit.service.ProductionExitAuditService;
import com.crypto.domain.PositionStatus;
import com.crypto.position.repository.PositionManagementEventRepository;
import com.crypto.repository.PaperPositionRepository;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.wallet.domain.WalletManagedPosition;
import com.crypto.wallet.repository.WalletManagedPositionRepository;
import com.crypto.wallet.service.WalletAutoExecutionService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LivePositionProtectionServiceFix118RebaseTest {

    @Test
    void rebaseHardFloorIsStandaloneExitAuthorityAfterStopLossPriority() {
        WalletManagedPositionRepository managedRepository = mock(WalletManagedPositionRepository.class);
        PaperPositionRepository paperRepository = mock(PaperPositionRepository.class);
        DynamicProfitLockService profitLockService = mock(DynamicProfitLockService.class);
        PositionContinuationPolicy continuationPolicy = mock(PositionContinuationPolicy.class);
        PositionExitPolicy exitPolicy = mock(PositionExitPolicy.class);
        TradeSignalRepository signalRepository = mock(TradeSignalRepository.class);
        WalletAutoExecutionService walletExecution = mock(WalletAutoExecutionService.class);
        ProductionExitAuditService exitAudit = mock(ProductionExitAuditService.class);
        PositionManagementEventRepository eventRepository = mock(PositionManagementEventRepository.class);
        NearTpFailureProtectionPolicy nearTpPolicy = mock(NearTpFailureProtectionPolicy.class);

        LivePositionProtectionService service = new LivePositionProtectionService(
                managedRepository, paperRepository, profitLockService, continuationPolicy,
                exitPolicy, signalRepository, walletExecution, exitAudit, eventRepository, nearTpPolicy);

        WalletManagedPosition managed = WalletManagedPosition.builder()
                .id(1010L)
                .symbol("ICPUSDT")
                .quantity(BigDecimal.ONE)
                .averageEntryPriceUsdt(new BigDecimal("100"))
                .stopLossUsdt(new BigDecimal("95"))
                .takeProfitUsdt(new BigDecimal("130"))
                .profitLockActive(true)
                .profitLockState(ProfitLockState.TP_EXTENSION_REBASE)
                .profitLockPriceUsdt(new BigDecimal("112"))
                .status("OPEN")
                .openedAt(Instant.parse("2026-09-06T12:58:03Z"))
                .updatedAt(Instant.parse("2026-09-06T13:00:55Z"))
                .build();

        when(managedRepository.findFirstBySymbolAndStatusOrderByOpenedAtDesc("ICPUSDT", "OPEN"))
                .thenReturn(Optional.of(managed));
        when(signalRepository.findTopBySymbolAndIntervalOrderByGeneratedAtDesc(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(profitLockService.evaluatePrice("ICPUSDT", new BigDecimal("100.01")))
                .thenReturn(new DynamicProfitLockService.Evaluation(
                        true, false, false, 1010L, new BigDecimal("100.01"), new BigDecimal("115"),
                        new BigDecimal("50"), new BigDecimal("112"), new BigDecimal("70"),
                        ProfitLockState.TP_EXTENSION_REBASE, new BigDecimal("100.0500"), true,
                        "TP-extension rebase safety floor breached."));

        service.onPrice("ICPUSDT", new BigDecimal("100.01"));

        verify(walletExecution).executeMechanicalExit(
                eq("ICPUSDT"), eq(new BigDecimal("100.01")), eq("PROFIT_LOCK_HARD_EXIT"), anyString());
        verify(walletExecution, never()).executeMechanicalExit(
                eq("ICPUSDT"), any(), eq("STOP_LOSS"), anyString());
    }

    @Test
    void normalSellAuthorityRemainsAvailableDuringRebase() {
        WalletManagedPositionRepository managedRepository = mock(WalletManagedPositionRepository.class);
        PaperPositionRepository paperRepository = mock(PaperPositionRepository.class);
        DynamicProfitLockService profitLockService = mock(DynamicProfitLockService.class);
        PositionContinuationPolicy continuationPolicy = mock(PositionContinuationPolicy.class);
        PositionExitPolicy exitPolicy = mock(PositionExitPolicy.class);
        TradeSignalRepository signalRepository = mock(TradeSignalRepository.class);
        WalletAutoExecutionService walletExecution = mock(WalletAutoExecutionService.class);
        ProductionExitAuditService exitAudit = mock(ProductionExitAuditService.class);
        PositionManagementEventRepository eventRepository = mock(PositionManagementEventRepository.class);
        NearTpFailureProtectionPolicy nearTpPolicy = mock(NearTpFailureProtectionPolicy.class);

        LivePositionProtectionService service = new LivePositionProtectionService(
                managedRepository, paperRepository, profitLockService, continuationPolicy,
                exitPolicy, signalRepository, walletExecution, exitAudit, eventRepository, nearTpPolicy);

        WalletManagedPosition managed = WalletManagedPosition.builder()
                .id(1010L)
                .symbol("ICPUSDT")
                .quantity(BigDecimal.ONE)
                .averageEntryPriceUsdt(new BigDecimal("100"))
                .stopLossUsdt(new BigDecimal("95"))
                .takeProfitUsdt(new BigDecimal("130"))
                .profitLockActive(true)
                .profitLockState(ProfitLockState.TP_EXTENSION_REBASE)
                .profitLockPriceUsdt(new BigDecimal("112"))
                .status("OPEN")
                .openedAt(Instant.parse("2026-09-06T12:58:03Z"))
                .updatedAt(Instant.parse("2026-09-06T13:00:55Z"))
                .build();

        when(managedRepository.findFirstBySymbolAndStatusOrderByOpenedAtDesc("ICPUSDT", "OPEN"))
                .thenReturn(Optional.of(managed));
        when(signalRepository.findTopBySymbolAndIntervalOrderByGeneratedAtDesc(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(profitLockService.evaluatePrice("ICPUSDT", new BigDecimal("110")))
                .thenReturn(new DynamicProfitLockService.Evaluation(
                        true, false, false, 1010L, new BigDecimal("110"), new BigDecimal("115"),
                        new BigDecimal("50"), new BigDecimal("112"), new BigDecimal("70"),
                        ProfitLockState.TP_EXTENSION_REBASE, new BigDecimal("100.0500"), false,
                        "Historical lock is non-executable during TP-extension rebase."));
        when(exitPolicy.evaluateNormalExit(any(), any(), any()))
                .thenReturn(PositionExitPolicy.Evaluation.exit(
                        "SELL_CONFIRMED", "Normal thesis exit remains authoritative during rebase."));
        when(walletExecution.executeMechanicalExit(
                eq("ICPUSDT"), eq(new BigDecimal("110")), eq("SELL_CONFIRMED"), anyString()))
                .thenReturn(true);

        service.onPrice("ICPUSDT", new BigDecimal("110"));

        verify(walletExecution).executeMechanicalExit(
                eq("ICPUSDT"), eq(new BigDecimal("110")), eq("SELL_CONFIRMED"), anyString());
        verify(walletExecution, never()).executeMechanicalExit(
                eq("ICPUSDT"), any(), eq("PROFIT_LOCK_HARD_EXIT"), anyString());
    }

}

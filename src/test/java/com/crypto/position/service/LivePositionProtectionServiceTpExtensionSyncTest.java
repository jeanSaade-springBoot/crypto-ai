package com.crypto.position.service;

import com.crypto.audit.service.ProductionExitAuditService;
import com.crypto.domain.PaperPosition;
import com.crypto.domain.PositionSide;
import com.crypto.domain.PositionStatus;
import com.crypto.position.domain.PositionManagementEvent;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LivePositionProtectionServiceTpExtensionSyncTest {

    @Test
    void pepe552ExtendedTargetIsSynchronizedIntoPaperPosition() {
        WalletManagedPositionRepository managedRepository = mock(WalletManagedPositionRepository.class);
        PaperPositionRepository paperRepository = mock(PaperPositionRepository.class);
        DynamicProfitLockService profitLockService = mock(DynamicProfitLockService.class);
        PositionContinuationPolicy continuationPolicy = mock(PositionContinuationPolicy.class);
        PositionExitPolicy exitPolicy = mock(PositionExitPolicy.class);
        TradeSignalRepository signalRepository = mock(TradeSignalRepository.class);
        WalletAutoExecutionService walletExecution = mock(WalletAutoExecutionService.class);
        ProductionExitAuditService exitAudit = mock(ProductionExitAuditService.class);
        PositionManagementEventRepository eventRepository = mock(PositionManagementEventRepository.class);
        NearTpFailureProtectionPolicy nearTpFailureProtectionPolicy = mock(NearTpFailureProtectionPolicy.class);

        LivePositionProtectionService service = new LivePositionProtectionService(
                managedRepository, paperRepository, profitLockService, continuationPolicy,
                exitPolicy, signalRepository, walletExecution, exitAudit, eventRepository,
                nearTpFailureProtectionPolicy);

        WalletManagedPosition managed = WalletManagedPosition.builder()
                .id(552L)
                .symbol("PEPEUSDT")
                .quantity(new BigDecimal("24330900.243309002433"))
                .averageEntryPriceUsdt(new BigDecimal("0.000004110000"))
                .takeProfitUsdt(new BigDecimal("0.000004146897"))
                .stopLossUsdt(new BigDecimal("0.000004085402"))
                .status("OPEN")
                .openedAt(Instant.parse("2026-08-23T20:48:03Z"))
                .updatedAt(Instant.parse("2026-08-23T20:48:03Z"))
                .build();

        PaperPosition paper = PaperPosition.builder()
                .id(552L)
                .symbol("PEPEUSDT")
                .side(PositionSide.BUY)
                .status(PositionStatus.OPEN)
                .quantity(new BigDecimal("24330900.243309002433"))
                .entryPrice(new BigDecimal("0.000004110000"))
                .stopLoss(new BigDecimal("0.000004085402"))
                .takeProfit(new BigDecimal("0.000004146897"))
                .openedAt(Instant.parse("2026-08-23T20:48:03Z"))
                .build();

        when(managedRepository.findFirstBySymbolAndStatusOrderByOpenedAtDesc("PEPEUSDT", "OPEN"))
                .thenReturn(Optional.of(managed));
        when(paperRepository.findBySymbolAndStatus("PEPEUSDT", PositionStatus.OPEN))
                .thenReturn(Optional.of(paper));
        when(signalRepository.findTopBySymbolAndIntervalOrderByGeneratedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(continuationPolicy.evaluate(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PositionContinuationPolicy.Evaluation(true, "Continuation PASS (HTF_TREND)"));

        service.onPrice("PEPEUSDT", new BigDecimal("0.000004150000"));

        // FIX-067 regression: the approved extension must update BOTH Production state holders.
        assertEquals(0, managed.getTakeProfitUsdt().compareTo(new BigDecimal("0.0000041653455")));
        assertEquals(0, paper.getTakeProfit().compareTo(new BigDecimal("0.0000041653455")));
        verify(managedRepository).save(managed);
        verify(paperRepository).save(paper);
        verify(eventRepository).save(any(PositionManagementEvent.class));
        verify(walletExecution, never()).executeMechanicalExit(any(), any(), eq("TAKE_PROFIT"), any());
    }
}

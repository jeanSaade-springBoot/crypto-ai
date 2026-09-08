package com.crypto.infrastructure.transaction;

import com.crypto.position.service.*;
import com.crypto.audit.service.ProductionExitAuditService;
import com.crypto.wallet.repository.WalletManagedPositionRepository;
import com.crypto.wallet.service.WalletAutoExecutionService;
import com.crypto.repository.PaperPositionRepository;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.wallet.domain.WalletManagedPosition;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveProtectionInitialLockTest {
    @Test void initialLockIsMarkedButFailureAfterLockIsNeverMarked() {
        var positions=mock(WalletManagedPositionRepository.class);
        var signals=mock(TradeSignalRepository.class);
        var wallet=mock(WalletAutoExecutionService.class);
        var service=new LivePositionProtectionService(positions,mock(PaperPositionRepository.class),
                mock(DynamicProfitLockService.class),mock(PositionContinuationPolicy.class),
                mock(PositionExitPolicy.class),signals,wallet,mock(ProductionExitAuditService.class),
                mock(com.crypto.position.repository.PositionManagementEventRepository.class),
                mock(NearTpFailureProtectionPolicy.class));
        var error=new CannotAcquireLockException("deadlock",new SQLException("deadlock","40001",1213));
        when(positions.findFirstBySymbolAndStatusOrderByOpenedAtDesc("UNIUSDT","OPEN")).thenThrow(error);
        assertThrows(InitialPositionLockDeadlock.class,()->service.onPrice("UNIUSDT",BigDecimal.ONE));
        verifyNoInteractions(wallet,signals);
        doReturn(Optional.of(WalletManagedPosition.builder().quantity(BigDecimal.ONE).build()))
                .when(positions).findFirstBySymbolAndStatusOrderByOpenedAtDesc("UNIUSDT","OPEN");
        when(signals.findTopBySymbolAndIntervalOrderByGeneratedAtDesc("UNIUSDT","1m")).thenThrow(error);
        assertSame(error,assertThrows(CannotAcquireLockException.class,()->service.onPrice("UNIUSDT",BigDecimal.ONE)));
        verifyNoInteractions(wallet);
    }
}

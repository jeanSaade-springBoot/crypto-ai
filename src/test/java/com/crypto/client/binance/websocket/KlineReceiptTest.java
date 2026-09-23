package com.crypto.client.binance.websocket;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.crypto.infrastructure.transaction.Fix124ProtectionStore;
import com.crypto.infrastructure.transaction.KlineTransactionCoordinator;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;

class KlineReceiptTest {
    @Test void inputCommitDiagnosticAppearsAfterCommitBeforeProtection() {
        Logger logger=(Logger)LoggerFactory.getLogger(KlineReceipt.class);
        var logs=new ListAppender<ILoggingEvent>();logs.start();logger.addAppender(logs);
        var tx=mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var coordinator=new KlineTransactionCoordinator(tx,mock(Fix124ProtectionStore.class));
        try(var receipt=KlineReceipt.begin(1,Instant.now(),System.nanoTime(),true)) {
            coordinator.process("BTCUSDT","1m",Instant.EPOCH,Instant.EPOCH,BigDecimal.ONE,()->{},()->{
                verify(tx).commit(any());
                assertTrue(logs.list.stream().anyMatch(e->e.getFormattedMessage().contains("CANDLE_INPUT_COMMITTED")));
            },null,null);
        } finally { logger.detachAppender(logs); }
    }
    @Test void failedInputNeverClaimsCommitAndReceiptDoesNotLeak() {
        Logger logger=(Logger)LoggerFactory.getLogger(KlineReceipt.class);
        var logs=new ListAppender<ILoggingEvent>();logs.start();logger.addAppender(logs);
        var tx=mock(PlatformTransactionManager.class);when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var coordinator=new KlineTransactionCoordinator(tx,mock(Fix124ProtectionStore.class));
        try {
            try(var receipt=KlineReceipt.begin(1,Instant.now(),System.nanoTime(),true)) {
                assertThrows(IllegalStateException.class,()->coordinator.process("BTCUSDT","1m",Instant.EPOCH,Instant.EPOCH,BigDecimal.ONE,()->{throw new IllegalStateException("rollback");},null,null,null));
            }
            KlineReceipt.inputCommitted("BTCUSDT","1m",Instant.EPOCH,Instant.EPOCH);
            assertFalse(logs.list.stream().anyMatch(e->e.getFormattedMessage().contains("CANDLE_INPUT_COMMITTED")));
            verify(tx,never()).commit(any());
        } finally { logger.detachAppender(logs); }
    }
}

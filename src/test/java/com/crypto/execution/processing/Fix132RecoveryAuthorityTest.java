package com.crypto.execution.processing;

import com.crypto.service.PaperTradingService;
import com.crypto.shared.SharedRecoveryAuthority;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;

class Fix132RecoveryAuthorityTest {
    @Test void liveKeepsQuarantineButNeverBypassesSharedDelivery() {
        var store=mock(SignalProcessingStore.class);var paper=mock(PaperTradingService.class);
        var guard=mock(SharedRecoveryAuthority.class);when(guard.ownsLiveExecution()).thenReturn(true);
        var recovery=new SignalProcessingRecovery(store,paper);
        ReflectionTestUtils.setField(recovery,"sharedRecoveryAuthority",guard);
        recovery.recover();verify(store).quarantineInterrupted();verify(store,never()).due();verifyNoInteractions(paper);
    }
    @Test void offPreservesPendingRecovery() {
        var store=mock(SignalProcessingStore.class);var paper=mock(PaperTradingService.class);
        var guard=mock(SharedRecoveryAuthority.class);when(store.due()).thenReturn(List.of(12L));
        var recovery=new SignalProcessingRecovery(store,paper);
        ReflectionTestUtils.setField(recovery,"sharedRecoveryAuthority",guard);
        recovery.recover();verify(paper).recoverRegisteredSignal(12L);
    }
}

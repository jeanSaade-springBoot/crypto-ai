package com.crypto.execution.processing;

import com.crypto.service.PaperTradingService;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class Fix128RecoveryIsolationTest {
    @Test void failedQuarantineDiscoveryDoesNotSuppressPendingRecovery() {
        var store=mock(SignalProcessingStore.class);var paper=mock(PaperTradingService.class);
        when(store.quarantineInterrupted()).thenThrow(new IllegalStateException("housekeeping failed"));
        when(store.due()).thenReturn(List.of(12L,13L));
        new SignalProcessingRecovery(store,paper).recover();
        verify(paper).recoverRegisteredSignal(12);verify(paper).recoverRegisteredSignal(13);
    }
    @Test void oneFailedQuarantineRowDoesNotSuppressLaterCandidates() {
        var store=mock(SignalProcessingStore.class);
        var first=new SignalProcessingStore.QuarantineCandidate(1,"a",Instant.EPOCH);
        var second=new SignalProcessingStore.QuarantineCandidate(2,"b",Instant.EPOCH);
        when(store.quarantineCandidates(any())).thenReturn(List.of(first,second));
        when(store.quarantineCandidate(eq(first),any())).thenThrow(new IllegalStateException("deadlock"));
        when(store.quarantineCandidate(eq(second),any())).thenReturn(true);
        when(store.quarantineInterrupted()).thenCallRealMethod();
        assertEquals(1,store.quarantineInterrupted());
        verify(store).quarantineCandidate(eq(first),any());verify(store).quarantineCandidate(eq(second),any());
    }
    @Test void oneFailedPendingSignalDoesNotSuppressNextSignal() {
        var store=mock(SignalProcessingStore.class);var paper=mock(PaperTradingService.class);
        when(store.due()).thenReturn(List.of(12L,13L));
        when(paper.recoverRegisteredSignal(12)).thenThrow(new IllegalStateException("processing failed"));
        new SignalProcessingRecovery(store,paper).recover();
        verify(paper).recoverRegisteredSignal(13);
    }
}

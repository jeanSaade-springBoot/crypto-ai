package com.crypto.execution.service;

import com.crypto.domain.TradeSignal;
import com.crypto.domain.SignalDecision;
import com.crypto.execution.service.ExecutionIntelligenceService.ExecutionDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StopLossEvidenceServiceTest {
    StopLossEvidenceStore store;
    ExecutionReplayScope replay;
    StopLossEvidenceService service;
    final Instant stop=Instant.parse("2026-09-07T14:05:28Z");
    final Instant at=Instant.parse("2026-09-07T14:09:02Z");
    @BeforeEach void setup() {
        store=mock(StopLossEvidenceStore.class); replay=new ExecutionReplayScope();
        service=new StopLossEvidenceService(store,replay,new ObjectMapper(),true,java.time.Clock.fixed(Instant.parse("2026-09-07T14:10:00Z"),java.time.ZoneOffset.UTC));
    }
    TradeSignal signal(long id,String symbol,Instant open,Instant generated) {
        TradeSignal s=new TradeSignal();s.setId(id);s.setSymbol(symbol);s.setInterval("1m");
        s.setCandleOpenTime(open);s.setGeneratedAt(generated);s.setDecision(SignalDecision.WATCH);
        return s;
    }
    TradeSignal fresh() { return signal(619225,"BICOUSDT",at.minusSeconds(62),at); }
    TradeSignal stale() { return signal(618956,"BICOUSDT",stop.minusSeconds(448),stop.minusSeconds(446)); }
    ExecutionDecision allowed() { return ExecutionDecision.allow("TEST","TEST",25,"test",ExecutionIntelligenceService.Evidence.empty()); }

    @Test void productionAndReplayExcludeTheSameOldObservationAndKeepFreshOne() {
        when(store.latest(eq("BICOUSDT"),any())).thenReturn(new StopLossEvidencePolicy.Boundary(1812,stop));
        var production=service.evaluate(fresh(),0,()-> {
            assertFalse(service.eligible(stale(),"EVIDENCE"));
            assertTrue(service.eligible(fresh(),"EVIDENCE")); return allowed();
        });
        clearInvocations(store);
        try(var scope=replay.open(8,List.of(),o->{})) {
            replay.reference(at);replay.recordStop("BICOUSDT",1812,stop,"STOP_LOSS");
            var historical=service.evaluate(fresh(),0,()-> {
                assertFalse(service.eligible(stale(),"EVIDENCE"));
                assertTrue(service.eligible(fresh(),"EVIDENCE"));return allowed();
            });
            assertEquals(production,historical);
            verify(store,never()).latest(any(),any());
        }
        assertFalse(service.hasBoundary());
    }
    @Test void lookupFailureDefersAndDoesNotRunTheEntryRules() {
        when(store.latest(any(),any())).thenThrow(new IllegalStateException("timeout"));
        var d=service.evaluate(fresh(),0,()->{fail("Must not fall back to OLD");return allowed();});
        assertFalse(d.allowed());assertEquals("FIX122_BOUNDARY_UNAVAILABLE",d.code());
        assertFalse(service.hasBoundary());
        verify(store).append(isNull(),eq("BICOUSDT"),eq(619225L),any(),eq("EVALUATION"),contains("BOUNDARY_UNAVAILABLE"));
    }
    @Test void additionsDoNotReadBoundaryOrFilterHistory() {
        var d=service.evaluate(fresh(),25,()->{assertTrue(service.eligible(stale(),"EVIDENCE"));return allowed();});
        assertTrue(d.allowed());assertNull(d.stopBoundary());verifyNoInteractions(store);
    }
    @Test void staleCurrentCandleCannotEnterThroughIndependentDirectRoute() {
        when(store.latest(any(),any())).thenReturn(new StopLossEvidencePolicy.Boundary(1812,stop));
        assertEquals("FIX122_CURRENT_CANDLE_STALE",service.evaluate(stale(),0,()->{
            fail("Stale current candle cannot authorize entry");return allowed();}).code());
    }
    @Test void excludedDeferredOriginDoesNotAutomaticallyBlockAlternativeRoute() {
        when(store.latest(any(),any())).thenReturn(new StopLossEvidencePolicy.Boundary(1812,stop));
        var d=service.evaluate(fresh(),0,()->{assertFalse(service.eligible(stale(),"DEFERRED_ORIGIN"));return allowed();});
        assertTrue(d.allowed());
        verify(store).append(isNull(),any(),any(),any(),eq("EVALUATION"),contains("DEFERRED_ORIGIN"));
    }
    @Test void newCommittedStopInvalidatesPriorApprovalAtRecheck() {
        when(store.latest(any(),any())).thenReturn(null,new StopLossEvidencePolicy.Boundary(1812,stop));
        var d=service.evaluate(fresh(),0,this::allowed);
        assertEquals("FIX122_BOUNDARY_CHANGED",service.recheck(fresh(),d).code());
    }
    @Test void replayStopsAreIsolatedByRunSymbolAndReason() {
        try(var scope=replay.open(8,List.of(),o->{})) {
            replay.reference(at);
            replay.recordStop("BICOUSDT",1,stop,"NEAR_TP_PARTIAL_HARVEST");
            replay.recordStop("BICOUSDT",2,stop,"POSITION_STOP_LOSS");
            replay.recordStop("BICOUSDT",3,stop,"TAKE_PROFIT");
            assertNull(replay.stopBoundary("BICOUSDT",at));
            replay.recordStop("BICOUSDT",4,stop,"STOP_LOSS");
            assertNull(replay.stopBoundary("DOGEUSDT",at));
            assertNull(replay.stopBoundary("BICOUSDT",stop.minusSeconds(1)));
        }
        try(var scope=replay.open(9,List.of(),o->{})) { assertNull(replay.stopBoundary("BICOUSDT",at)); }
    }
    @Test void oldReplayRevisionLeavesNewProductionDefaultAlone() {
        try(var scope=replay.open(8,List.of(),o->{})) {
            replay.reference(at);replay.fix122Enabled(false);replay.recordStop("BICOUSDT",1,stop,"STOP_LOSS");
            assertTrue(service.evaluate(stale(),0,this::allowed).allowed());
            verifyNoInteractions(store);
        }
        service.evaluate(fresh(),0,this::allowed);verify(store).latest(eq("BICOUSDT"),any());
    }
    @Test void exceptionCleansThreadContext() {
        when(store.latest(any(),any())).thenReturn(new StopLossEvidencePolicy.Boundary(1812,stop));
        assertThrows(IllegalArgumentException.class,()->service.evaluate(fresh(),0,()->{throw new IllegalArgumentException();}));
        assertFalse(service.hasBoundary());
        assertTrue(service.eligible(stale(),"EVIDENCE"));
    }
}

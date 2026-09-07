package com.crypto.execution.service;

import com.crypto.config.TradingProperties;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.repository.*;
import com.crypto.execution.repository.ExecutionOpportunityRepository;
import com.crypto.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class Fix122EvidenceIntegrationTest {
    @Test void realEvidenceCalculatorExcludesPreStopBuyButAddKeepsOriginalHistory() {
        var signals=mock(TradeSignalRepository.class);
        var engine=new ExecutionIntelligenceService(mock(TradingProperties.class),mock(TradeExecutionValidationService.class),
                mock(OpportunityConsolidationService.class),signals,mock(CandleRepository.class),
                mock(ExecutionOpportunityRepository.class),mock(PressureReadinessService.class),mock(RecoveryTransitionService.class));
        var store=mock(StopLossEvidenceStore.class);
        var helper=new StopLossEvidenceService(store,new ExecutionReplayScope(),new ObjectMapper(),true,java.time.Clock.fixed(Instant.parse("2026-09-07T14:10:00Z"),java.time.ZoneOffset.UTC));
        ReflectionTestUtils.setField(engine,"stopLossEvidence",helper);
        Instant stop=Instant.parse("2026-09-07T14:05:28Z");
        var current=signal(619225,SignalDecision.WATCH,Instant.parse("2026-09-07T14:08:00Z"));
        var old=signal(618956,SignalDecision.BUY,Instant.parse("2026-09-07T13:57:00Z"));
        when(signals.findTop20BySymbolAndIntervalOrderByGeneratedAtDesc("BICOUSDT","1m")).thenReturn(List.of(current,old));
        when(store.latest(eq("BICOUSDT"),any())).thenReturn(new StopLossEvidencePolicy.Boundary(1812,stop));
        helper.evaluate(current,0,()->{
            ExecutionIntelligenceService.Evidence e=ReflectionTestUtils.invokeMethod(engine,"evidence",current);
            assertNotNull(e);assertEquals(0,e.buyCount());assertEquals(1,e.watchCount());assertEquals(1,e.evidenceScore());
            return ExecutionIntelligenceService.ExecutionDecision.building("TEST","test",e);
        });
        helper.evaluate(current,25,()->{
            ExecutionIntelligenceService.Evidence e=ReflectionTestUtils.invokeMethod(engine,"evidence",current);
            assertNotNull(e);assertEquals(1,e.buyCount());assertEquals(1,e.watchCount());assertEquals(4,e.evidenceScore());
            return ExecutionIntelligenceService.ExecutionDecision.building("TEST","test",e);
        });
    }
    private TradeSignal signal(long id,SignalDecision decision,Instant open) {
        var s=new TradeSignal();s.setId(id);s.setSymbol("BICOUSDT");s.setInterval("1m");
        s.setDecision(decision);s.setOriginalDecision(decision);s.setTotalScore(75);s.setConfidenceScore(72);
        s.setCandleOpenTime(open);s.setGeneratedAt(open.plusSeconds(62));return s;
    }
}

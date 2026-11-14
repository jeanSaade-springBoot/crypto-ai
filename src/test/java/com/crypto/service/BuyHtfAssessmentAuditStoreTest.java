package com.crypto.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import com.crypto.execution.service.ExecutionReplayScope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;

class BuyHtfAssessmentAuditStoreTest {
    @Test
    void replayNeverWritesAProductionAuditRow() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BuyHtfAssessmentAuditStore store = new BuyHtfAssessmentAuditStore(jdbc, Runnable::run, new Fix154Metrics());
        ExecutionReplayScope replay = new ExecutionReplayScope();
        ReflectionTestUtils.setField(store, "replayScope", replay);
        TradeSignal reference = signal(1L, "1m", SignalDecision.BUY, Instant.parse("2026-01-01T00:00:00Z"));
        TradeSignal five = signal(null, "5m", SignalDecision.BUY, reference.getGeneratedAt().minusSeconds(60));
        TradeSignal one = signal(null, "1h", SignalDecision.SELL, reference.getGeneratedAt().minusSeconds(3600));
        one.setDataQualityValid(true);
        HigherTimeframeBuyAssessmentService.Assessment assessment = new HigherTimeframeBuyAssessmentService().assess(five, true, one, true);
        try (ExecutionReplayScope.Scope ignored = replay.open(99L, List.of(reference, five, one), o -> { })) {
            replay.reference(reference.getGeneratedAt());
            store.record(reference, "DIRECT_BUY", assessment, "BALANCED_STRONG", "test");
        }
        verifyNoInteractions(jdbc);
    }

    private static TradeSignal signal(Long id, String interval, SignalDecision decision, Instant at) {
        TradeSignal signal = new TradeSignal();
        signal.setId(id); signal.setSymbol("BTCUSDT"); signal.setInterval(interval); signal.setDecision(decision);
        signal.setGeneratedAt(at); signal.setCandleOpenTime(at.minusSeconds(switch (interval) { case "1m" -> 60; case "5m" -> 300; default -> 3600; }));
        return signal;
    }
}

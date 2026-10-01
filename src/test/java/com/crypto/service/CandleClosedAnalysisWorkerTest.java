package com.crypto.service;

import com.crypto.domain.PaperPosition;
import com.crypto.domain.TechnicalIndicator;
import com.crypto.domain.TradeSignal;
import com.crypto.dto.CandleDataQualityResult;
import com.crypto.indicator.event.CandleAnalysisExecutionCoordinator;
import com.crypto.indicator.event.CandleClosedAnalysisWorker;
import com.crypto.indicator.event.CandleClosedEvent;
import com.crypto.indicator.service.TechnicalIndicatorService;
import com.crypto.repository.TradeSignalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandleClosedAnalysisWorkerTest {

    @Mock private TechnicalIndicatorService technicalIndicatorService;
    @Mock private AnalysisService analysisService;
    @Mock private PaperTradingService paperTradingService;
    @Mock private CandleDataQualityService candleDataQualityService;
    @Mock private TradeSignalRepository tradeSignalRepository;
    @Mock private org.springframework.jdbc.core.JdbcTemplate sharedWorkEvidence;

    private CandleClosedAnalysisWorker worker;

    @BeforeEach
    void setUp() {
        worker = new CandleClosedAnalysisWorker(
                technicalIndicatorService,
                analysisService,
                paperTradingService,
                candleDataQualityService,
                tradeSignalRepository,
                new CandleAnalysisExecutionCoordinator(),
                sharedWorkEvidence);
    }

    @Test
    void shouldAnalyzeSavedIndicatorAndPassSignalToPaperTrading() {
        Instant openTime = Instant.parse("2026-07-30T06:00:00Z");
        CandleClosedEvent event = new CandleClosedEvent("BTCUSDT", "1h", openTime);

        TechnicalIndicator indicator = new TechnicalIndicator();
        indicator.setSymbol("BTCUSDT");
        indicator.setIntervalCode("1h");
        indicator.setCandleOpenTime(openTime);

        TradeSignal signal = new TradeSignal();
        PaperPosition position = new PaperPosition();

        when(candleDataQualityService.validate("BTCUSDT", "1h"))
                .thenReturn(new CandleDataQualityResult(true, 210, 210, 0, 0, List.of()));
        when(technicalIndicatorService.calculateAndPersist("BTCUSDT", "1h", openTime))
                .thenReturn(Optional.of(indicator));
        when(tradeSignalRepository.existsBySymbolAndIntervalAndCandleOpenTime(
                "BTCUSDT", "1h", openTime)).thenReturn(false);
        when(analysisService.analyzeForProcessing(indicator, com.crypto.execution.processing.ProcessingOrigin.WORKER)).thenReturn(signal);
        when(paperTradingService.processSignal(signal)).thenReturn(Optional.of(position));

        worker.process(event);

        verify(technicalIndicatorService).calculateAndPersist("BTCUSDT", "1h", openTime);
        verify(analysisService).analyzeForProcessing(indicator, com.crypto.execution.processing.ProcessingOrigin.WORKER);
        verify(paperTradingService).processSignal(signal);
    }

    @Test
    void shouldSkipWhenSignalAlreadyExistsForCandle() {
        Instant openTime = Instant.parse("2026-07-30T06:00:00Z");
        CandleClosedEvent event = new CandleClosedEvent("BTCUSDT", "1h", openTime);

        TechnicalIndicator indicator = new TechnicalIndicator();
        indicator.setSymbol("BTCUSDT");
        indicator.setIntervalCode("1h");
        indicator.setCandleOpenTime(openTime);

        when(candleDataQualityService.validate("BTCUSDT", "1h"))
                .thenReturn(new CandleDataQualityResult(true, 210, 210, 0, 0, List.of()));
        when(technicalIndicatorService.calculateAndPersist("BTCUSDT", "1h", openTime))
                .thenReturn(Optional.of(indicator));
        when(tradeSignalRepository.existsBySymbolAndIntervalAndCandleOpenTime(
                "BTCUSDT", "1h", openTime)).thenReturn(true);

        worker.process(event);

        verify(analysisService, never()).analyzeForProcessing(indicator, com.crypto.execution.processing.ProcessingOrigin.WORKER);
        verify(paperTradingService, never()).processSignal(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void staleSharedClosePersistsHistoricalEvidenceWithoutWalletOrRecoveryRegistration() {
        Instant open=Instant.parse("2020-01-01T00:00:00Z");
        TechnicalIndicator indicator=new TechnicalIndicator();indicator.setSymbol("BTCUSDT");
        indicator.setIntervalCode("1m");indicator.setCandleOpenTime(open);
        when(candleDataQualityService.validate("BTCUSDT","1m")).thenReturn(new CandleDataQualityResult(true,210,210,0,0,List.of()));
        when(technicalIndicatorService.calculateAndPersist("BTCUSDT","1m",open)).thenReturn(Optional.of(indicator));
        String outcome=worker.processShared(new CandleClosedEvent("BTCUSDT","1m",open),open.plusSeconds(60),true);
        org.junit.jupiter.api.Assertions.assertEquals("HISTORICAL_ONLY",outcome);
        verify(analysisService).analyzeRecovered(indicator,open.plusSeconds(60));
        verify(analysisService,never()).analyzeForProcessing(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verifyNoInteractions(paperTradingService);
    }
    @Test
    void fix138ExistingExpiredLiveSourceDoesNotRequireLiveExecutionEvidenceOrReplay() {
        Instant open=Instant.parse("2020-01-01T00:00:00Z");
        TechnicalIndicator indicator=new TechnicalIndicator();
        indicator.setSymbol("BTCUSDT");indicator.setIntervalCode("1m");indicator.setCandleOpenTime(open);
        when(candleDataQualityService.validate("BTCUSDT","1m"))
            .thenReturn(new CandleDataQualityResult(true,210,210,0,0,List.of()));
        when(technicalIndicatorService.calculateAndPersist("BTCUSDT","1m",open)).thenReturn(Optional.of(indicator));
        when(tradeSignalRepository.existsBySymbolAndIntervalAndCandleOpenTime("BTCUSDT","1m",open)).thenReturn(true);
        String outcome=worker.processShared(new CandleClosedEvent("BTCUSDT","1m",open),open.plusSeconds(60),true,42L);
        org.junit.jupiter.api.Assertions.assertEquals("HISTORICAL_ALREADY_EXISTS",outcome);
        // FIX-140 performs a bounded attributed-retry lookup even for an expired source;
        // an ordinary existing historical signal still never reaches wallet processing.
        org.mockito.Mockito.verifyNoInteractions(analysisService,paperTradingService);
    }

    @Test
    void fix138FreshRedeliveryWithCompletedOwnedWorkReturnsAlreadyCompletedWithoutReplay() {
        assertFreshRedeliveryEvidence(1, "ALREADY_COMPLETED");
    }

    @Test
    void fix138FreshRedeliveryWithoutCompletedOwnedWorkRequiresReviewWithoutReplay() {
        assertFreshRedeliveryEvidence(0, "REVIEW_REQUIRED");
    }

    /** Exercise the fresh existing-signal branch, including exact evidence lookup
     * parameters. Neither outcome may rescore, register new work, or execute a wallet. */
    private void assertFreshRedeliveryEvidence(int completed, String expectedOutcome) {
        Instant close = Instant.now().minusSeconds(1);
        Instant open = close.minusSeconds(60);
        TechnicalIndicator indicator = new TechnicalIndicator();
        indicator.setSymbol("BTCUSDT");
        indicator.setIntervalCode("1m");
        indicator.setCandleOpenTime(open);
        when(candleDataQualityService.validate("BTCUSDT", "1m"))
                .thenReturn(new CandleDataQualityResult(true, 210, 210, 0, 0, List.of()));
        when(technicalIndicatorService.calculateAndPersist("BTCUSDT", "1m", open))
                .thenReturn(Optional.of(indicator));
        when(tradeSignalRepository.existsBySymbolAndIntervalAndCandleOpenTime("BTCUSDT", "1m", open))
                .thenReturn(true);
        // FIX-140: ordinary redelivery checks for a due attributed retry first.
        String retrySql = "SELECT signal_id FROM signal_processing_work WHERE source_event_id=? AND status='SYMBOL_LOCK_RETRY' AND failure_stage='INITIAL_SYMBOL_LOCK_ROLLED_BACK' AND next_attempt_at<=CURRENT_TIMESTAMP(6)";
        when(sharedWorkEvidence.queryForList(retrySql, 42L)).thenReturn(List.of());
        String evidenceSql = "SELECT COUNT(*) FROM signal_processing_work w JOIN shared_market_event_delivery d ON d.source_event_id=w.source_event_id WHERE w.symbol=? AND w.interval_code=? AND w.candle_open_time=? AND w.status='COMPLETED' AND d.symbol=w.symbol AND d.interval_code=w.interval_code AND d.candle_open_time=w.candle_open_time";
        java.sql.Timestamp candleOpen = java.sql.Timestamp.from(open);
        when(sharedWorkEvidence.queryForObject(evidenceSql, Integer.class, "BTCUSDT", "1m", candleOpen))
                .thenReturn(completed);

        String outcome = worker.processShared(new CandleClosedEvent("BTCUSDT", "1m", open), close, true, 42L);

        org.junit.jupiter.api.Assertions.assertEquals(expectedOutcome, outcome);
        verify(sharedWorkEvidence).queryForList(retrySql, 42L);
        verify(sharedWorkEvidence).queryForObject(evidenceSql, Integer.class, "BTCUSDT", "1m", candleOpen);
        org.mockito.Mockito.verifyNoMoreInteractions(sharedWorkEvidence);
        org.mockito.Mockito.verifyNoInteractions(analysisService, paperTradingService);
    }

    // FIX-140: cover every durable processing outcome on the saved-signal branch.
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"COMPLETED,COMPLETED","EXPIRED,EXECUTION_EXPIRED",
        "LOCK_RETRY_EXHAUSTED,EXECUTION_NOT_EXECUTED","SYMBOL_LOCK_RETRY,EXECUTION_RETRY","REVIEW_REQUIRED,REVIEW_REQUIRED"})
    void fix140SavedSignalResumeMapsActualWorkState(String state,String expected) {
        Instant open=Instant.now().minusSeconds(65);
        var saved=new TradeSignal();saved.setId(740L);saved.setSymbol("BTCUSDT");saved.setInterval("1m");saved.setCandleOpenTime(open);
        when(sharedWorkEvidence.queryForList(org.mockito.ArgumentMatchers.contains("failure_stage='INITIAL_SYMBOL_LOCK_ROLLED_BACK'"),org.mockito.ArgumentMatchers.eq(140L)))
            .thenReturn(List.of(java.util.Map.of("signal_id",740L)));
        when(tradeSignalRepository.findById(740L)).thenReturn(Optional.of(saved));
        when(paperTradingService.processSharedSignal(saved,140L)).thenReturn(Optional.empty());
        when(sharedWorkEvidence.queryForList("SELECT status FROM signal_processing_work WHERE source_event_id=?",140L))
            .thenReturn(List.of(java.util.Map.of("status",state)));
        org.junit.jupiter.api.Assertions.assertEquals(expected,worker.processShared(new CandleClosedEvent("BTCUSDT","1m",open),open.plusSeconds(60),true,140L));
        org.mockito.Mockito.verifyNoInteractions(technicalIndicatorService,analysisService,candleDataQualityService);
        verify(paperTradingService).processSharedSignal(saved,140L);
    }
}

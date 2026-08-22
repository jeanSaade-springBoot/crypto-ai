package com.crypto.service;

import com.crypto.domain.PaperPosition;
import com.crypto.domain.TechnicalIndicator;
import com.crypto.domain.TradeSignal;
import com.crypto.dto.CandleDataQualityResult;
import com.crypto.indicator.event.CandleClosedAnalysisWorker;
import com.crypto.indicator.event.CandleClosedEvent;
import com.crypto.indicator.service.TechnicalIndicatorService;
import com.crypto.repository.TradeSignalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

    @InjectMocks private CandleClosedAnalysisWorker worker;

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
        when(analysisService.analyze(indicator)).thenReturn(signal);
        when(paperTradingService.processSignal(signal)).thenReturn(Optional.of(position));

        worker.process(event);

        verify(technicalIndicatorService).calculateAndPersist("BTCUSDT", "1h", openTime);
        verify(analysisService).analyze(indicator);
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

        verify(analysisService, never()).analyze(indicator);
        verify(paperTradingService, never()).processSignal(org.mockito.ArgumentMatchers.any());
    }
}

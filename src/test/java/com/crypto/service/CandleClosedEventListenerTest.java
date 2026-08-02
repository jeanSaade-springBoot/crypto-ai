package com.crypto.service;

import com.crypto.domain.PaperPosition;
import com.crypto.domain.TechnicalIndicator;
import com.crypto.domain.TradeSignal;
import com.crypto.dto.CandleDataQualityResult;
import com.crypto.indicator.event.CandleClosedEvent;
import com.crypto.indicator.event.CandleClosedEventListener;
import com.crypto.indicator.service.TechnicalIndicatorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandleClosedEventListenerTest {

    @Mock
    private TechnicalIndicatorService technicalIndicatorService;

    @Mock
    private AnalysisService analysisService;

    @Mock
    private PaperTradingService paperTradingService;

    @Mock
    private CandleDataQualityService candleDataQualityService;

    @InjectMocks
    private CandleClosedEventListener listener;

    @Test
    void shouldAnalyzeSavedIndicatorAndPassSignalToPaperTrading() {
        Instant openTime = Instant.parse("2026-07-30T06:00:00Z");

        CandleClosedEvent event = new CandleClosedEvent(
                "BTCUSDT",
                "1h",
                openTime
        );

        TechnicalIndicator indicator = new TechnicalIndicator();
        TradeSignal signal = new TradeSignal();
        PaperPosition position = new PaperPosition();

        CandleDataQualityResult validDataQuality =
                new CandleDataQualityResult(
                        true,
                        210,
                        210,
                        0,
                        0,
                        List.of()
                );

        when(candleDataQualityService.validate(
                "BTCUSDT",
                "1h"
        )).thenReturn(validDataQuality);

        when(technicalIndicatorService.calculateAndPersist(
                "BTCUSDT",
                "1h",
                openTime
        )).thenReturn(Optional.of(indicator));

        when(analysisService.analyze(indicator))
                .thenReturn(signal);

        when(paperTradingService.processSignal(signal))
                .thenReturn(Optional.of(position));

        listener.handle(event);

        verify(candleDataQualityService).validate(
                "BTCUSDT",
                "1h"
        );

        verify(technicalIndicatorService).calculateAndPersist(
                "BTCUSDT",
                "1h",
                openTime
        );

        verify(analysisService).analyze(indicator);

        verify(paperTradingService).processSignal(signal);
    }
}
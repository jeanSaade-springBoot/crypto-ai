package com.crypto.service;

import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.config.TradingProperties;
import com.crypto.domain.Candle;
import com.crypto.domain.TechnicalIndicator;
import com.crypto.domain.TradeSignal;
import com.crypto.indicator.service.TechnicalIndicatorService;
import com.crypto.repository.CandleRepository;
import com.crypto.repository.TechnicalIndicatorRepository;
import com.crypto.repository.TradeSignalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledAnalysisServiceTest {

    @Mock TradingProperties properties;
    @Mock CoinConfigurationService coinConfigurationService;
    @Mock CandleRepository candleRepository;
    @Mock TechnicalIndicatorRepository technicalIndicatorRepository;
    @Mock TechnicalIndicatorService technicalIndicatorService;
    @Mock AnalysisService analysisService;
    @Mock PaperTradingService paperTradingService;
    @Mock TradeSignalRepository tradeSignalRepository;

    @Test
    void fix043RecoversEveryMissingCandleChronologicallyWithoutExecutingHistoricalPrices() {
        Instant now = Instant.parse("2026-08-22T10:30:00Z");
        Candle c1 = candle("2026-08-22T10:21:00Z", "2026-08-22T10:21:59Z");
        Candle c2 = candle("2026-08-22T10:22:00Z", "2026-08-22T10:22:59Z");
        Candle c3 = candle("2026-08-22T10:23:00Z", "2026-08-22T10:23:59Z");

        when(candleRepository.findClosedCandlesMissingAnalysisThrough(
                eq("ACEUSDT"), eq("1m"), any(Instant.class), eq(now), any(Pageable.class)))
                .thenReturn(List.of(c1, c2, c3));
        when(candleRepository.findFirstBySymbolAndIntervalCodeAndClosedTrueOrderByCloseTimeDesc("ACEUSDT", "1m"))
                .thenReturn(Optional.of(c3));

        TechnicalIndicator i1 = indicator(c1);
        TechnicalIndicator i2 = indicator(c2);
        TechnicalIndicator i3 = indicator(c3);
        when(technicalIndicatorRepository.findBySymbolAndIntervalCodeAndCandleOpenTime(eq("ACEUSDT"), eq("1m"), any()))
                .thenReturn(Optional.empty());
        when(technicalIndicatorService.calculateAndPersist("ACEUSDT", "1m", c1.getOpenTime())).thenReturn(Optional.of(i1));
        when(technicalIndicatorService.calculateAndPersist("ACEUSDT", "1m", c2.getOpenTime())).thenReturn(Optional.of(i2));
        when(technicalIndicatorService.calculateAndPersist("ACEUSDT", "1m", c3.getOpenTime())).thenReturn(Optional.of(i3));
        when(tradeSignalRepository.findBySymbolAndIntervalAndCandleOpenTime(eq("ACEUSDT"), eq("1m"), any()))
                .thenReturn(Optional.empty());
        when(analysisService.analyzeRecovered(i1, c1.getCloseTime())).thenReturn(signal(1L));
        when(analysisService.analyzeRecovered(i2, c2.getCloseTime())).thenReturn(signal(2L));
        when(analysisService.analyzeRecovered(i3, c3.getCloseTime())).thenReturn(signal(3L));

        ScheduledAnalysisService service = new ScheduledAnalysisService(
                properties, coinConfigurationService, candleRepository, technicalIndicatorRepository,
                technicalIndicatorService, analysisService, paperTradingService, tradeSignalRepository);

        service.recoverMissingAnalysis("ACEUSDT", "1m", now);

        InOrder order = inOrder(technicalIndicatorService, analysisService);
        order.verify(technicalIndicatorService).calculateAndPersist("ACEUSDT", "1m", c1.getOpenTime());
        order.verify(analysisService).analyzeRecovered(i1, c1.getCloseTime());
        order.verify(technicalIndicatorService).calculateAndPersist("ACEUSDT", "1m", c2.getOpenTime());
        order.verify(analysisService).analyzeRecovered(i2, c2.getCloseTime());
        order.verify(technicalIndicatorService).calculateAndPersist("ACEUSDT", "1m", c3.getOpenTime());
        order.verify(analysisService).analyzeRecovered(i3, c3.getCloseTime());

        // All three candles are several minutes old, so recovery may rebuild evidence/history
        // but must never place a wallet trade using those historical prices.
        verify(paperTradingService, never()).processSignal(any());
    }

    @Test
    void fix043MayExecuteOnlyFreshLatestRecoveredCandle() {
        Instant now = Instant.parse("2026-08-22T10:30:20Z");
        Candle latest = candle("2026-08-22T10:29:00Z", "2026-08-22T10:29:59Z");
        TechnicalIndicator indicator = indicator(latest);
        TradeSignal signal = signal(99L);

        when(candleRepository.findClosedCandlesMissingAnalysisThrough(
                eq("ACEUSDT"), eq("1m"), any(Instant.class), eq(now), any(Pageable.class)))
                .thenReturn(List.of(latest));
        when(candleRepository.findFirstBySymbolAndIntervalCodeAndClosedTrueOrderByCloseTimeDesc("ACEUSDT", "1m"))
                .thenReturn(Optional.of(latest));
        when(technicalIndicatorRepository.findBySymbolAndIntervalCodeAndCandleOpenTime("ACEUSDT", "1m", latest.getOpenTime()))
                .thenReturn(Optional.empty());
        when(technicalIndicatorService.calculateAndPersist("ACEUSDT", "1m", latest.getOpenTime()))
                .thenReturn(Optional.of(indicator));
        when(tradeSignalRepository.findBySymbolAndIntervalAndCandleOpenTime("ACEUSDT", "1m", latest.getOpenTime()))
                .thenReturn(Optional.empty());
        when(analysisService.analyzeRecovered(indicator, latest.getCloseTime())).thenReturn(signal);

        ScheduledAnalysisService service = new ScheduledAnalysisService(
                properties, coinConfigurationService, candleRepository, technicalIndicatorRepository,
                technicalIndicatorService, analysisService, paperTradingService, tradeSignalRepository);

        service.recoverMissingAnalysis("ACEUSDT", "1m", now);

        verify(paperTradingService).processSignal(signal);
    }

    private Candle candle(String open, String close) {
        Candle candle = new Candle();
        candle.setSymbol("ACEUSDT");
        candle.setIntervalCode("1m");
        candle.setOpenTime(Instant.parse(open));
        candle.setCloseTime(Instant.parse(close));
        candle.setClosed(true);
        return candle;
    }

    private TechnicalIndicator indicator(Candle candle) {
        TechnicalIndicator indicator = new TechnicalIndicator();
        indicator.setSymbol(candle.getSymbol());
        indicator.setIntervalCode(candle.getIntervalCode());
        indicator.setCandleOpenTime(candle.getOpenTime());
        return indicator;
    }

    private TradeSignal signal(Long id) {
        TradeSignal signal = new TradeSignal();
        signal.setId(id);
        signal.setSymbol("ACEUSDT");
        signal.setInterval("1m");
        return signal;
    }
}

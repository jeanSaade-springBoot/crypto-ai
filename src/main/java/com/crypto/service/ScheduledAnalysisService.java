package com.crypto.service;

import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.config.TradingProperties;
import com.crypto.domain.TechnicalIndicator;
import com.crypto.domain.TradeSignal;
import com.crypto.indicator.service.TechnicalIndicatorService;
import com.crypto.repository.TradeSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledAnalysisService {

    private final TradingProperties properties;
    private final CoinConfigurationService coinConfigurationService;
    private final TechnicalIndicatorService technicalIndicatorService;
    private final AnalysisService analysisService;
    private final PaperTradingService paperTradingService;
    private final TradeSignalRepository tradeSignalRepository;

    /**
     * Recovery flow for every configured symbol and interval.
     * The normal flow remains event-driven from CandleClosedEvent.
     */
    @Scheduled(fixedDelayString = "${trading.analysis-delay-ms:300000}")
    public void analyzeConfiguredSymbols() {
        if (!properties.scheduledAnalysisEnabled()) {
            return;
        }

        for (String symbol : coinConfigurationService.enabledSymbols()) {
            for (String interval : properties.intervals()) {
                analyze(symbol, interval);
            }
        }
    }

    private void analyze(String symbol, String interval) {
        try {
            TechnicalIndicator indicator = technicalIndicatorService
                    .calculateAndPersist(symbol, interval, null)
                    .orElse(null);

            if (indicator == null) {
                log.info(
                        "Scheduled analysis skipped: symbol={}, interval={}, reason=insufficient closed-candle history",
                        symbol,
                        interval
                );
                return;
            }

            if (tradeSignalRepository.existsBySymbolAndIntervalAndCandleOpenTime(
                    indicator.getSymbol(),
                    indicator.getIntervalCode(),
                    indicator.getCandleOpenTime()
            )) {
                log.info(
                        "Scheduled recovery skipped: signal already exists for symbol={}, interval={}, candleOpenTime={}",
                        indicator.getSymbol(),
                        indicator.getIntervalCode(),
                        indicator.getCandleOpenTime()
                );
                return;
            }

            TradeSignal signal = analysisService.analyze(indicator);
            paperTradingService.processSignal(signal);

            log.info(
                    "Scheduled recovery analysis completed: symbol={}, interval={}, score={}, decision={}",
                    symbol,
                    interval,
                    signal.getTotalScore(),
                    signal.getDecision()
            );
        } catch (Exception exception) {
            log.error(
                    "Scheduled analysis failed: symbol={}, interval={}",
                    symbol,
                    interval,
                    exception
            );
        }
    }
}

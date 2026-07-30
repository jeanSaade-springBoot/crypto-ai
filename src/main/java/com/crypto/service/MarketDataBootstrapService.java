package com.crypto.service;

import com.crypto.client.config.binance.BinanceMarketDataProperties;
import com.crypto.config.TradingProperties;
import com.crypto.domain.TechnicalIndicator;
import com.crypto.domain.TradeSignal;
import com.crypto.indicator.service.TechnicalIndicatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Ensures every configured symbol and interval has enough historical candles
 * when the application starts. Existing candles are preserved and only missing
 * Binance rows are inserted by MarketDataService.
 */
@Component
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class MarketDataBootstrapService implements ApplicationRunner {

    private static final int REQUIRED_CANDLES = 300;

    private final BinanceMarketDataProperties marketDataProperties;
    private final TradingProperties tradingProperties;
    private final MarketDataService marketDataService;
    private final TechnicalIndicatorService technicalIndicatorService;
    private final AnalysisService analysisService;
    private final PaperTradingService paperTradingService;

    @Override
    public void run(ApplicationArguments args) {
        if (!marketDataProperties.isEnabled()
                || !marketDataProperties.getHistorical().isEnabled()) {
            log.info("Historical candle bootstrap is disabled");
            return;
        }

        List<String> symbols = selectSymbols();
        List<String> intervals = selectIntervals();
        int limit = Math.max(
                REQUIRED_CANDLES,
                Math.min(1000, marketDataProperties.getHistorical().getLimit())
        );

        for (String symbol : symbols) {
            for (String interval : intervals) {
                bootstrap(symbol, interval, limit);
            }
        }
    }

    private void bootstrap(String symbol, String interval, int limit) {
        try {
            int inserted = marketDataService.importCandles(symbol, interval, limit);

            log.info(
                    "Historical bootstrap completed: symbol={}, interval={}, inserted={}",
                    symbol,
                    interval,
                    inserted
            );

            TechnicalIndicator indicator = technicalIndicatorService
                    .calculateAndPersist(symbol, interval, null)
                    .orElse(null);

            if (indicator == null) {
                log.warn(
                        "Initial indicator skipped: symbol={}, interval={}, reason=fewer than 210 closed candles",
                        symbol,
                        interval
                );
                return;
            }

            TradeSignal signal = analysisService.analyze(indicator);
            paperTradingService.openFromSignal(signal);

            log.info(
                    "Initial signal generated: symbol={}, interval={}, score={}, decision={}",
                    symbol,
                    interval,
                    signal.getTotalScore(),
                    signal.getDecision()
            );
        } catch (Exception exception) {
            log.error(
                    "Historical bootstrap failed: symbol={}, interval={}",
                    symbol,
                    interval,
                    exception
            );
        }
    }

    private List<String> selectSymbols() {
        Set<String> values = new LinkedHashSet<>();
        values.addAll(marketDataProperties.getSymbols());
        values.addAll(tradingProperties.symbols());
        return List.copyOf(values);
    }

    private List<String> selectIntervals() {
        Set<String> values = new LinkedHashSet<>();
        values.addAll(marketDataProperties.getIntervals());
        values.addAll(tradingProperties.intervals());
        return List.copyOf(values);
    }
}

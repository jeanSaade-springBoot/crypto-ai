package com.crypto.indicator.event;

import com.crypto.domain.PaperPosition;
import com.crypto.domain.TechnicalIndicator;
import com.crypto.domain.TradeSignal;
import com.crypto.indicator.service.TechnicalIndicatorService;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.service.AnalysisService;
import com.crypto.service.CandleDataQualityService;
import com.crypto.dto.CandleDataQualityResult;
import com.crypto.service.PaperTradingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

@Component
public class CandleClosedEventListener {

    private static final Logger log =
            LoggerFactory.getLogger(CandleClosedEventListener.class);

    private final TechnicalIndicatorService technicalIndicatorService;
    private final AnalysisService analysisService;
    private final PaperTradingService paperTradingService;
    private final CandleDataQualityService candleDataQualityService;
    private final TradeSignalRepository tradeSignalRepository;

    public CandleClosedEventListener(
            TechnicalIndicatorService technicalIndicatorService,
            AnalysisService analysisService,
            PaperTradingService paperTradingService,
            CandleDataQualityService candleDataQualityService,
            TradeSignalRepository tradeSignalRepository
    ) {
        this.technicalIndicatorService = technicalIndicatorService;
        this.analysisService = analysisService;
        this.paperTradingService = paperTradingService;
        this.candleDataQualityService = candleDataQualityService;
        this.tradeSignalRepository = tradeSignalRepository;
    }

    /**
     * Automatic event-driven flow:
     * Closed Candle -> TechnicalIndicatorService -> technical_indicator
     * -> AnalysisService -> trade_signal -> PaperTradingService -> paper_position.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(CandleClosedEvent event) {
        try {
        	 log.info(
        	            "Received CandleClosedEvent: symbol={}, interval={}",
        	            event.symbol(),
        	            event.intervalCode()
        	        );
            CandleDataQualityResult dataQuality = candleDataQualityService.validate(
                    event.symbol(), event.intervalCode()
            );
            if (!dataQuality.valid()) {
                log.warn(
                        "Automatic analysis blocked by candle data quality: symbol={}, interval={}, warnings={}",
                        event.symbol(), event.intervalCode(), dataQuality.warnings()
                );
                return;
            }

            Optional<TechnicalIndicator> indicatorResult =
                    technicalIndicatorService.calculateAndPersist(
                            event.symbol(),
                            event.intervalCode(),
                            event.openTime()
                    );

            if (indicatorResult.isEmpty()) {
                log.info(
                        "Automatic analysis skipped: symbol={}, interval={}, openTime={}. " +
                                "The candle is not the latest closed candle or history is insufficient.",
                        event.symbol(),
                        event.intervalCode(),
                        event.openTime()
                );
                return;
            }
            log.info(
                    "going to analyze: symbol={}, interval={}, openTime={}. " +
                            "The candle is not the latest closed candle or history is insufficient.",
                    event.symbol(),
                    event.intervalCode(),
                    event.openTime()
            );

            TechnicalIndicator indicator = indicatorResult.get();

            if (tradeSignalRepository.existsBySymbolAndIntervalAndCandleOpenTime(
                    indicator.getSymbol(),
                    indicator.getIntervalCode(),
                    indicator.getCandleOpenTime()
            )) {
                log.info(
                        "Automatic analysis skipped: signal already exists for symbol={}, interval={}, candleOpenTime={}",
                        indicator.getSymbol(),
                        indicator.getIntervalCode(),
                        indicator.getCandleOpenTime()
                );
                return;
            }

            TradeSignal signal = analysisService.analyze(indicator);
            
            log.info(
                    "going to paper trading: symbol={}, interval={}, openTime={}. " +
                            "The candle is not the latest closed candle or history is insufficient.",
                    event.symbol(),
                    event.intervalCode(),
                    event.openTime()
            );
            Optional<PaperPosition> position = paperTradingService.processSignal(signal);

            log.info(
                    "Automatic candle flow completed: symbol={}, interval={}, openTime={}, " +
                            "score={}, decision={}, paperPositionOpened={}",
                    indicator.getSymbol(),
                    indicator.getIntervalCode(),
                    indicator.getCandleOpenTime(),
                    signal.getTotalScore(),
                    signal.getDecision(),
                    position.isPresent()
            );
        } catch (Exception exception) {
            log.error(
                    "Automatic candle flow failed for {} {} at {}",
                    event.symbol(),
                    event.intervalCode(),
                    event.openTime(),
                    exception
            );
        }
    }
}

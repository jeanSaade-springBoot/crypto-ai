package com.crypto.indicator.event;

import com.crypto.domain.PaperPosition;
import com.crypto.domain.TechnicalIndicator;
import com.crypto.domain.TradeSignal;
import com.crypto.indicator.service.TechnicalIndicatorService;
import com.crypto.service.AnalysisService;
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

    public CandleClosedEventListener(
            TechnicalIndicatorService technicalIndicatorService,
            AnalysisService analysisService,
            PaperTradingService paperTradingService
    ) {
        this.technicalIndicatorService = technicalIndicatorService;
        this.analysisService = analysisService;
        this.paperTradingService = paperTradingService;
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
            TradeSignal signal = analysisService.analyze(indicator);
            
            log.info(
                    "going to paper trading: symbol={}, interval={}, openTime={}. " +
                            "The candle is not the latest closed candle or history is insufficient.",
                    event.symbol(),
                    event.intervalCode(),
                    event.openTime()
            );
            Optional<PaperPosition> position = paperTradingService.openFromSignal(signal);

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

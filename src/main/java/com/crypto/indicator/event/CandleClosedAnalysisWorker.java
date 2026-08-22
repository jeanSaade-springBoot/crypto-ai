package com.crypto.indicator.event;

import com.crypto.domain.PaperPosition;
import com.crypto.domain.TechnicalIndicator;
import com.crypto.domain.TradeSignal;
import com.crypto.dto.CandleDataQualityResult;
import com.crypto.indicator.service.TechnicalIndicatorService;
import com.crypto.repository.TradeSignalRepository;
import com.crypto.service.AnalysisService;
import com.crypto.service.CandleDataQualityService;
import com.crypto.service.PaperTradingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The exact production analysis work formerly embedded in CandleClosedEventListener.
 *
 * FIX-043 changes WHERE the work runs, not WHAT trading logic runs. Keeping the complete existing
 * pipeline in one worker makes that boundary explicit and prevents future performance changes from
 * accidentally duplicating/replacing scoring, veto, wake-up or execution rules.
 */
@Component
public class CandleClosedAnalysisWorker {

    private static final Logger log = LoggerFactory.getLogger(CandleClosedAnalysisWorker.class);

    private final TechnicalIndicatorService technicalIndicatorService;
    private final AnalysisService analysisService;
    private final PaperTradingService paperTradingService;
    private final CandleDataQualityService candleDataQualityService;
    private final TradeSignalRepository tradeSignalRepository;

    public CandleClosedAnalysisWorker(
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

    public void process(CandleClosedEvent event) {
        try {
            log.info("Processing committed CandleClosedEvent: symbol={}, interval={}, openTime={}",
                    event.symbol(), event.intervalCode(), event.openTime());

            CandleDataQualityResult dataQuality = candleDataQualityService.validate(
                    event.symbol(), event.intervalCode());
            if (!dataQuality.valid()) {
                log.warn(
                        "Automatic analysis blocked by candle data quality: symbol={}, interval={}, warnings={}",
                        event.symbol(), event.intervalCode(), dataQuality.warnings());
                return;
            }

            Optional<TechnicalIndicator> indicatorResult = technicalIndicatorService.calculateAndPersist(
                    event.symbol(), event.intervalCode(), event.openTime());
            if (indicatorResult.isEmpty()) {
                log.info(
                        "Automatic analysis skipped: symbol={}, interval={}, openTime={}, reason=history/as-of candle unavailable",
                        event.symbol(), event.intervalCode(), event.openTime());
                return;
            }

            TechnicalIndicator indicator = indicatorResult.get();
            if (tradeSignalRepository.existsBySymbolAndIntervalAndCandleOpenTime(
                    indicator.getSymbol(), indicator.getIntervalCode(), indicator.getCandleOpenTime())) {
                log.info(
                        "Automatic analysis skipped: signal already exists for symbol={}, interval={}, candleOpenTime={}",
                        indicator.getSymbol(), indicator.getIntervalCode(), indicator.getCandleOpenTime());
                return;
            }

            TradeSignal signal = analysisService.analyze(indicator);
            Optional<PaperPosition> position = paperTradingService.processSignal(signal);

            log.info(
                    "Automatic candle flow completed: symbol={}, interval={}, openTime={}, score={}, decision={}, paperPositionOpened={}",
                    indicator.getSymbol(), indicator.getIntervalCode(), indicator.getCandleOpenTime(),
                    signal.getTotalScore(), signal.getDecision(), position.isPresent());
        } catch (Exception exception) {
            // FIX-043: never let one analysis failure kill the dispatcher lane. The next candle must
            // continue, and chronological recovery will later repair this specific missing row.
            log.error("Automatic candle flow failed for {} {} at {}",
                    event.symbol(), event.intervalCode(), event.openTime(), exception);
        }
    }
}

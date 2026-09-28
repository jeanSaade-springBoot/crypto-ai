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
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate sharedWorkEvidence;


    private static final Logger log = LoggerFactory.getLogger(CandleClosedAnalysisWorker.class);

    private final TechnicalIndicatorService technicalIndicatorService;
    private final AnalysisService analysisService;
    private final PaperTradingService paperTradingService;
    private final CandleDataQualityService candleDataQualityService;
    private final TradeSignalRepository tradeSignalRepository;
    private final CandleAnalysisExecutionCoordinator executionCoordinator;

    public CandleClosedAnalysisWorker(
            TechnicalIndicatorService technicalIndicatorService,
            AnalysisService analysisService,
            PaperTradingService paperTradingService,
            CandleDataQualityService candleDataQualityService,
            TradeSignalRepository tradeSignalRepository,
            CandleAnalysisExecutionCoordinator executionCoordinator
    ) {
        this.technicalIndicatorService = technicalIndicatorService;
        this.analysisService = analysisService;
        this.paperTradingService = paperTradingService;
        this.candleDataQualityService = candleDataQualityService;
        this.tradeSignalRepository = tradeSignalRepository;
        this.executionCoordinator = executionCoordinator;
    }

    public void process(CandleClosedEvent event) { processShared(event,null,true); }

    /** FIX-132 reports actual completion, never enqueue success. Historical-only
     * analysis may persist evidence but never registers or invokes wallet work. */
    public String processShared(CandleClosedEvent event, java.time.Instant closeTime, boolean liveSource) {
        return processShared(event,closeTime,liveSource,null);
    }

    /** FIX-132 event ownership is explicit, including across executor boundaries. */
    public String processShared(CandleClosedEvent event, java.time.Instant closeTime, boolean liveSource, Long sourceEventId) {
        // FIX-074: serialize only the exact candle identity against FIX-043 recovery.
        // This removes the duplicate trade_signal race while preserving parallelism across
        // independent symbols/timeframes and does not alter scoring or execution semantics.
        try (CandleAnalysisExecutionCoordinator.LockHandle ignored = executionCoordinator.lock(
                event.symbol(), event.intervalCode(), event.openTime())) {
            log.info("Processing committed CandleClosedEvent: symbol={}, interval={}, openTime={}",
                    event.symbol(), event.intervalCode(), event.openTime());

            CandleDataQualityResult dataQuality = candleDataQualityService.validate(
                    event.symbol(), event.intervalCode());
            if (!dataQuality.valid()) {
                log.warn(
                        "Automatic analysis blocked by candle data quality: symbol={}, interval={}, warnings={}",
                        event.symbol(), event.intervalCode(), dataQuality.warnings());
                return "DATA_QUALITY_BLOCKED";
            }

            Optional<TechnicalIndicator> indicatorResult = technicalIndicatorService.calculateAndPersist(
                    event.symbol(), event.intervalCode(), event.openTime());
            if (indicatorResult.isEmpty()) {
                log.info(
                        "Automatic analysis skipped: symbol={}, interval={}, openTime={}, reason=history/as-of candle unavailable",
                        event.symbol(), event.intervalCode(), event.openTime());
                return "HISTORY_UNAVAILABLE";
            }

            TechnicalIndicator indicator = indicatorResult.get();
            if (tradeSignalRepository.existsBySymbolAndIntervalAndCandleOpenTime(
                    indicator.getSymbol(), indicator.getIntervalCode(), indicator.getCandleOpenTime())) {
                log.info(
                        "Automatic analysis skipped: signal already exists for symbol={}, interval={}, candleOpenTime={}",
                        indicator.getSymbol(), indicator.getIntervalCode(), indicator.getCandleOpenTime());
                if(sourceEventId==null)return "ALREADY_EXISTS";
                if(!liveSource)return "HISTORICAL_ALREADY_EXISTS";
                // Existing signal is NOT processing completion. Only an attributed,
                // completed work row establishes a prior shared business outcome.
                Integer completed=sharedWorkEvidence.queryForObject("SELECT COUNT(*) FROM signal_processing_work w JOIN shared_market_event_delivery d ON d.source_event_id=w.source_event_id WHERE w.symbol=? AND w.interval_code=? AND w.candle_open_time=? AND w.status='COMPLETED' AND d.symbol=w.symbol AND d.interval_code=w.interval_code AND d.candle_open_time=w.candle_open_time",Integer.class,event.symbol(),event.intervalCode(),java.sql.Timestamp.from(event.openTime()));
                return completed>0?"ALREADY_COMPLETED":"REVIEW_REQUIRED";
            }

            if (closeTime != null && (!liveSource || !com.crypto.shared.SharedEventPolicy.closeEligible(event.intervalCode(),closeTime,java.time.Instant.now()))) {
                analysisService.analyzeRecovered(indicator,closeTime);
                return "HISTORICAL_ONLY";
            }
            TradeSignal signal = sourceEventId == null
                ? analysisService.analyzeForProcessing(indicator, com.crypto.execution.processing.ProcessingOrigin.WORKER)
                : analysisService.analyzeForProcessing(indicator, com.crypto.execution.processing.ProcessingOrigin.WORKER,sourceEventId);
            Optional<PaperPosition> position = sourceEventId == null ? paperTradingService.processSignal(signal)
                    : paperTradingService.processSharedSignal(signal,sourceEventId);

            log.info(
                    "Automatic candle flow completed: symbol={}, interval={}, openTime={}, score={}, decision={}, paperPositionOpened={}",
                    indicator.getSymbol(), indicator.getIntervalCode(), indicator.getCandleOpenTime(),
                    signal.getTotalScore(), signal.getDecision(), position.isPresent());
            return "COMPLETED";
        } catch (Exception exception) {
            // FIX-043: never let one analysis failure kill the dispatcher lane. The next candle must
            // continue. FIX-132 LIVE persists this failure for reconciliation; legacy
            // recovery must not independently execute an uncertain shared outcome.
            log.error("Automatic candle flow failed for {} {} at {}",
                    event.symbol(), event.intervalCode(), event.openTime(), exception);
            if(sourceEventId != null) {
                try {
                    String detail=exception.getClass().getName()+": "+exception.getMessage();
                    sharedWorkEvidence.update("UPDATE shared_market_event_delivery SET last_error=? WHERE source_event_id=? AND analysis_status IN ('RUNNING','REVIEW_REQUIRED')",
                            detail.substring(0,Math.min(1000,detail.length())),sourceEventId);
                } catch(Exception recordingFailure) {
                    log.error("[FIX-136][ANALYSIS_ERROR_RECORD_FAILED] event={}",sourceEventId,recordingFailure);
                }
            }
            return "REVIEW_REQUIRED";
        }
    }
}

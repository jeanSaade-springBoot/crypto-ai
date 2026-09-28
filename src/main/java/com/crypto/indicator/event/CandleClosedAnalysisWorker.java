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
        // FIX-137: one correlated boundary around every legacy/shared worker attempt.
        long started = System.nanoTime();
        String outcome = "UNCONFIRMED";
        log.info("[FIX-137][WORKER_START] event={}, symbol={}, interval={}, open={}, close={}, liveSource={}",
                sourceEventId,event.symbol(),event.intervalCode(),event.openTime(),closeTime,liveSource);
        try {
            outcome = processSharedTraced(event,closeTime,liveSource,sourceEventId,started);
            return outcome;
        } finally {
            log.info("[FIX-137][WORKER_END] event={}, symbol={}, interval={}, open={}, outcome={}, elapsedMs={}",
                    sourceEventId,event.symbol(),event.intervalCode(),event.openTime(),outcome,
                    (System.nanoTime()-started)/1_000_000);
        }
    }

    private String processSharedTraced(CandleClosedEvent event, java.time.Instant closeTime,
            boolean liveSource, Long sourceEventId, long started) {
        String stage = "COORDINATION_LOCK";
        traceStage(sourceEventId,event,stage,started);
        // FIX-074: serialize only the exact candle identity against FIX-043 recovery.
        // This removes the duplicate trade_signal race while preserving parallelism across
        // independent symbols/timeframes and does not alter scoring or execution semantics.
        try (CandleAnalysisExecutionCoordinator.LockHandle ignored = executionCoordinator.lock(
                event.symbol(), event.intervalCode(), event.openTime())) {
            log.info("Processing committed CandleClosedEvent: symbol={}, interval={}, openTime={}",
                    event.symbol(), event.intervalCode(), event.openTime());

            stage = "DATA_QUALITY";
            traceStage(sourceEventId,event,stage,started);
            CandleDataQualityResult dataQuality = candleDataQualityService.validate(
                    event.symbol(), event.intervalCode());
            if (!dataQuality.valid()) {
                log.warn(
                        "Automatic analysis blocked by candle data quality: symbol={}, interval={}, warnings={}",
                        event.symbol(), event.intervalCode(), dataQuality.warnings());
                return "DATA_QUALITY_BLOCKED";
            }

            stage = "INDICATORS";
            traceStage(sourceEventId,event,stage,started);
            Optional<TechnicalIndicator> indicatorResult = technicalIndicatorService.calculateAndPersist(
                    event.symbol(), event.intervalCode(), event.openTime());
            if (indicatorResult.isEmpty()) {
                log.info(
                        "Automatic analysis skipped: symbol={}, interval={}, openTime={}, reason=history/as-of candle unavailable",
                        event.symbol(), event.intervalCode(), event.openTime());
                return "HISTORY_UNAVAILABLE";
            }

            TechnicalIndicator indicator = indicatorResult.get();
            stage = "EXISTING_SIGNAL_CHECK";
            traceStage(sourceEventId,event,stage,started);
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
                if(completed==0) log.warn("[FIX-137][EXISTING_SIGNAL_REVIEW] event={}, symbol={}, interval={}, open={}, reason=NO_MATCHING_COMPLETED_SHARED_WORK; this does not prove work is absent",
                        sourceEventId,event.symbol(),event.intervalCode(),event.openTime());
                return completed>0?"ALREADY_COMPLETED":"REVIEW_REQUIRED";
            }

            if (closeTime != null && (!liveSource || !com.crypto.shared.SharedEventPolicy.closeEligible(event.intervalCode(),closeTime,java.time.Instant.now()))) {
                stage = "HISTORICAL_SIGNAL";
                traceStage(sourceEventId,event,stage,started);
                analysisService.analyzeRecovered(indicator,closeTime);
                return "HISTORICAL_ONLY";
            }
            stage = "LIVE_SIGNAL_AND_REGISTRATION";
            traceStage(sourceEventId,event,stage,started);
            TradeSignal signal = sourceEventId == null
                ? analysisService.analyzeForProcessing(indicator, com.crypto.execution.processing.ProcessingOrigin.WORKER)
                : analysisService.analyzeForProcessing(indicator, com.crypto.execution.processing.ProcessingOrigin.WORKER,sourceEventId);
            stage = "SIGNAL_PROCESSING";
            traceStage(sourceEventId,event,stage,started);
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
            log.error("[FIX-137][WORKER_STAGE_FAILED] event={}, symbol={}, interval={}, open={}, stage={}, elapsedMs={}",
                    sourceEventId,event.symbol(),event.intervalCode(),event.openTime(),stage,
                    (System.nanoTime()-started)/1_000_000,exception);
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
    private static void traceStage(Long id, CandleClosedEvent event, String stage, long started) {
        log.debug("[FIX-137][WORKER_STAGE] event={}, symbol={}, interval={}, open={}, stage={}, elapsedMs={}",
                id,event.symbol(),event.intervalCode(),event.openTime(),stage,
                (System.nanoTime()-started)/1_000_000);
    }

}

package com.crypto.service;

import com.crypto.domain.TradeSignal;
import com.crypto.execution.service.ExecutionReplayScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Best-effort, append-only FIX-154 evidence. It snapshots values then writes
 * on a bounded dedicated executor: an audit insert never nests a database
 * connection inside an execution transaction and never changes a BUY result.
 */
@Slf4j
@Component
public class BuyHtfAssessmentAuditStore {
    private final JdbcTemplate jdbc;
    private final Executor executor;
    private final Fix154Metrics metrics;
    @Autowired(required = false)
    private ExecutionReplayScope replayScope;

    public BuyHtfAssessmentAuditStore(JdbcTemplate jdbc, @Qualifier("fix154AuditExecutor") Executor executor,
                                      Fix154Metrics metrics) {
        this.jdbc = jdbc;
        this.executor = executor;
        this.metrics = metrics;
    }

    public void record(TradeSignal reference, String path,
                       HigherTimeframeBuyAssessmentService.Assessment assessment,
                       String finalResult, String reason) {
        // Replay signals are JDBC-generated and have no entity id. Replay parity
        // uses its own evidence tables; never write a misleading Production audit.
        if (reference == null || assessment == null || reference.getId() == null
                || (replayScope != null && replayScope.active())) return;
        AuditPayload payload = AuditPayload.from(reference, path, assessment, finalResult, reason);
        try {
            executor.execute(() -> persist(payload));
        } catch (RejectedExecutionException rejected) {
            metrics.auditWriteFailed();
            log.warn("FIX-154 assessment audit queue is full; BUY decision remains unchanged: path={}, referenceSignalId={}",
                    path, reference.getId());
        }
    }

    private void persist(AuditPayload p) {
        try {
            jdbc.update("""
                    INSERT INTO buy_htf_assessment_audit (
                      reference_signal_id, evaluation_path, evaluated_at, replay_mode,
                      one_hour_signal_id, one_hour_raw_decision, one_hour_effective_decision,
                      one_hour_candle_close_time, one_hour_age_seconds, one_hour_data_quality_valid, one_hour_usability_reason,
                      five_minute_signal_id, five_minute_raw_decision, five_minute_effective_decision,
                      five_minute_candle_close_time, five_minute_age_seconds, five_minute_data_quality_valid, five_minute_usability_reason,
                      confidence_threshold, assessment_band, hard_confirmation_present, max_initial_percent,
                      legacy_veto_result, final_result, final_reason)
                    VALUES (?, ?, ?, FALSE, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    p.referenceId, p.path, Timestamp.from(p.evaluatedAt),
                    p.one.id, p.one.decision, p.effectiveOne, timestamp(p.one.candleClose), p.one.ageSeconds, p.one.quality, p.one.usability,
                    p.five.id, p.five.decision, p.effectiveFive, timestamp(p.five.candleClose), p.five.ageSeconds, p.five.quality, p.five.usability,
                    HigherTimeframeBuyAssessmentService.HARD_CONFIDENCE_THRESHOLD, p.band, p.hardConfirmationPresent, p.maxInitialPercent,
                    p.legacyVeto, p.finalResult, p.reason);
        } catch (Exception e) {
            metrics.auditWriteFailed();
            log.warn("FIX-154 assessment audit was not recorded; BUY decision remains unchanged: path={}, referenceSignalId={}",
                    p.path, p.referenceId, e);
        }
    }

    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }

    private record Context(Long id, String decision, Instant candleClose, Long ageSeconds, Boolean quality, String usability) {
        static Context from(TradeSignal signal, Instant evaluatedAt) {
            if (signal == null) return new Context(null, null, null, null, null, "MISSING");
            Instant close = deriveCandleClose(signal);
            Long age = close == null ? null : Math.max(0L, Duration.between(close, evaluatedAt).getSeconds());
            return new Context(signal.getId(), signal.getDecision() == null ? null : signal.getDecision().name(), close, age,
                    signal.getDataQualityValid(), Boolean.TRUE.equals(signal.getDataQualityValid()) ? "AVAILABLE" : "QUALITY_UNKNOWN");
        }
    }
    private record AuditPayload(Long referenceId, String path, Instant evaluatedAt, Context one, Context five,
                                String effectiveOne, String effectiveFive, String band, boolean hardConfirmationPresent,
                                Integer maxInitialPercent, String legacyVeto, String finalResult, String reason) {
        static AuditPayload from(TradeSignal reference, String path, HigherTimeframeBuyAssessmentService.Assessment assessment,
                                 String finalResult, String reason) {
            Instant evaluated = reference.getGeneratedAt() == null ? Instant.now() : reference.getGeneratedAt();
            String legacy = "5M_BEARISH_VETO".equals(assessment.code()) || "1H_BEARISH_VETO".equals(assessment.code())
                    ? assessment.code() : null;
            return new AuditPayload(reference.getId(), path, evaluated,
                    Context.from(assessment.rawOneHour(), evaluated), Context.from(assessment.rawFiveMinute(), evaluated),
                    assessment.effectiveOneHour().name(), assessment.effectiveFiveMinute().name(), assessment.band().name(),
                    assessment.hardConfirmationPresent(), assessment.maxInitialPercent(), legacy, finalResult, trim(reason));
        }
    }
    private static Instant deriveCandleClose(TradeSignal signal) {
        if (signal.getCandleOpenTime() == null || signal.getInterval() == null) return null;
        long seconds = switch (signal.getInterval()) { case "1m" -> 60; case "5m" -> 300; case "1h" -> 3600; default -> 0; };
        return seconds == 0 ? null : signal.getCandleOpenTime().plusSeconds(seconds).minusMillis(1);
    }
    private static String trim(String value) { return value == null ? null : value.length() <= 1000 ? value : value.substring(0, 1000); }
}

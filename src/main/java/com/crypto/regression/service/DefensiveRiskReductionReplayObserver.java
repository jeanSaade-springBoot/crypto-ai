package com.crypto.regression.service;

import com.crypto.domain.ConfluenceStatus;
import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;

/**
 * FIX-11K Phase A — Replay research only.
 *
 * This observer records counterfactual defensive-risk-reduction candidates beside the
 * existing Production-parity replay. It never mutates the replay position, never calls
 * SELL execution, and never changes Production/Replay decision authority.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefensiveRiskReductionReplayObserver {
    private static final MathContext MC = MathContext.DECIMAL64;
    // Mirrors the freshness boundaries already used by Production validateSell().
    private static final Duration FIVE_MINUTE_MAX_AGE = Duration.ofMinutes(20);
    private static final Duration ONE_HOUR_MAX_AGE = Duration.ofHours(3);

    private final JdbcTemplate jdbcTemplate;

    public void observe(long runId, long positionId, TradeSignal currentOneMinute,
                        TradeSignal fiveMinute, TradeSignal oneHour, int consecutiveFinalStrongSells,
                        BigDecimal entryPrice, BigDecimal highestPriceSinceEntry) {
        if (currentOneMinute == null || currentOneMinute.getGeneratedAt() == null
                || currentOneMinute.getLatestPrice() == null || entryPrice == null
                || entryPrice.signum() <= 0 || highestPriceSinceEntry == null) {
            return;
        }

        // The experiment matrix begins at a streak of two. Recording from two upward lets
        // Phase A evaluate 2/3/4 later without hard-coding a final Production threshold.
        if (consecutiveFinalStrongSells < 2) return;
        if (!fresh(fiveMinute, currentOneMinute, FIVE_MINUTE_MAX_AGE)) {
            rejected(runId, positionId, currentOneMinute, consecutiveFinalStrongSells, "MISSING_OR_STALE_5M"); return;
        }
        if (!fresh(oneHour, currentOneMinute, ONE_HOUR_MAX_AGE)) {
            rejected(runId, positionId, currentOneMinute, consecutiveFinalStrongSells, "MISSING_OR_STALE_1H"); return;
        }
        if (!bearish(fiveMinute.getOriginalDecision())) {
            rejected(runId, positionId, currentOneMinute, consecutiveFinalStrongSells, "5M_RAW_NOT_BEARISH"); return;
        }
        if (fiveMinute.getDecision() != SignalDecision.NEUTRAL) {
            rejected(runId, positionId, currentOneMinute, consecutiveFinalStrongSells, "5M_FINAL_NOT_NEUTRAL"); return;
        }
        if (!conflict(fiveMinute.getConfluenceStatus())) {
            rejected(runId, positionId, currentOneMinute, consecutiveFinalStrongSells, "5M_NOT_MTF_CONFLICT"); return;
        }
        if (bullish(oneHour.getDecision())) {
            rejected(runId, positionId, currentOneMinute, consecutiveFinalStrongSells, "1H_BULLISH_VETO"); return;
        }

        BigDecimal currentPrice = currentOneMinute.getLatestPrice();
        // In exact-price Replay, ShadowPosition.highest is already advanced by the persisted
        // live-price stream. In legacy signal-price fallback, include this current 1m price so
        // the observer's peak remains replay-native and monotonic without mutating the position.
        BigDecimal effectiveHighest = highestPriceSinceEntry.max(currentPrice);
        BigDecimal currentProfit = percentage(entryPrice, currentPrice);
        BigDecimal peakProfit = percentage(entryPrice, effectiveHighest);
        BigDecimal giveback = peakProfit.subtract(currentProfit, MC);
        if (currentProfit.signum() <= 0) {
            rejected(runId, positionId, currentOneMinute, consecutiveFinalStrongSells, "POSITION_NOT_PROFITABLE"); return;
        }
        if (peakProfit.signum() <= 0 || giveback.signum() <= 0) {
            rejected(runId, positionId, currentOneMinute, consecutiveFinalStrongSells, "NO_POSITIVE_PEAK_GIVEBACK"); return;
        }

        jdbcTemplate.update("""
                INSERT INTO defensive_risk_reduction_observation_test
                (test_run_id, position_test_id, symbol, observed_at, source_signal_id,
                 current_price, entry_price, highest_price_since_entry,
                 current_profit_percent, peak_profit_percent, giveback_from_peak_percent,
                 consecutive_final_1m_strong_sell, five_minute_signal_id,
                 five_minute_original_decision, five_minute_final_decision, five_minute_confluence_status,
                 one_hour_signal_id, one_hour_final_decision, observation_code)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                runId, positionId, currentOneMinute.getSymbol(), Timestamp.from(currentOneMinute.getGeneratedAt()),
                currentOneMinute.getId(), currentPrice, entryPrice, effectiveHighest,
                currentProfit, peakProfit, giveback, consecutiveFinalStrongSells,
                fiveMinute.getId(), name(fiveMinute.getOriginalDecision()), name(fiveMinute.getDecision()),
                fiveMinute.getConfluenceStatus() == null ? null : fiveMinute.getConfluenceStatus().name(),
                oneHour.getId(), name(oneHour.getDecision()), "DEFENSIVE_RISK_REDUCTION_CANDIDATE");

        // Searchable server marker requested for FIX-11K evaluation. Values are deliberately
        // complete enough to correlate a log line with the persisted replay observation.
        log.info("FIX11K_DEFENSIVE_OBSERVER_CANDIDATE runId={} positionId={} symbol={} signalId={} observedAt={} "
                        + "price={} entry={} peak={} currentProfitPct={} peakProfitPct={} givebackPct={} streak1m={} "
                        + "fiveMinuteId={} fiveMinuteOriginal={} fiveMinuteFinal={} confluence={} "
                        + "oneHourId={} oneHourFinal={} action=OBSERVE_ONLY",
                runId, positionId, currentOneMinute.getSymbol(), currentOneMinute.getId(), currentOneMinute.getGeneratedAt(),
                currentPrice, entryPrice, effectiveHighest, currentProfit, peakProfit, giveback,
                consecutiveFinalStrongSells, fiveMinute.getId(), name(fiveMinute.getOriginalDecision()),
                name(fiveMinute.getDecision()), fiveMinute.getConfluenceStatus(), oneHour.getId(), name(oneHour.getDecision()));
    }

    private void rejected(long runId, long positionId, TradeSignal signal, int streak, String reason) {
        log.info("FIX11K_DEFENSIVE_OBSERVER_REJECTED runId={} positionId={} symbol={} signalId={} observedAt={} streak1m={} reason={} action=OBSERVE_ONLY",
                runId, positionId, signal.getSymbol(), signal.getId(), signal.getGeneratedAt(), streak, reason);
    }

    public void logRunStart(long runId, String symbol) {
        log.info("FIX11K_DEFENSIVE_OBSERVER_START runId={} symbol={} mode=PHASE_A_OBSERVE_ONLY productionMutation=false replayMutation=false", runId, symbol);
    }

    public void logRunSummary(long runId, String symbol) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM defensive_risk_reduction_observation_test WHERE test_run_id=?", Integer.class, runId);
        log.info("FIX11K_DEFENSIVE_OBSERVER_SUMMARY runId={} symbol={} candidates={} mode=PHASE_A_OBSERVE_ONLY action=NO_EXECUTION",
                runId, symbol, count == null ? 0 : count);
    }

    private boolean fresh(TradeSignal context, TradeSignal reference, Duration maxAge) {
        if (context == null || context.getGeneratedAt() == null || reference.getGeneratedAt() == null) return false;
        if (context.getGeneratedAt().isAfter(reference.getGeneratedAt())) return false;
        return !context.getGeneratedAt().isBefore(reference.getGeneratedAt().minus(maxAge));
    }

    private boolean conflict(ConfluenceStatus status) {
        return status == ConfluenceStatus.CONFLICT || status == ConfluenceStatus.STRONG_CONFLICT;
    }

    private boolean bearish(SignalDecision decision) {
        return decision == SignalDecision.SELL || decision == SignalDecision.STRONG_SELL;
    }

    private boolean bullish(SignalDecision decision) {
        return decision == SignalDecision.BUY || decision == SignalDecision.STRONG_BUY;
    }

    private BigDecimal percentage(BigDecimal entry, BigDecimal price) {
        return price.subtract(entry, MC).multiply(BigDecimal.valueOf(100), MC)
                .divide(entry, 8, RoundingMode.HALF_UP);
    }

    private String name(SignalDecision decision) {
        return decision == null ? null : decision.name();
    }
}

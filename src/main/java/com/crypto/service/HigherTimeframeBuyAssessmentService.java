package com.crypto.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import org.springframework.stereotype.Service;

/**
 * Pure FIX-154 BUY-context assessment. It deliberately returns effective
 * decisions separately and never mutates the JPA-managed context signals.
 */
@Service
public class HigherTimeframeBuyAssessmentService {
    public static final int HARD_CONFIDENCE_THRESHOLD = 70;

    public Assessment assess(TradeSignal fiveMinute, boolean fiveFresh,
                             TradeSignal oneHour, boolean oneFresh) {
        if (!fiveFresh) return Assessment.reject("MISSING_5M_CONFIRMATION", Band.NOT_APPLICABLE, fiveMinute, oneHour);
        if (!oneFresh) return Assessment.reject("MISSING_1H_CONTEXT", Band.NOT_APPLICABLE, fiveMinute, oneHour);

        SignalDecision five = decision(fiveMinute);
        SignalDecision one = decision(oneHour);
        if (bearish(five)) {
            boolean hard = hard(oneHour);
            return Assessment.reject("5M_BEARISH_VETO", hard ? Band.HARD_BEARISH_CANDIDATE : band(oneHour), fiveMinute, oneHour)
                    .withHardConfirmation(hard);
        }
        if (bearish(one) && !qualityValid(oneHour)) {
            return Assessment.reject("1H_BEARISH_VETO", Band.NOT_TRUSTED, fiveMinute, oneHour);
        }
        if (hard(oneHour)) {
            return Assessment.reject("HTF_TRUSTED_BEARISH_VETO", Band.HARD_BEARISH_CANDIDATE, fiveMinute, oneHour);
        }
        if (soft(oneHour)) {
            return Assessment.allow(Band.SOFT_CONFLICT, five, SignalDecision.NEUTRAL, 25, fiveMinute, oneHour);
        }
        return Assessment.allow(qualityValid(oneHour) ? Band.NO_CONFLICT : Band.NOT_TRUSTED,
                five, one, null, fiveMinute, oneHour);
    }

    private Band band(TradeSignal one) {
        if (!qualityValid(one)) return Band.NOT_TRUSTED;
        if (hard(one)) return Band.HARD_BEARISH_CANDIDATE;
        if (soft(one)) return Band.SOFT_CONFLICT;
        return Band.NO_CONFLICT;
    }
    private boolean hard(TradeSignal s) { return qualityValid(s) && decision(s) == SignalDecision.STRONG_SELL && confidence(s) >= HARD_CONFIDENCE_THRESHOLD; }
    private boolean soft(TradeSignal s) { return qualityValid(s) && (decision(s) == SignalDecision.SELL || (decision(s) == SignalDecision.STRONG_SELL && confidence(s) < HARD_CONFIDENCE_THRESHOLD)); }
    private boolean qualityValid(TradeSignal s) { return s != null && Boolean.TRUE.equals(s.getDataQualityValid()); }
    private int confidence(TradeSignal s) { return s == null ? 0 : s.getConfidenceScore(); }
    private SignalDecision decision(TradeSignal s) { return s == null || s.getDecision() == null ? SignalDecision.NEUTRAL : s.getDecision(); }
    private boolean bearish(SignalDecision d) { return d == SignalDecision.SELL || d == SignalDecision.STRONG_SELL; }

    public enum Band { NOT_APPLICABLE, NOT_TRUSTED, SOFT_CONFLICT, HARD_BEARISH_CANDIDATE, NO_CONFLICT }
    public record Assessment(boolean allowed, String code, Band band, SignalDecision effectiveFiveMinute,
                             SignalDecision effectiveOneHour, Integer maxInitialPercent,
                             boolean hardConfirmationPresent, TradeSignal rawFiveMinute, TradeSignal rawOneHour) {
        static Assessment reject(String code, Band band, TradeSignal five, TradeSignal one) {
            return new Assessment(false, code, band, decisionOf(five), decisionOf(one), null, false, five, one);
        }
        static Assessment allow(Band band, SignalDecision five, SignalDecision one, Integer cap, TradeSignal rawFive, TradeSignal rawOne) {
            return new Assessment(true, "HTF_CONTEXT_ALLOWED", band, five, one, cap, false, rawFive, rawOne);
        }
        Assessment withHardConfirmation(boolean value) { return new Assessment(allowed, code, band, effectiveFiveMinute, effectiveOneHour, maxInitialPercent, value, rawFiveMinute, rawOneHour); }
        private static SignalDecision decisionOf(TradeSignal s) { return s == null || s.getDecision() == null ? SignalDecision.NEUTRAL : s.getDecision(); }
    }
}

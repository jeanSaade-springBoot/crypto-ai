package com.crypto.position.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NearTpFailureProtectionPolicyTest {

    private final NearTpFailureProtectionPolicy policy = new NearTpFailureProtectionPolicy();

    @Test
    void missingFiveMinuteEvidenceNeverConfirmsHarvest() {
        Instant now = Instant.parse("2026-09-03T17:55:30Z");
        NearTpFailureProtectionPolicy.State rejected = new NearTpFailureProtectionPolicy.State(
                NearTpState.NEAR_TP_REJECTION_DETECTED, new BigDecimal("0.7964"), 1, 100L, false);

        TradeSignal bearish = signal(101L, "1m", now.minusSeconds(20), SignalDecision.NEUTRAL, SignalDecision.SELL);
        NearTpFailureProtectionPolicy.Evaluation result = policy.evaluate(
                rejected, new BigDecimal("0.7885"), new BigDecimal("0.796837871647"),
                new BigDecimal("0.7912"), now, bearish, null);

        assertThat(result.harvestEligible()).isFalse();
        assertThat(result.code()).isEqualTo("HOLD_MISSING_OR_STALE_EVIDENCE");
    }

    @Test
    void twoFreshBearishOneMinuteSignalsAndLostFiveMinuteSupportConfirmHarvest() {
        Instant t1 = Instant.parse("2026-09-03T17:54:59Z");
        NearTpFailureProtectionPolicy.State rejected = new NearTpFailureProtectionPolicy.State(
                NearTpState.NEAR_TP_REJECTION_DETECTED, new BigDecimal("0.7964"), 0, null, false);
        TradeSignal fiveWatch = signal(500L, "5m", t1, SignalDecision.WATCH, SignalDecision.WATCH);

        NearTpFailureProtectionPolicy.Evaluation first = policy.evaluate(
                rejected, new BigDecimal("0.7885"), new BigDecimal("0.796837871647"),
                new BigDecimal("0.7912"), t1.plusSeconds(10),
                signal(201L, "1m", t1, SignalDecision.NEUTRAL, SignalDecision.SELL), fiveWatch);
        assertThat(first.harvestEligible()).isFalse();
        assertThat(first.state().consecutiveBearishOneMinute()).isEqualTo(1);

        NearTpFailureProtectionPolicy.Evaluation second = policy.evaluate(
                first.state(), new BigDecimal("0.7885"), new BigDecimal("0.796837871647"),
                new BigDecimal("0.7912"), t1.plusSeconds(70),
                signal(202L, "1m", t1.plusSeconds(60), SignalDecision.NEUTRAL, SignalDecision.SELL), fiveWatch);

        assertThat(second.harvestEligible()).isTrue();
        assertThat(second.state().nearTpState()).isEqualTo(NearTpState.NEAR_TP_FAILURE_CONFIRMED);
    }


    @Test
    void singleBearishOneMinuteSignalNeverConfirmsHarvest() {
        Instant now = Instant.parse("2026-09-03T17:55:10Z");
        NearTpFailureProtectionPolicy.State rejected = new NearTpFailureProtectionPolicy.State(
                NearTpState.NEAR_TP_REJECTION_DETECTED, new BigDecimal("0.7964"), 0, null, false);

        NearTpFailureProtectionPolicy.Evaluation result = policy.evaluate(
                rejected, new BigDecimal("0.7885"), new BigDecimal("0.796837871647"),
                new BigDecimal("0.7912"), now,
                signal(301L, "1m", now.minusSeconds(10), SignalDecision.NEUTRAL, SignalDecision.SELL),
                signal(601L, "5m", now.minusSeconds(30), SignalDecision.WATCH, SignalDecision.WATCH));

        assertThat(result.harvestEligible()).isFalse();
        assertThat(result.code()).isEqualTo("HOLD_1M_BEARISH_NOT_PERSISTENT");
        assertThat(result.state().consecutiveBearishOneMinute()).isEqualTo(1);
    }

    @Test
    void staleFiveMinuteEvidenceNeverConfirmsHarvest() {
        Instant now = Instant.parse("2026-09-03T17:55:10Z");
        NearTpFailureProtectionPolicy.State rejected = new NearTpFailureProtectionPolicy.State(
                NearTpState.NEAR_TP_REJECTION_DETECTED, new BigDecimal("0.7964"), 2, 400L, false);

        NearTpFailureProtectionPolicy.Evaluation result = policy.evaluate(
                rejected, new BigDecimal("0.7885"), new BigDecimal("0.796837871647"),
                new BigDecimal("0.7912"), now,
                signal(401L, "1m", now.minusSeconds(10), SignalDecision.NEUTRAL, SignalDecision.SELL),
                signal(701L, "5m", now.minusSeconds(21 * 60L), SignalDecision.WATCH, SignalDecision.WATCH));

        assertThat(result.harvestEligible()).isFalse();
        assertThat(result.code()).isEqualTo("HOLD_MISSING_OR_STALE_EVIDENCE");
    }

    @Test
    void strongFiveMinuteOriginalBuyBlocksHarvestEvenAfterBearishPersistence() {
        Instant now = Instant.parse("2026-08-30T12:16:30Z");
        NearTpFailureProtectionPolicy.State rejected = new NearTpFailureProtectionPolicy.State(
                NearTpState.NEAR_TP_REJECTION_DETECTED, new BigDecimal("239.2"), 2, 301L, false);

        NearTpFailureProtectionPolicy.Evaluation result = policy.evaluate(
                rejected, new BigDecimal("238.7"), new BigDecimal("239.249271822677"),
                new BigDecimal("238.8"), now,
                signal(302L, "1m", now.minusSeconds(20), SignalDecision.NEUTRAL, SignalDecision.SELL),
                signal(600L, "5m", now.minusSeconds(90), SignalDecision.BUY, SignalDecision.BUY));

        assertThat(result.harvestEligible()).isFalse();
        assertThat(result.code()).isEqualTo("HOLD_5M_BULLISH_SUPPORT");
    }

    @Test
    void recoveryInsideRejectionBoundaryResetsBearishPersistence() {
        Instant now = Instant.parse("2026-09-03T20:58:00Z");
        NearTpFailureProtectionPolicy.State rejected = new NearTpFailureProtectionPolicy.State(
                NearTpState.NEAR_TP_REJECTION_DETECTED, new BigDecimal("6.395"), 2, 401L, false);

        // UNI #896 TP distance ~= 0.13328. A price of 6.380 is only ~11.3% giveback.
        NearTpFailureProtectionPolicy.Evaluation result = policy.evaluate(
                rejected, new BigDecimal("6.266"), new BigDecimal("6.399279514202"),
                new BigDecimal("6.380"), now,
                signal(402L, "1m", now.minusSeconds(10), SignalDecision.WATCH, SignalDecision.WATCH),
                signal(700L, "5m", now.minusSeconds(60), SignalDecision.WATCH, SignalDecision.STRONG_BUY));

        assertThat(result.harvestEligible()).isFalse();
        assertThat(result.code()).isEqualTo("NEAR_TP_RECOVERY");
        assertThat(result.state().nearTpState()).isEqualTo(NearTpState.NEAR_TP_ARMED);
        assertThat(result.state().consecutiveBearishOneMinute()).isZero();
    }

    private TradeSignal signal(long id, String interval, Instant at,
                               SignalDecision decision, SignalDecision original) {
        TradeSignal signal = new TradeSignal();
        signal.setId(id);
        signal.setSymbol("TESTUSDT");
        signal.setInterval(interval);
        signal.setGeneratedAt(at);
        signal.setDecision(decision);
        signal.setOriginalDecision(original);
        return signal;
    }
}

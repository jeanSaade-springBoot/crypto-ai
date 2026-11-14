package com.crypto.regression.replay;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * FIX-156: immutable, as-of policy evidence consumed by Replay only.
 * Production continues to read its live configuration through its own paths.
 */
public record ReplayPolicyProfile(
        long runId,
        String profileHash,
        Instant effectiveAt,
        String policyFingerprint,
        String executionProfile,
        boolean requireNewBuyTransition,
        BigDecimal minimumUsdtReserve,
        BigDecimal baseTradeAmountUsdt,
        int maximumDailyNewPositions,
        String exchangeConstraintFixtureHash,
        BigDecimal minimumExecutableNotionalUsdt,
        boolean dynamicProfitLockEnabled,
        BigDecimal profitLockActivationPercent,
        BigDecimal profitLockInitialPercent,
        BigDecimal profitLockTrailStepPercent,
        BigDecimal fixedNotionalUsdt) {

    public ReplayPolicyProfile {
        if (runId <= 0 || profileHash == null || profileHash.isBlank()) {
            throw new IllegalArgumentException("FIX-156 Replay policy profile hash is required");
        }
        if (effectiveAt == null || policyFingerprint == null || policyFingerprint.isBlank()
                || executionProfile == null || executionProfile.isBlank()
                || fixedNotionalUsdt == null || fixedNotionalUsdt.signum() <= 0
                || minimumUsdtReserve == null || minimumUsdtReserve.signum() < 0
                || baseTradeAmountUsdt == null || baseTradeAmountUsdt.signum() < 0
                || maximumDailyNewPositions < 0
                || exchangeConstraintFixtureHash == null || exchangeConstraintFixtureHash.isBlank()
                || minimumExecutableNotionalUsdt == null || minimumExecutableNotionalUsdt.signum() <= 0
                || profitLockActivationPercent == null || profitLockInitialPercent == null
                || profitLockTrailStepPercent == null) {
            throw new IllegalArgumentException("FIX-156 Replay policy profile is incomplete");
        }
        validatePercent("profitLockActivationPercent", profitLockActivationPercent);
        validatePercent("profitLockInitialPercent", profitLockInitialPercent);
        validatePercent("profitLockTrailStepPercent", profitLockTrailStepPercent);
    }

    private static void validatePercent(String name, BigDecimal value) {
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("FIX-156 " + name + " is outside 0..100");
        }
    }
}

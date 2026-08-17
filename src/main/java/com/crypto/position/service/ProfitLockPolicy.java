package com.crypto.position.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure Dynamic Profit Lock policy shared by live production and Proven/Regression.
 * Keep all percentage/progression math here so replay can never drift from production.
 */
@Component
public class ProfitLockPolicy {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 12;

    public State evaluate(BigDecimal entry, BigDecimal target, BigDecimal current,
                          BigDecimal previousHighest, boolean previouslyActive, BigDecimal previousLock,
                          boolean enabled, BigDecimal activationPercent, BigDecimal initialLockPercent,
                          BigDecimal trailStepPercent) {
        if (entry == null || target == null || current == null || entry.signum() <= 0 || target.compareTo(entry) <= 0) {
            return new State(previousHighest, previouslyActive, previousLock, BigDecimal.ZERO, false);
        }

        BigDecimal highest = previousHighest;
        if (highest == null || highest.compareTo(entry) < 0) highest = entry;
        if (current.compareTo(highest) > 0) highest = current;
        if (!enabled) return new State(highest, false, null, BigDecimal.ZERO, false);

        BigDecimal distance = target.subtract(entry);
        BigDecimal progress = highest.subtract(entry).multiply(HUNDRED)
                .divide(distance, 6, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
        BigDecimal activation = nvl(activationPercent, BigDecimal.valueOf(70));
        BigDecimal initial = nvl(initialLockPercent, BigDecimal.valueOf(40));
        BigDecimal step = nvl(trailStepPercent, BigDecimal.valueOf(10));

        boolean active = previouslyActive;
        BigDecimal lock = previousLock;
        if (progress.compareTo(activation) >= 0) {
            active = true;
            BigDecimal completed = progress.subtract(activation).max(BigDecimal.ZERO)
                    .divide(step, 0, RoundingMode.DOWN);
            BigDecimal lockedProgress = initial.add(completed.multiply(step));
            BigDecimal maximum = progress.subtract(step).max(initial);
            lockedProgress = lockedProgress.min(maximum).max(initial);
            BigDecimal candidate = entry.add(distance.multiply(lockedProgress)
                    .divide(HUNDRED, SCALE, RoundingMode.HALF_UP));
            candidate = candidate.max(entry.multiply(BigDecimal.valueOf(1.0005)));
            if (lock == null || candidate.compareTo(lock) > 0) lock = candidate;
        }
        boolean triggered = active && lock != null && current.compareTo(lock) <= 0;
        return new State(highest, active, lock, progress, triggered);
    }

    private static BigDecimal nvl(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    public record State(BigDecimal highestPrice, boolean active, BigDecimal lockPrice,
                        BigDecimal progressPercent, boolean triggered) {}
}

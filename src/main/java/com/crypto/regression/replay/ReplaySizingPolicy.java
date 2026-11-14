package com.crypto.regression.replay;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * FIX-156: Replay-only mirror of the approved sizing arithmetic. It consumes a
 * frozen profile and deliberately has no wallet repository dependency.
 */
@Component
public final class ReplaySizingPolicy {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int SCALE = 12;

    public Plan plan(BigDecimal availableUsdt, BigDecimal minimumReserve, BigDecimal dailyTradeBudget,
                     int requestedPositionPercent, int currentAllocatedPercent,
                     boolean newPosition, int maximumNewPositions, int executedNewPositions,
                     BigDecimal price) {
        int requested = Math.max(1, Math.min(100, requestedPositionPercent));
        int current = Math.max(0, currentAllocatedPercent);
        int normalized = Math.min(requested, Math.max(0, 100 - current));
        if (normalized <= 0) return Plan.reject("ALLOCATION_FULL");
        if (newPosition && maximumNewPositions > 0 && executedNewPositions >= maximumNewPositions) {
            return Plan.reject("DAILY_POSITION_LIMIT");
        }
        BigDecimal available = value(availableUsdt).subtract(value(minimumReserve)).max(ZERO);
        BigDecimal spend = value(dailyTradeBudget).multiply(BigDecimal.valueOf(normalized))
                .divide(BigDecimal.valueOf(100), SCALE, RoundingMode.DOWN);
        if (spend.signum() <= 0) return Plan.reject("ZERO_BUDGET");
        if (available.compareTo(spend) < 0) return Plan.reject("INSUFFICIENT_USDT_ABOVE_RESERVE");
        if (price == null || price.signum() <= 0) return Plan.reject("INVALID_PRICE");
        BigDecimal quantity = spend.divide(price, SCALE, RoundingMode.DOWN);
        return quantity.signum() <= 0 ? Plan.reject("ZERO_QUANTITY")
                : new Plan(true, normalized, spend, quantity, "APPROVED");
    }

    public BigDecimal initialDailyBudget(BigDecimal availableUsdt, BigDecimal minimumReserve,
                                         BigDecimal baseTradeAmount, int maximumNewPositions) {
        BigDecimal tradable = value(availableUsdt).subtract(value(minimumReserve)).max(ZERO);
        if (tradable.signum() <= 0) return ZERO;
        return maximumNewPositions == 0 ? value(baseTradeAmount).min(tradable)
                : tradable.divide(BigDecimal.valueOf(maximumNewPositions), SCALE, RoundingMode.DOWN);
    }

    private BigDecimal value(BigDecimal input) { return input == null ? ZERO : input; }

    public record Plan(boolean allowed, int normalizedPositionPercent, BigDecimal spend,
                       BigDecimal quantity, String code) {
        private static Plan reject(String code) { return new Plan(false, 0, ZERO, ZERO, code); }
    }
}

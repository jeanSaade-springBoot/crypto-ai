package com.crypto.wallet.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure wallet BUY sizing/eligibility policy shared by production and Proven/Regression.
 * Persistence is intentionally outside this class; all reserve/budget/allocation math lives here.
 */
@Component
public class WalletExecutionSizingPolicy {
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
        BigDecimal available = nvl(availableUsdt).subtract(nvl(minimumReserve)).max(ZERO);
        BigDecimal spend = nvl(dailyTradeBudget).multiply(BigDecimal.valueOf(normalized))
                .divide(BigDecimal.valueOf(100), SCALE, RoundingMode.DOWN);
        if (spend.signum() <= 0) return Plan.reject("ZERO_BUDGET");
        if (available.compareTo(spend) < 0) return Plan.reject("INSUFFICIENT_USDT_ABOVE_RESERVE");
        if (price == null || price.signum() <= 0) return Plan.reject("INVALID_PRICE");
        BigDecimal quantity = spend.divide(price, SCALE, RoundingMode.DOWN);
        if (quantity.signum() <= 0) return Plan.reject("ZERO_QUANTITY");
        return new Plan(true, normalized, spend, quantity, "APPROVED");
    }

    public BigDecimal initialDailyBudget(BigDecimal availableUsdt, BigDecimal minimumReserve,
                                         BigDecimal baseTradeAmount, int maximumNewPositions) {
        BigDecimal tradable = nvl(availableUsdt).subtract(nvl(minimumReserve)).max(ZERO);
        if (tradable.signum() <= 0) return ZERO;
        return maximumNewPositions == 0
                ? nvl(baseTradeAmount).min(tradable)
                : tradable.divide(BigDecimal.valueOf(maximumNewPositions), SCALE, RoundingMode.DOWN);
    }

    private BigDecimal nvl(BigDecimal value) { return value == null ? ZERO : value; }

    public record Plan(boolean allowed, int normalizedPositionPercent, BigDecimal spend,
                       BigDecimal quantity, String code) {
        static Plan reject(String code) { return new Plan(false, 0, ZERO, ZERO, code); }
    }
}

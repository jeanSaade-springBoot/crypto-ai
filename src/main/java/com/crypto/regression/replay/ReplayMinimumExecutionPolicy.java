package com.crypto.regression.replay;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;

/** FIX-156: immutable-fixture minimum-order policy; never calls Binance. */
@Component
public final class ReplayMinimumExecutionPolicy {
    private static final MathContext MC = MathContext.DECIMAL64;

    public Evaluation evaluate(BigDecimal minimumNotional, BigDecimal quantity, BigDecimal price) {
        if (minimumNotional == null || minimumNotional.signum() <= 0
                || quantity == null || quantity.signum() <= 0 || price == null || price.signum() <= 0) {
            return new Evaluation(false, "MISSING_OR_INVALID_REPLAY_CONSTRAINT", BigDecimal.ZERO, minimumNotional);
        }
        BigDecimal requested = quantity.multiply(price, MC);
        return requested.compareTo(minimumNotional) < 0
                ? new Evaluation(false, "BELOW_BINANCE_MINIMUM", requested, minimumNotional)
                : new Evaluation(true, "EXECUTABLE", requested, minimumNotional);
    }

    public record Evaluation(boolean executable, String code, BigDecimal requestedNotional,
                             BigDecimal minimumNotional) { }
}

package com.crypto.regression.replay;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Deterministic, Replay-only policy for the late-entry research ledger.
 * It never authorizes a Production or paper-wallet action.
 */
public final class ReplayCandidateRecoveryPolicy {
    public static final BigDecimal MIN_SCORE = new BigDecimal("75");
    public static final BigDecimal MIN_RAW_CONFIDENCE = new BigDecimal("65");
    public static final BigDecimal RANGE_CHASE_LIMIT = new BigDecimal("0.0075");
    public static final BigDecimal FEE_RATE = new BigDecimal("0.0010");
    public static final BigDecimal SLIPPAGE_RATE = new BigDecimal("0.0005");
    public static final BigDecimal VIRTUAL_NOTIONAL_USDT = new BigDecimal("100.00");

    private ReplayCandidateRecoveryPolicy() { }

    public static boolean supportedBlocker(String blocker) {
        return "RANGE_ENTRY_LOCATION".equals(normalize(blocker)) || "ATR_RISK".equals(normalize(blocker));
    }

    public static boolean qualifies(int score, Integer rawConfidence, String blocker) {
        return score >= MIN_SCORE.intValue()
                && rawConfidence != null && rawConfidence >= MIN_RAW_CONFIDENCE.intValue()
                && supportedBlocker(blocker);
    }

    public static BigDecimal releaseCeiling(BigDecimal candidatePrice, BigDecimal retracementPrice, String blocker) {
        if (candidatePrice == null) throw new IllegalArgumentException("candidatePrice is required");
        if ("ATR_RISK".equals(normalize(blocker)) && retracementPrice != null && retracementPrice.signum() > 0) {
            return retracementPrice;
        }
        return candidatePrice.multiply(BigDecimal.ONE.add(RANGE_CHASE_LIMIT)).setScale(12, RoundingMode.HALF_UP);
    }

    public static BigDecimal adverseBuyFill(BigDecimal decisionPrice) {
        return decisionPrice.multiply(BigDecimal.ONE.add(SLIPPAGE_RATE)).setScale(12, RoundingMode.HALF_UP);
    }

    public static BigDecimal adverseSellFill(BigDecimal decisionPrice) {
        return decisionPrice.multiply(BigDecimal.ONE.subtract(SLIPPAGE_RATE)).setScale(12, RoundingMode.HALF_UP);
    }

    public static Settlement settle(BigDecimal entryFill, BigDecimal exitFill) {
        BigDecimal quantity = VIRTUAL_NOTIONAL_USDT.divide(entryFill, 18, RoundingMode.HALF_UP);
        BigDecimal proceeds = quantity.multiply(exitFill);
        BigDecimal fees = VIRTUAL_NOTIONAL_USDT.multiply(FEE_RATE).add(proceeds.multiply(FEE_RATE));
        BigDecimal pnl = proceeds.subtract(VIRTUAL_NOTIONAL_USDT).subtract(fees).setScale(12, RoundingMode.HALF_UP);
        BigDecimal pct = pnl.multiply(new BigDecimal("100")).divide(VIRTUAL_NOTIONAL_USDT, 8, RoundingMode.HALF_UP);
        return new Settlement(pnl, pct, fees.setScale(12, RoundingMode.HALF_UP));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public record Settlement(BigDecimal realizedPnlUsdt, BigDecimal realizedPnlPercent, BigDecimal feesUsdt) { }
}

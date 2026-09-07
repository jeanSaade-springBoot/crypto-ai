package com.crypto.execution.service;

import java.time.Instant;

/** FIX-122: pure, shared candle-lineage policy. No scoring, price, or wallet authority. */
public final class StopLossEvidencePolicy {
    private StopLossEvidencePolicy() {}

    public record Boundary(long executionId, Instant executedAt) {}
    public record Stamp(Boundary boundary) {}

    public static String exclusion(String interval, Instant candleOpen, Instant generatedAt,
                                   Instant evaluationAt, Boundary boundary) {
        if (boundary == null) return null;
        if (!"1m".equals(interval) || candleOpen == null || generatedAt == null || evaluationAt == null)
            return "LINEAGE_UNRESOLVED";
        // Binance klines have an inclusive millisecond close: next minute minus 1 ms.
        // generatedAt is availability only; delayed processing cannot refresh an old candle.
        Instant close = candleOpen.plusSeconds(60).minusMillis(1);
        if (!close.isAfter(boundary.executedAt())) return "PRE_STOP_CANDLE";
        if (close.isAfter(evaluationAt) || generatedAt.isAfter(evaluationAt)) return "NOT_AVAILABLE";
        return null;
    }
}

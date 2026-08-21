package com.crypto.dto;

import java.math.BigDecimal;

/**
 * Immutable audit result for the RANGE_MEAN_REVERSION entry-location guard.
 * This guard never changes the technical BUY/STRONG_BUY score; it only decides
 * whether a new immediate entry is allowed at the current position inside the
 * Bollinger range.
 */
public record RangeEntryLocationAssessment(
        boolean applicable,
        boolean entryAllowed,
        BigDecimal bollingerPositionPercent,
        boolean expansionException,
        String explanation
) {
    public static RangeEntryLocationAssessment notApplicable() {
        return new RangeEntryLocationAssessment(false, true, null, false,
                "Range entry-location guard is not applicable to the selected strategy/decision.");
    }
}

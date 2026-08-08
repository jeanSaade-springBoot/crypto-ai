package com.crypto.wallet.domain;

import java.util.Locale;

/**
 * Controls how much multi-timeframe confirmation is required before a 1m BUY
 * may reach wallet execution. It does not change indicator scoring or the
 * FinalDecisionService output.
 */
public enum ExecutionProfile {
    CONSERVATIVE,
    BALANCED,
    AGGRESSIVE;

    public static ExecutionProfile from(String value) {
        if (value == null || value.isBlank()) {
            return BALANCED;
        }
        try {
            return ExecutionProfile.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BALANCED;
        }
    }
}

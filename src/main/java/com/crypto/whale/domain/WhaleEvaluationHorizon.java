package com.crypto.whale.domain;

import java.time.Duration;

public enum WhaleEvaluationHorizon {
    ONE_HOUR(Duration.ofHours(1), "1h"), FOUR_HOURS(Duration.ofHours(4), "4h"), TWENTY_FOUR_HOURS(Duration.ofHours(24), "24h");
    private final Duration duration; private final String code;
    WhaleEvaluationHorizon(Duration duration, String code) { this.duration = duration; this.code = code; }
    public Duration duration() { return duration; }
    public String code() { return code; }
    public static WhaleEvaluationHorizon fromCode(String code) {
        for (WhaleEvaluationHorizon value : values()) if (value.code.equalsIgnoreCase(code)) return value;
        throw new IllegalArgumentException("Unsupported whale evaluation horizon: " + code);
    }
}

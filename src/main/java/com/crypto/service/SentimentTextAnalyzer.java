package com.crypto.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;

@Component
public class SentimentTextAnalyzer {

    private static final Set<String> POSITIVE = Set.of(
            "adoption", "approval", "approved", "bullish", "breakout", "gain", "gains",
            "growth", "high", "institutional", "launch", "partnership", "positive", "profit",
            "rally", "record", "recovery", "surge", "upgrade", "uptrend", "win"
    );

    private static final Set<String> NEGATIVE = Set.of(
            "attack", "ban", "bearish", "breach", "collapse", "crash", "decline", "delay",
            "downtrend", "drop", "exploit", "fraud", "hack", "lawsuit", "liquidation", "loss",
            "negative", "outflow", "rejection", "risk", "scam", "selloff", "shutdown"
    );

    private static final Set<String> NEGATIONS = Set.of(
            "not", "no", "never", "without", "hardly"
    );

    public Result analyze(String text) {
        if (text == null || text.isBlank()) {
            return new Result(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
        }

        String[] tokens = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s'-]", " ")
                .trim()
                .split("\\s+");

        int positive = 0;
        int negative = 0;
        boolean negateNext = false;

        for (String token : tokens) {
            if (NEGATIONS.contains(token)) {
                negateNext = true;
                continue;
            }

            int polarity = POSITIVE.contains(token) ? 1 : NEGATIVE.contains(token) ? -1 : 0;
            if (polarity != 0 && negateNext) {
                polarity *= -1;
            }

            if (polarity > 0) positive++;
            if (polarity < 0) negative++;
            if (polarity != 0) negateNext = false;
        }

        int matched = positive + negative;
        if (matched == 0) {
            return new Result(BigDecimal.ZERO, BigDecimal.valueOf(0.20), 0, 0);
        }

        BigDecimal score = BigDecimal.valueOf(positive - negative)
                .divide(BigDecimal.valueOf(matched), 6, RoundingMode.HALF_UP);
        BigDecimal coverage = BigDecimal.valueOf(matched)
                .divide(BigDecimal.valueOf(Math.max(tokens.length, 1)), 6, RoundingMode.HALF_UP);
        BigDecimal confidence = BigDecimal.valueOf(0.35)
                .add(BigDecimal.valueOf(Math.min(0.60, coverage.doubleValue() * 8)))
                .min(BigDecimal.valueOf(0.95));

        return new Result(score, confidence, positive, negative);
    }

    public record Result(
            BigDecimal score,
            BigDecimal confidence,
            int positiveMatches,
            int negativeMatches
    ) {
    }
}

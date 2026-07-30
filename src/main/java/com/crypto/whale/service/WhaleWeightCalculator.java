package com.crypto.whale.service;

import com.crypto.whale.config.WhaleProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;

@Component
@RequiredArgsConstructor
public class WhaleWeightCalculator {
    private static final MathContext MC = MathContext.DECIMAL64;
    private final WhaleProperties properties;

    public BigDecimal calculate(long samples, BigDecimal accuracy, BigDecimal averageQuality, BigDecimal oldWeight) {
        WhaleProperties.Learning cfg = properties.learning();
        if (samples <= 0) return cfg.initialWeight();
        BigDecimal count = BigDecimal.valueOf(samples);
        BigDecimal sampleConfidence = count.divide(count.add(BigDecimal.valueOf(cfg.priorSampleSize())), MC);
        BigDecimal accuracyStrength = accuracy.subtract(new BigDecimal("0.50")).multiply(new BigDecimal("2"), MC)
                .max(BigDecimal.ZERO).min(BigDecimal.ONE);
        BigDecimal quality = averageQuality.max(BigDecimal.ZERO).min(BigDecimal.ONE);
        BigDecimal target = cfg.initialWeight().add(
                accuracyStrength.multiply(new BigDecimal("0.70"), MC)
                        .add(quality.multiply(new BigDecimal("0.30"), MC), MC)
                        .multiply(sampleConfidence, MC)
                        .multiply(BigDecimal.ONE.subtract(cfg.initialWeight()), MC), MC);
        BigDecimal previous = oldWeight == null ? cfg.initialWeight() : oldWeight;
        BigDecimal smoothed = previous.multiply(BigDecimal.ONE.subtract(cfg.smoothingFactor()), MC)
                .add(target.multiply(cfg.smoothingFactor(), MC), MC);
        return smoothed.max(cfg.minimumWeight()).min(cfg.maximumWeight());
    }
}

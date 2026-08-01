package com.crypto.dto;

import com.crypto.domain.DerivativesPositioningStatus;
import com.crypto.domain.SignalDecision;

import java.math.BigDecimal;
import java.time.Instant;

public record DerivativesPositioningResult(
        DerivativesPositioningStatus status,
        SignalDecision finalDecision,
        boolean entryAllowed,
        BigDecimal fundingRate,
        BigDecimal fundingPercentile,
        BigDecimal openInterest,
        BigDecimal openInterestValue,
        BigDecimal openInterestChangePercent,
        BigDecimal priceChangePercent,
        int fundingSamples,
        String futuresPeriod,
        int confidenceAdjustment,
        String explanation,
        Instant evaluatedAt
) {}

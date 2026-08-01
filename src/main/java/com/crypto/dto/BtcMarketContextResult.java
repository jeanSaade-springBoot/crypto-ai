package com.crypto.dto;

import com.crypto.domain.BtcContextStatus;
import com.crypto.domain.BtcRelationshipType;
import com.crypto.domain.SignalDecision;

import java.math.BigDecimal;
import java.time.Instant;

public record BtcMarketContextResult(
        BtcRelationshipType relationshipType,
        BtcContextStatus contextStatus,
        SignalDecision finalDecision,
        boolean entryAllowed,
        String btcInterval,
        SignalDecision btcDecision,
        Integer btcTrendScore,
        BigDecimal correlation,
        BigDecimal beta,
        int sampleSize,
        BigDecimal influenceFactor,
        boolean stable,
        Instant evaluatedAt,
        Instant btcSignalGeneratedAt,
        String explanation
) {
}

package com.crypto.position.service;

import com.crypto.domain.TradeSignal;
import org.springframework.stereotype.Component;

/**
 * Shared immutable-BUY-thesis deterioration policy.
 *
 * Production position analysis and take-profit continuation MUST use the same
 * deterioration definition. This prevents one component from saying HOLD while
 * another closes the same position because of a different hard-coded trend floor.
 *
 * The policy compares the immutable entry thesis with the current TradeSignal.
 * It does not change signal generation, BUY/SELL decisions, ATR entry logic or
 * higher-timeframe vetoes.
 */
@Component
public class PositionThesisPressurePolicy {

    public ThesisPressure evaluate(Integer entryTrend,
                                   Integer entryStructure,
                                   Integer entryMomentum,
                                   Integer entryVolume,
                                   Integer entryConfidence,
                                   Integer entryTotal,
                                   TradeSignal current) {
        if (current == null) {
            return new ThesisPressure(0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0,
                    8, 5);
        }

        int eTrend = valueOrCurrent(entryTrend, current.getTrendScore());
        int eStructure = valueOrCurrent(entryStructure, current.getTrendStructureScore());
        int eMomentum = valueOrCurrent(entryMomentum, current.getMomentumScore());
        int eVolume = valueOrCurrent(entryVolume, current.getVolumeScore());
        int eConfidence = valueOrCurrent(entryConfidence, current.getConfidenceScore());
        int eTotal = valueOrCurrent(entryTotal, current.getTotalScore());

        int trendDrop = positiveDrop(eTrend, current.getTrendScore());
        int structureDrop = positiveDrop(eStructure, current.getTrendStructureScore());
        int momentumDrop = positiveDrop(eMomentum, current.getMomentumScore());
        int volumeDrop = positiveDrop(eVolume, current.getVolumeScore());
        int confidenceDrop = positiveDrop(eConfidence, current.getConfidenceScore());
        int totalDrop = positiveDrop(eTotal, current.getTotalScore());

        // Keep these thresholds identical to the historically proven
        // PositionManagementService immutable-thesis calculation.
        int trendPressure = Math.min(8,
                points(trendDrop, 3, 6, 10, 3)
                        + points(structureDrop, 2, 3, 5, 2)
                        + (current.getTrendScore() <= 8 ? 2 : current.getTrendScore() <= 13 ? 1 : 0));

        int momentumPressure = Math.min(5,
                points(momentumDrop, 3, 5, 8, 2)
                        + (current.getMomentumScore() <= 4 ? 2 : current.getMomentumScore() <= 8 ? 1 : 0));

        return new ThesisPressure(
                eTrend, current.getTrendScore(), trendDrop,
                eStructure, current.getTrendStructureScore(), structureDrop,
                eMomentum, current.getMomentumScore(), momentumDrop,
                eVolume, current.getVolumeScore(), volumeDrop,
                eConfidence, current.getConfidenceScore(), confidenceDrop,
                eTotal, current.getTotalScore(), totalDrop,
                trendPressure, momentumPressure);
    }

    private int valueOrCurrent(Integer entryValue, int currentValue) {
        return entryValue == null ? currentValue : entryValue;
    }

    private int positiveDrop(int entry, int current) {
        return Math.max(0, entry - current);
    }

    private int points(int drop, int low, int medium, int high, int highPoints) {
        if (drop >= high) return highPoints;
        if (drop >= medium) return Math.max(1, highPoints - 1);
        if (drop >= low) return 1;
        return 0;
    }

    public record ThesisPressure(
            int entryTrend, int currentTrend, int trendDrop,
            int entryStructure, int currentStructure, int structureDrop,
            int entryMomentum, int currentMomentum, int momentumDrop,
            int entryVolume, int currentVolume, int volumeDrop,
            int entryConfidence, int currentConfidence, int confidenceDrop,
            int entryTotal, int currentTotal, int totalDrop,
            int trendPressure, int momentumPressure) {
    }
}

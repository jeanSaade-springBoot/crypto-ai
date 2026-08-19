package com.crypto.position.service;

import com.crypto.domain.SignalDecision;
import com.crypto.domain.TradeSignal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PositionThesisPressurePolicyTest {
    private final PositionThesisPressurePolicy policy = new PositionThesisPressurePolicy();

    @Test
    void reproducesTheExactSolImmutableThesisPressureUsedByProductionAndReplay() {
        TradeSignal current = TradeSignal.builder()
                .decision(SignalDecision.WATCH)
                .totalScore(63)
                .confidenceScore(68)
                .trendScore(16)
                .trendStructureScore(2)
                .momentumScore(15)
                .volumeScore(7)
                .build();

        var pressure = policy.evaluate(21, 5, 13, 19, 78, 88, current);

        assertEquals(5, pressure.trendDrop());
        assertEquals(3, pressure.structureDrop());
        assertEquals(0, pressure.momentumDrop());
        assertEquals(2, pressure.trendPressure());
        assertEquals(0, pressure.momentumPressure());
    }
}

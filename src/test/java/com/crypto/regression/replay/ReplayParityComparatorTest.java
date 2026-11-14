package com.crypto.regression.replay;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayParityComparatorTest {
    private final ReplayParityComparator comparator = new ReplayParityComparator();

    @Test
    void reportsTheFirstDivergenceInDeterministicSnapshotOrder() {
        ReplayParityComparator.Result result = comparator.compare("BUY-1",
                Map.of("decision", "BUY", "positionPercent", 30),
                Map.of("decision", "HOLD", "positionPercent", 30));

        assertFalse(result.matched());
        assertEquals("decision", result.firstDivergenceField());
        assertEquals("BUY", result.expectedValue());
        assertEquals("HOLD", result.actualValue());
    }

    @Test
    void acceptsEqualSnapshots() {
        assertTrue(comparator.compare("EXIT-1", Map.of("reason", "STOP"), Map.of("reason", "STOP")).matched());
    }
}

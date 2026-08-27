package com.crypto.execution.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionReplayScopeModeTest {
    @Test
    void defaultReplayScopeIsProductionParity() {
        ExecutionReplayScope scope = new ExecutionReplayScope();
        try (ExecutionReplayScope.Scope ignored = scope.open(1L, List.of(), o -> {})) {
            assertTrue(scope.productionParity());
            assertFalse(scope.experimental());
        }
    }

    @Test
    void experimentalReplayMustBeExplicit() {
        ExecutionReplayScope scope = new ExecutionReplayScope();
        try (ExecutionReplayScope.Scope ignored = scope.open(2L, List.of(), o -> {},
                ExecutionReplayScope.ReplayLogicMode.EXPERIMENTAL)) {
            assertTrue(scope.experimental());
            assertFalse(scope.productionParity());
        }
    }
}

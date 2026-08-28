package com.crypto.execution.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionReplayScopeModeTest {

    @Test
    void defaultReplayScopeIsProductionParity() {

        ExecutionReplayScope scope = new ExecutionReplayScope();

        try (ExecutionReplayScope.Scope ignored =
                     scope.open(1L, List.of(), o -> {})) {

            assertTrue(scope.productionParity());
            assertFalse(scope.experimental());
        }
    }

    @Test
    void experimentalReplayMustBeExplicit() {

        ExecutionReplayScope scope = new ExecutionReplayScope();

        try (ExecutionReplayScope.Scope ignored =
                     scope.open(
                             2L,
                             List.of(),
                             o -> {},
                             ExecutionReplayScope.ReplayLogicMode.EXPERIMENTAL
                     )) {

            assertTrue(scope.experimental());
            assertFalse(scope.productionParity());
        }
    }

    @Test
    void replayEntryConsumptionTracksExactExecutedSignalId() {

        ExecutionReplayScope scope = new ExecutionReplayScope();

        try (ExecutionReplayScope.Scope ignored =
                     scope.open(3L, List.of(), o -> {})) {

            // FIX-112A:
            // Replay consumption is tied to the exact BUY signal that executed.
            // An executed signal must not make another signal appear consumed.
            Long executedSignalId = 301L;
            Long differentSignalId = 302L;

            assertFalse(scope.entryConsumed(executedSignalId));
            assertFalse(scope.entryConsumed(differentSignalId));

            scope.markEntryConsumed(executedSignalId);

            assertTrue(scope.entryConsumed(executedSignalId));

            // A different signal remains available even when another BUY signal
            // has already executed during the same replay lifecycle.
            assertFalse(scope.entryConsumed(differentSignalId));

            // FIX-112A intentionally does not release consumption.
            // Once a signal has executed, that exact historical signal remains
            // consumed for the lifetime of this replay scope.
        }
    }
}
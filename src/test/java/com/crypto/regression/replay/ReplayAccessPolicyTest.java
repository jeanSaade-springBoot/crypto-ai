package com.crypto.regression.replay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReplayAccessPolicyTest {

    @Test
    void allowsReplayOwnedAndReadOnlyMarketEvidenceTables() {
        // FIX-156 / T156-09b: baseline persistence stays Replay-owned; market evidence is read-only.
        assertDoesNotThrow(() -> ReplayAccessPolicy.require("replay_policy_profile", ReplayAccessPolicy.Operation.REPLAY_READ));
        assertDoesNotThrow(() -> ReplayAccessPolicy.require("replay_policy_profile_detail", ReplayAccessPolicy.Operation.REPLAY_READ));
        assertDoesNotThrow(() -> ReplayAccessPolicy.require("replay_fixture", ReplayAccessPolicy.Operation.REPLAY_INSERT));
        assertDoesNotThrow(() -> ReplayAccessPolicy.require("replay_run_output", ReplayAccessPolicy.Operation.REPLAY_INSERT));
        assertDoesNotThrow(() -> ReplayAccessPolicy.require("replay_decision_trace", ReplayAccessPolicy.Operation.REPLAY_INSERT));
        assertDoesNotThrow(() -> ReplayAccessPolicy.require("replay_run_certificate", ReplayAccessPolicy.Operation.REPLAY_INSERT));
        assertDoesNotThrow(() -> ReplayAccessPolicy.require("replay_price_coverage", ReplayAccessPolicy.Operation.REPLAY_INSERT));
        assertDoesNotThrow(() -> ReplayAccessPolicy.require("replay_position", ReplayAccessPolicy.Operation.REPLAY_UPDATE));
        assertDoesNotThrow(() -> ReplayAccessPolicy.require("replay_execution", ReplayAccessPolicy.Operation.REPLAY_INSERT));
        assertDoesNotThrow(() -> ReplayAccessPolicy.require("replay_position_management", ReplayAccessPolicy.Operation.REPLAY_INSERT));
        assertDoesNotThrow(() -> ReplayAccessPolicy.require("replay_candidate_recovery_ledger", ReplayAccessPolicy.Operation.REPLAY_INSERT));
        assertDoesNotThrow(() -> ReplayAccessPolicy.require("analysis_test_run", ReplayAccessPolicy.Operation.REPLAY_READ));
        assertDoesNotThrow(() -> ReplayAccessPolicy.require("trade_signal_test", ReplayAccessPolicy.Operation.REPLAY_READ));
        assertDoesNotThrow(() -> ReplayAccessPolicy.require("candle", ReplayAccessPolicy.Operation.MARKET_EVIDENCE_READ));
        assertDoesNotThrow(() -> ReplayAccessPolicy.require("market_price_event", ReplayAccessPolicy.Operation.MARKET_EVIDENCE_READ));
        assertDoesNotThrow(() -> ReplayAccessPolicy.require("trade_signal", ReplayAccessPolicy.Operation.MARKET_EVIDENCE_READ));
        assertDoesNotThrow(() -> ReplayAccessPolicy.require("technical_indicator", ReplayAccessPolicy.Operation.MARKET_EVIDENCE_READ));
    }

    @Test
    void allowsOnlyTheExactFix122Operations() {
        // FIX-156 / T156-09b: O-8 is statement-category specific, not a table exemption.
        assertDoesNotThrow(() -> ReplayAccessPolicy.requireFix122ActiveRunRead("fix122_evaluation", 7L));
        assertDoesNotThrow(() -> ReplayAccessPolicy.requireFix122ActiveRunInsert("fix122_evaluation", 7L));
        assertDoesNotThrow(() -> ReplayAccessPolicy.requireFix122ScopedPurge("fix122_evaluation"));
        assertDoesNotThrow(() -> ReplayAccessPolicy.requireFix122ProductionDisplayRead("fix122_evaluation"));
        assertThrows(ReplayForbiddenDependencyException.class,
                () -> ReplayAccessPolicy.require("fix122_evaluation", ReplayAccessPolicy.Operation.REPLAY_READ));
        assertThrows(ReplayForbiddenDependencyException.class,
                () -> ReplayAccessPolicy.requireFix122ActiveRunRead("fix122_evaluation", 0L));
    }

    @Test
    void rejectsAccountAndUnregisteredTablesAndOperations() {
        // FIX-156 / T156-09b: a new path must fail closed until explicitly registered.
        assertThrows(ReplayForbiddenDependencyException.class,
                () -> ReplayAccessPolicy.require("wallet_settings", ReplayAccessPolicy.Operation.MARKET_EVIDENCE_READ));
        assertThrows(ReplayForbiddenDependencyException.class,
                () -> ReplayAccessPolicy.require("wallet_trade", ReplayAccessPolicy.Operation.REPLAY_READ));
        assertThrows(ReplayForbiddenDependencyException.class,
                () -> ReplayAccessPolicy.require("wallet_position_test", ReplayAccessPolicy.Operation.REPLAY_UPDATE));
        assertThrows(ReplayForbiddenDependencyException.class,
                () -> ReplayAccessPolicy.require("candle", ReplayAccessPolicy.Operation.REPLAY_UPDATE));
    }
}

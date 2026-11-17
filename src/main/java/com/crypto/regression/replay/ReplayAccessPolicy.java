package com.crypto.regression.replay;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * FIX-156: central allow-list for Replay-owned SQL names. This is deliberately
 * independent from Production repositories and must be used by Replay-local stores.
 */
public final class ReplayAccessPolicy {
    /** FIX-156: operation names are deliberately narrower than JDBC verbs. */
    public enum Operation {
        REPLAY_READ,
        REPLAY_INSERT,
        REPLAY_UPDATE,
        REPLAY_DELETE,
        MARKET_EVIDENCE_READ,
        FIX122_ACTIVE_RUN_READ,
        FIX122_ACTIVE_RUN_INSERT,
        FIX122_SCOPED_PURGE,
        FIX122_PRODUCTION_DISPLAY_READ
    }

    private static final Set<String> REPLAY_TABLES = Set.of(
            "replay_policy_profile",
            "replay_policy_profile_detail",
            "replay_fixture",
            "replay_run_output",
            "replay_decision_trace",
            "replay_run_certificate",
            "replay_price_coverage",
            "replay_position",
            "replay_execution",
            "replay_position_management",
            "replay_candidate_recovery_ledger",
            "analysis_test_run",
            "trade_signal_test");

    /** FIX-156: immutable historical evidence only; no account or wallet table belongs here. */
    private static final Set<String> MARKET_EVIDENCE_TABLES = Set.of(
            "candle",
            "market_price_event",
            "trade_signal",
            "technical_indicator");

    private static final Map<Operation, Set<String>> FIX122_ALLOWED = Map.of(
            Operation.FIX122_ACTIVE_RUN_READ, Set.of("fix122_evaluation"),
            Operation.FIX122_ACTIVE_RUN_INSERT, Set.of("fix122_evaluation"),
            Operation.FIX122_SCOPED_PURGE, Set.of("fix122_evaluation"),
            Operation.FIX122_PRODUCTION_DISPLAY_READ, Set.of("fix122_evaluation"));

    private ReplayAccessPolicy() {
    }

    public static void requireReplayTable(String table) {
        require(table, Operation.REPLAY_READ);
    }

    /**
     * FIX-156 / T156-09b: every Replay SQL operation must declare its approved
     * category. This is intentionally not a generic shared-datasource interceptor.
     */
    public static void require(String table, Operation operation) {
        String normalized = table == null ? "" : table.toLowerCase(Locale.ROOT);
        if (operation == null) deny(table, null);
        boolean allowed = switch (operation) {
            case REPLAY_READ, REPLAY_INSERT, REPLAY_UPDATE, REPLAY_DELETE -> REPLAY_TABLES.contains(normalized);
            case MARKET_EVIDENCE_READ -> MARKET_EVIDENCE_TABLES.contains(normalized);
            case FIX122_ACTIVE_RUN_READ, FIX122_ACTIVE_RUN_INSERT,
                 FIX122_SCOPED_PURGE, FIX122_PRODUCTION_DISPLAY_READ -> FIX122_ALLOWED
                    .getOrDefault(operation, Set.of()).contains(normalized);
        };
        if (!allowed) deny(table, operation);
    }

    /** FIX-156: make the O-8 predicates explicit at each JDBC call site. */
    public static void requireFix122ActiveRunRead(String table, long runId) {
        if (runId <= 0) deny(table, Operation.FIX122_ACTIVE_RUN_READ);
        require(table, Operation.FIX122_ACTIVE_RUN_READ);
    }

    public static void requireFix122ActiveRunInsert(String table, long runId) {
        if (runId <= 0) deny(table, Operation.FIX122_ACTIVE_RUN_INSERT);
        require(table, Operation.FIX122_ACTIVE_RUN_INSERT);
    }

    public static void requireFix122ScopedPurge(String table) {
        require(table, Operation.FIX122_SCOPED_PURGE);
    }

    public static void requireFix122ProductionDisplayRead(String table) {
        require(table, Operation.FIX122_PRODUCTION_DISPLAY_READ);
    }

    private static void deny(String table, Operation operation) {
        throw new ReplayForbiddenDependencyException("table=" + table + ", operation=" + operation);
    }
}

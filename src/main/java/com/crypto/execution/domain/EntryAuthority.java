package com.crypto.execution.domain;

/**
 * FIX-091 / Fix 5: maximum pre-wallet exposure authority.
 * This enum never means "execute now"; ExecutionIntelligenceService and validateBuy()
 * remain authoritative for whether a BUY may actually execute.
 */
public enum EntryAuthority {
    BLOCKED,
    TRANSITION_PROBE,
    REDUCED_ENTRY,
    NORMAL_ENTRY
}

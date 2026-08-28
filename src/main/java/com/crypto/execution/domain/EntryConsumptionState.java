package com.crypto.execution.domain;

/**
 * FIX-112A shared vocabulary for whether a bullish opportunity has produced a
 * real position. PARTIALLY_CONSUMED is reserved for future scale-in policy;
 * FIX-112A treats any consumed state as protected continuation.
 */
public enum EntryConsumptionState {
    NOT_CONSUMED,
    PARTIALLY_CONSUMED,
    CONSUMED
}

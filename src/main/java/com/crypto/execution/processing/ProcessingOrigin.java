package com.crypto.execution.processing;

/** FIX-127: only eligible automatic work may be recovered by the background scan. */
public enum ProcessingOrigin {
    WORKER, RECOVERY, STARTUP, EXPLICIT;
    public boolean automaticRecovery() { return this == WORKER || this == RECOVERY; }
}

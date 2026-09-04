package com.crypto.position.service;

/**
 * FIX-11T: persisted lifecycle for Near-TP Failure Protection.
 *
 * The state is position-scoped.  It must never be stored on a singleton service because
 * Production and Replay can evaluate several independent positions over the lifetime of
 * the application/run.
 */
public enum NearTpState {
    INACTIVE,
    NEAR_TP_ARMED,
    NEAR_TP_REJECTION_DETECTED,
    NEAR_TP_FAILURE_CONFIRMED,
    NEAR_TP_PARTIAL_HARVESTED
}

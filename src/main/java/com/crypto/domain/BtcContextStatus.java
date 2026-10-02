package com.crypto.domain;

public enum BtcContextStatus {
    CONFIRMED,
    NEUTRAL,
    CONFLICT,
    STRONG_CONFLICT,
    LEARNING,
    NOT_APPLICABLE,
    UNAVAILABLE,
    // FIX-141: missing, stale, future-dated, or unknown-candle-time context. Always
    // blocks new entries when BTC context is required, independent of LEARNING/correlation.
    STALE_CONTEXT
}

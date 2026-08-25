package com.crypto.domain;

public enum LiquidityContextStatus {
    BULLISH_SUPPORT,
    BALANCED,
    BEARISH_PRESSURE,
    TARGET_BLOCKED,
    WALL_WEAKENING,
    STOP_EXPOSED,
    THIN_LIQUIDITY,
    LEARNING,
    // FIX-091: live order-book sampling exists but is not yet mature enough to authorize a fresh entry.
    INSUFFICIENT_DATA_HOLD,
    UNAVAILABLE,
    DISABLED
}

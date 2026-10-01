package com.crypto.execution.processing;
/** FIX-140: durable retry already recorded; the delivery dispatcher owns resumption. */
public final class SymbolLockRetryScheduled extends RuntimeException {
    public SymbolLockRetryScheduled(long signal) { super("FIX-140 safe symbol-lock retry scheduled for signal="+signal); }
}

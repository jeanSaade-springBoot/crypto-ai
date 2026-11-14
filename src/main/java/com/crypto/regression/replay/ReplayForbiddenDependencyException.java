package com.crypto.regression.replay;

/** FIX-156: Replay fails closed instead of falling through to Production state. */
public final class ReplayForbiddenDependencyException extends IllegalStateException {
    public ReplayForbiddenDependencyException(String dependency) {
        super("FIX-156 Replay forbidden dependency: " + dependency);
    }
}

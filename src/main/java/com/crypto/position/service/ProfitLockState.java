package com.crypto.position.service;

/**
 * FIX-118 persisted Dynamic Profit Lock lifecycle.
 *
 * INACTIVE            - protection has not yet been earned for the current target geometry.
 * ACTIVE              - the persisted lock is executable.
 * TP_EXTENSION_REBASE - protection was earned before TP extension, but the old lock is
 *                       temporarily non-executable until the new target geometry reaches
 *                       the configured activation threshold again.
 */
public enum ProfitLockState {
    INACTIVE,
    ACTIVE,
    TP_EXTENSION_REBASE
}

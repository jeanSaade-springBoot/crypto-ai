package com.crypto.regression.service;

/**
 * FIX-090: internal cooperative-cancellation signal used only by isolated Replay/Test code.
 * It is never used by Production analysis or wallet execution.
 */
final class ReplayCancellationException extends RuntimeException {
    ReplayCancellationException(long runId) {
        super("Replay stop requested for run #" + runId);
    }
}

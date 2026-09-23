package com.crypto.client.binance.websocket;

import java.time.Instant;

/** FIX-131: a permit spans the entire synchronous kline callback, including commit.
 * Revocation stops admission, never interrupts wallet work already admitted. */
public final class GenerationGate {
    public enum State { PENDING, ADMITTING, REVOKED }
    private State state = State.PENDING;
    private int inFlight;
    private Instant drainedAt;
    public synchronized boolean activate() {
        if (state != State.PENDING) return false;
        state = State.ADMITTING;
        return true;
    }
    public synchronized Permit tryAdmit() {
        if (state != State.ADMITTING) return null;
        inFlight++;
        return new Permit();
    }
    public synchronized void revoke() {
        state = State.REVOKED;
        checkDrained();
    }
    private void checkDrained() {
        if (state == State.REVOKED && inFlight == 0 && drainedAt == null) drainedAt = Instant.now();
    }
    public synchronized boolean isDrained() { return drainedAt != null; }
    public synchronized int inFlight() { return inFlight; }
    public synchronized State state() { return state; }
    public synchronized Instant drainedAt() { return drainedAt; }
    public final class Permit implements AutoCloseable {
        private boolean released;
        @Override public void close() {
            synchronized (GenerationGate.this) {
                if (released) return;
                released = true;
                inFlight--;
                checkDrained(); // Last release after revoke is the normal busy-drain case.
            }
        }
    }
}

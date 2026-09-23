package com.crypto.client.binance.websocket;

/** FIX-131: a caller timeout does not cancel the manager's cleanup. */
public class WebSocketReconnectException extends IllegalStateException {
    public enum Reason { CONNECTION_FAILED, DRAIN_TIMEOUT, CLOSE_TIMEOUT, WAIT_TIMEOUT, BUSY, STOPPED }
    private final Reason reason;
    private final String phase;
    public WebSocketReconnectException(Reason reason, String phase) {
        super("[FIX-131] " + reason + "; lifecycle=" + phase);
        this.reason = reason;
        this.phase = phase;
    }
    public Reason getReason() { return reason; }
    public String getPhase() { return phase; }
}

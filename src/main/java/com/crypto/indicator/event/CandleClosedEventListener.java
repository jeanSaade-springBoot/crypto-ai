package com.crypto.indicator.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Receives the committed closed-candle event and hands it to FIX-043's ordered async dispatcher.
 *
 * IMPORTANT: keep this listener intentionally tiny. Before FIX-043 it performed the complete
 * indicator -> signal -> wallet chain synchronously inside AFTER_COMMIT. Production evidence on
 * 22 Aug 2026 showed ~97-100% candle persistence but only ~17-20% 1m indicator/signal coverage;
 * the heavy listener was effectively letting the five-minute recovery scheduler become the
 * analysis clock.
 *
 * We do NOT put @Async directly on this method because a generic async pool can reorder adjacent
 * candles (for example 12:02 could start before 12:01). The dispatcher returns immediately to the
 * Binance ingestion path while preserving FIFO order per symbol+interval and parallelism across
 * independent streams.
 */
@Component
public class CandleClosedEventListener {

    private final CandleAnalysisDispatcher dispatcher;

    public CandleClosedEventListener(CandleAnalysisDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(CandleClosedEvent event) {
        dispatcher.submit(event);
    }
}

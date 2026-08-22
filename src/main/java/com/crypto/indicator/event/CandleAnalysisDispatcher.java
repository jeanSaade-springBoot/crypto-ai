package com.crypto.indicator.event;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * FIX-043 ordered asynchronous dispatcher for production candle-close analysis.
 *
 * Why this exists instead of plain @Async:
 * - Binance ingestion must not wait for expensive technical/scoring/wallet work.
 * - Different symbols/timeframes SHOULD run in parallel so 15+ configured symbols can keep up.
 * - Adjacent candles of the SAME symbol/timeframe MUST remain FIFO. Out-of-order analysis could
 *   mutate opportunity memory or wallet state with 12:02 before 12:01 and create a new class of
 *   false entry/exit bug while trying to solve the late-entry incident.
 *
 * Each symbol+interval gets a lightweight SerialExecutor lane backed by the bounded shared
 * candleAnalysisExecutor. The key set is naturally bounded by configured market streams.
 */
@Component
public class CandleAnalysisDispatcher {

    private final Executor backingExecutor;
    private final CandleClosedAnalysisWorker worker;
    private final Map<String, SerialExecutor> lanes = new ConcurrentHashMap<>();

    public CandleAnalysisDispatcher(
            @Qualifier("candleAnalysisExecutor") Executor backingExecutor,
            CandleClosedAnalysisWorker worker
    ) {
        this.backingExecutor = backingExecutor;
        this.worker = worker;
    }

    public void submit(CandleClosedEvent event) {
        if (event == null) {
            return;
        }
        lanes.computeIfAbsent(key(event), ignored -> new SerialExecutor(backingExecutor))
                .execute(() -> worker.process(event));
    }

    private String key(CandleClosedEvent event) {
        String symbol = event.symbol() == null ? "" : event.symbol().trim().toUpperCase(Locale.ROOT);
        String interval = event.intervalCode() == null ? "" : event.intervalCode().trim();
        return symbol + "|" + interval;
    }

    /** FIFO wrapper adapted to the JDK Executor contract; one active task per stream lane. */
    static final class SerialExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private final Executor executor;
        private Runnable active;

        SerialExecutor(Executor executor) {
            this.executor = executor;
        }

        @Override
        public synchronized void execute(Runnable command) {
            tasks.offer(() -> {
                try {
                    command.run();
                } finally {
                    scheduleNext();
                }
            });
            if (active == null) {
                scheduleNext();
            }
        }

        private synchronized void scheduleNext() {
            if ((active = tasks.poll()) != null) {
                executor.execute(active);
            }
        }
    }
}

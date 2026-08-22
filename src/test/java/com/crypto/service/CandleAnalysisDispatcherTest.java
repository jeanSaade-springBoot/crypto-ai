package com.crypto.service;

import com.crypto.indicator.event.CandleAnalysisDispatcher;
import com.crypto.indicator.event.CandleClosedAnalysisWorker;
import com.crypto.indicator.event.CandleClosedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class CandleAnalysisDispatcherTest {

    @Test
    void fix043SerialExecutorPreservesFifoWithinOneMarketStream() {
        List<Runnable> submitted = new ArrayList<>();
        Executor capturingExecutor = submitted::add;
        CandleClosedAnalysisWorker worker = mock(CandleClosedAnalysisWorker.class);
        CandleAnalysisDispatcher dispatcher = new CandleAnalysisDispatcher(capturingExecutor, worker);

        // The real worker is mocked here; the purpose is to prove submission of the second task
        // is held behind the first task on the same symbol/timeframe lane.
        dispatcher.submit(new CandleClosedEvent("ACEUSDT", "1m", Instant.parse("2026-08-22T10:00:00Z")));
        dispatcher.submit(new CandleClosedEvent("ACEUSDT", "1m", Instant.parse("2026-08-22T10:01:00Z")));

        assertEquals(1, submitted.size(), "Only the first same-lane task may reach the backing executor");
        submitted.remove(0).run();
        assertEquals(1, submitted.size(), "Completing the first task must release exactly the next FIFO task");
    }
}

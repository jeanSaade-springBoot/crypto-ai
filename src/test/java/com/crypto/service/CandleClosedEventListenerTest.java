package com.crypto.service;

import com.crypto.indicator.event.CandleAnalysisDispatcher;
import com.crypto.indicator.event.CandleClosedEvent;
import com.crypto.indicator.event.CandleClosedEventListener;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CandleClosedEventListenerTest {

    @Test
    void fix043ListenerOnlyDispatchesCommittedEventAndDoesNotRunHeavyTradingWorkInline() {
        CandleAnalysisDispatcher dispatcher = mock(CandleAnalysisDispatcher.class);
        CandleClosedEventListener listener = new CandleClosedEventListener(dispatcher);
        CandleClosedEvent event = new CandleClosedEvent(
                "BTCUSDT", "1m", Instant.parse("2026-08-22T10:00:00Z"));

        listener.handle(event);

        verify(dispatcher).submit(event);
    }
}

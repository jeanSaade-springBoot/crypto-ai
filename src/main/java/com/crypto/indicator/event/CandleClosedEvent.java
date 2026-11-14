package com.crypto.indicator.event;
import java.time.Instant;
/** FIX-153: runtime carries persisted close time; historical/shared callers retain their explicit clocks. */
public record CandleClosedEvent(String symbol,String intervalCode,Instant openTime,Instant closeTime) {
    public CandleClosedEvent(String symbol,String intervalCode,Instant openTime){this(symbol,intervalCode,openTime,null);}
}

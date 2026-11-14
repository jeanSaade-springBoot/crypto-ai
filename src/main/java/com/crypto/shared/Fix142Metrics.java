package com.crypto.shared;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.*;
import com.crypto.infrastructure.diagnostics.JdbcTimingDataSource;

/** FIX-142: actual events, never inferred throughput from rate-limited dispatch logs.
 * Keys are fixed operation/reason names; bounded storage rejects accidental high cardinality. */
final class Fix142Metrics {
    private static final Map<String,LongAdder> counters=new ConcurrentHashMap<>();
    static void count(String name) {
        LongAdder value=counters.get(name);
        if(value==null)synchronized(counters){value=counters.get(name);if(value==null){String key=counters.size()<256?name:"OTHER";value=counters.computeIfAbsent(key,ignored->new LongAdder());}}
        value.increment();
    }
    static Map<String,Long> snapshot(){Map<String,Long> result=new TreeMap<>();counters.forEach((key,value)->result.put(key,value.sum()));return result;}
    static PhaseSpan phase(String symbol,long event,int phase){return new PhaseSpan(symbol,event,phase);}
    static final class PhaseSpan implements AutoCloseable {
        private final String symbol;private final long event;private final int phase;
        private final long started=System.nanoTime();private final Instant startUtc=Instant.now();
        private boolean returned;
        PhaseSpan(String symbol,long event,int phase){this.symbol=symbol;this.event=event;this.phase=phase;}
        void returned(){returned=true;}
        @Override public void close() {
            long elapsed=(System.nanoTime()-started)/1_000_000;
            count("PHASE_"+phase+(returned?"_RETURNED":"_THREW"));
            JdbcTimingDataSource.diagnostic("[FIX-142][PHASE_DURATION] symbol="+symbol+" event="+event+" phase="+phase+" startUtc="+startUtc+" endUtc="+Instant.now()+" elapsedMs="+elapsed+" transactionReturned="+returned+" resolutionSource=JDBC_TRANSACTION_EVENT");
        }
    }
}

package com.crypto.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** FIX-140: count every context evaluation; sampling applies only to summary output.
 * No database queries, per-symbol tags, or trading-rule changes are introduced. */
@Component
public class SignalContextMetrics {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(SignalContextMetrics.class);
    private final ConcurrentHashMap<String,AtomicLong> counts=new ConcurrentHashMap<>();
    private final AtomicLong nextLog=new AtomicLong();
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private io.micrometer.core.instrument.MeterRegistry meters;
    public void record(String kind,String interval,int required,int present) {
        String coverage=present==0?"MISSING":present<required?"PARTIAL":"FULL";
        counts.computeIfAbsent(kind+"|"+interval+"|"+coverage,k->new AtomicLong()).incrementAndGet();
        if(meters!=null)meters.counter("signal.context.evaluations","kind",kind,"interval",interval,"coverage",coverage).increment();
        long now=System.nanoTime(),previous=nextLog.get();
        if(now>=previous && nextLog.compareAndSet(previous,now+java.util.concurrent.TimeUnit.SECONDS.toNanos(30)))
            log.info("[FIX-140][CONTEXT_COVERAGE_TOTALS] counts={}; cumulative evaluations, not sampled throughput",counts);
    }

    /** FIX-141: record() alone counts snapshot PRESENCE only, so a stale snapshot counts
     * identically to a fresh one (both "present"). This records the freshness outcome
     * (FRESH/STALE/MISSING) as its own series so degradation is visible on its own, not
     * folded into presence counts. Call alongside record(), not instead of it. */
    public void recordFreshness(String kind,String interval,String outcome) {
        counts.computeIfAbsent(kind+"|"+interval+"|FRESHNESS_"+outcome,k->new AtomicLong()).incrementAndGet();
        if(meters!=null)meters.counter("signal.context.freshness","kind",kind,"interval",interval,"outcome",outcome).increment();
    }
}

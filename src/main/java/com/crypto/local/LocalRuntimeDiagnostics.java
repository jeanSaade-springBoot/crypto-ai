package com.crypto.local;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

/** FIX-153: finite categories; per-tick details are DEBUG, one aggregate every minute.
 * UTC is authoritative; KSA is explicitly an offset presentation. Counters are since process start.
 * callbackMs includes SQL, mapping and waits: it is never labelled server SQL or commit latency. */
@Component
public class LocalRuntimeDiagnostics {
    private static final Logger log=LoggerFactory.getLogger(LocalRuntimeDiagnostics.class);
    public enum Event { INPUT_COMMITTED, INPUT_FAILED_OR_UNCONFIRMED, PIPELINE_FAILED_OR_UNCONFIRMED, PROTECTION_APPLIED,
        PROTECTION_REJECTED, PROTECTION_PENDING_RESOLVED, PROTECTION_FAILED_OR_UNCONFIRMED,
        ADMITTED, REPLACED, OVERFLOW, DUPLICATE_ADMISSION, SUBMISSION_REJECTED, DUPLICATE_CLOSE, LATE_OPEN_IGNORED,
        ANALYSIS_ATTEMPTED, ANALYSIS_FINISHED, DETECTOR_ADMITTED, DETECTOR_FAILED }
    private static final Map<Event,LongAdder> counts=new EnumMap<>(Event.class);
    private static final ConcurrentMap<String,Instant> lastProtection=new ConcurrentHashMap<>();
    private static final ConcurrentMap<String,Instant> lastProtectionCompletion=new ConcurrentHashMap<>();
    private static final ConcurrentMap<String,Instant> lastAnalysis=new ConcurrentHashMap<>();
    static {for(Event e:Event.values())counts.put(e,new LongAdder());}
    public static void count(Event e){counts.get(e).increment();}
    public static void protection(String symbol,Instant at){lastProtection.put(symbol,at);lastProtectionCompletion.put(symbol,Instant.now());}
    public static void analysis(String lane,Instant at){lastAnalysis.put(lane,at);}
    public static Map<String,Object> snapshot(){
        Map<String,Object> m=new LinkedHashMap<>();Map<String,Long> c=new LinkedHashMap<>();
        counts.forEach((k,v)->c.put(k.name(),v.sum()));m.put("countersSinceStart",c);
        m.put("lastAcceptedProtectionObservationUtc",new TreeMap<>(lastProtection));
        m.put("lastProtectionCompletionObservedUtc",new TreeMap<>(lastProtectionCompletion));
        m.put("lastAnalysisAttemptUtc",new TreeMap<>(lastAnalysis));return m;
    }
    @Scheduled(fixedDelayString="${local-candle.diagnostics-summary-ms:60000}")
    public void summary(){Instant now=Instant.now();log.info("[FIX-153][RUNTIME_SUMMARY] observedUtc={}, observedKsa={}, {}",
        now,now.atOffset(ZoneOffset.ofHours(3)),snapshot());}
}

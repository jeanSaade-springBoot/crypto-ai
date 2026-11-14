package com.crypto.indicator.event;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.crypto.local.LocalRuntimeDiagnostics;
import static com.crypto.local.LocalRuntimeDiagnostics.Event.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** FIX-153: one running task per lane. 1m keeps latest pending; higher TFs retain
 * a bounded FIFO. Reservations and admission share the lane monitor, including
 * detector idle check. No analysis executes on ingestion threads at saturation. */
@Component
public class CandleAnalysisDispatcher {
    private static final Logger log=LoggerFactory.getLogger(CandleAnalysisDispatcher.class);
    private final Executor executor;
    private final CandleClosedAnalysisWorker worker;
    private final Map<String,Lane> lanes=new ConcurrentHashMap<>();
    private volatile boolean stopped;
    public CandleAnalysisDispatcher(@Qualifier("candleAnalysisExecutor") Executor executor,CandleClosedAnalysisWorker worker){this.executor=executor;this.worker=worker;}
    public void submit(CandleClosedEvent event){admit(event,false,()->true);}
    /** Check exact candle evidence under the detector's existing in-process lock.
     * Check+reserve+enqueue is atomic with live admission; detector never replaces pending live work. */
    public boolean submitDetected(CandleClosedEvent event,java.util.function.BooleanSupplier missing){return admit(event,true,missing);}
    public boolean idle(String symbol,String interval){Lane l=lanes.get(symbol+"|"+interval);if(l==null)return !stopped;synchronized(l){return !stopped&&l.active==null&&l.pending.isEmpty();}}
    /** A detector reservation is in-flight admission work, even before a task exists. */
    public boolean idleAll(){return !stopped && lanes.values().stream().allMatch(l->{synchronized(l){return l.active==null&&l.pending.isEmpty()&&l.reserved.isEmpty()&&l.deferredLive.isEmpty();}});}
    private boolean admit(CandleClosedEvent e,boolean detector,java.util.function.BooleanSupplier missing){
        if(e==null || stopped)return false;
        String key=e.symbol().trim().toUpperCase(Locale.ROOT)+"|"+e.intervalCode();
        Lane lane=lanes.computeIfAbsent(key,k->new Lane());
        if(detector){
            synchronized(lane){
                if(stopped || lane.active!=null || !lane.pending.isEmpty() || !lane.reserved.add(e.openTime())) return false;
            }
            // FIX-153: reserve first, but never hold admission's monitor across database I/O.
            boolean absent;
            try {absent=missing.getAsBoolean();}
            catch(RuntimeException failure){
                CandleClosedEvent handoff;
                synchronized(lane){
                    handoff=lane.deferredLive.remove(e.openTime());
                    if(handoff!=null)enqueueReserved(lane,key,handoff,false);
                    else lane.reserved.remove(e.openTime());
                }
                throw failure;
            }
            synchronized(lane){
                // A committed live close that arrived during the detector's database
                // check owns admission. Transfer it to the queue while retaining
                // its reservation and the lane monitor: no second SQL check and no
                // interleaving admission window can lose it.
                CandleClosedEvent liveHandoff=lane.deferredLive.remove(e.openTime());
                if(liveHandoff!=null)return enqueueReserved(lane,key,liveHandoff,false);
                if(!absent || stopped || lane.active!=null || !lane.pending.isEmpty()){
                    lane.reserved.remove(e.openTime());return false;
                }
            }
        }
        synchronized(lane){
            if(stopped || (detector && (lane.active!=null || !lane.pending.isEmpty()))){if(detector)lane.reserved.remove(e.openTime());return false;}
            if(!detector && lane.reserved.contains(e.openTime())){
                // Detector owns the temporary reservation only. Preserve the exact
                // committed event and let the detector hand it straight back once
                // its read completes; no live close is discarded in this window.
                lane.deferredLive.putIfAbsent(e.openTime(),e);return true;
            }
            if(!detector && !lane.reserved.add(e.openTime())){LocalRuntimeDiagnostics.count(DUPLICATE_ADMISSION);return false;}
            Task task=new Task(e,detector);
            if("1m".equals(e.intervalCode()) && !lane.pending.isEmpty()){
                Task previous=lane.pending.peekLast();
                // A delayed older callback cannot replace newer pending work.
                if(!e.openTime().isAfter(previous.event.openTime())){lane.reserved.remove(e.openTime());LocalRuntimeDiagnostics.count(OVERFLOW);return false;}
                previous=lane.pending.removeLast();lane.reserved.remove(previous.event.openTime());lane.deferredLive.remove(previous.event.openTime());LocalRuntimeDiagnostics.count(REPLACED);
                log.info("[FIX-153][PENDING_REPLACED] lane={}, skippedOpenUtc={}, keptOpenUtc={}, businessExecuted=false",key,previous.event.openTime(),e.openTime());
            }else if(lane.pending.size()>=4){lane.reserved.remove(e.openTime());LocalRuntimeDiagnostics.count(OVERFLOW);
                log.info("[FIX-153][LANE_OVERFLOW] lane={}, rejectedOpenUtc={}, pending={}, policy=BOUNDED_FIFO",key,e.openTime(),lane.pending.size());return false;}
            lane.pending.add(task);LocalRuntimeDiagnostics.count(ADMITTED);
            if(detector)LocalRuntimeDiagnostics.count(DETECTOR_ADMITTED);
            return schedule(lane,key);
        }
    }
    /** Caller holds lane's monitor and has already retained e.openTime() in reserved. */
    private boolean enqueueReserved(Lane lane,String key,CandleClosedEvent e,boolean detector){
        if(stopped){lane.reserved.remove(e.openTime());return false;}
        Task task=new Task(e,detector);
        if("1m".equals(e.intervalCode()) && !lane.pending.isEmpty()){
            Task previous=lane.pending.peekLast();
            if(!e.openTime().isAfter(previous.event.openTime())){lane.reserved.remove(e.openTime());LocalRuntimeDiagnostics.count(OVERFLOW);return false;}
            previous=lane.pending.removeLast();lane.reserved.remove(previous.event.openTime());lane.deferredLive.remove(previous.event.openTime());LocalRuntimeDiagnostics.count(REPLACED);
            log.info("[FIX-153][PENDING_REPLACED] lane={}, skippedOpenUtc={}, keptOpenUtc={}, businessExecuted=false",key,previous.event.openTime(),e.openTime());
        }else if(lane.pending.size()>=4){lane.reserved.remove(e.openTime());LocalRuntimeDiagnostics.count(OVERFLOW);
            log.info("[FIX-153][LANE_OVERFLOW] lane={}, rejectedOpenUtc={}, pending={}, policy=BOUNDED_FIFO",key,e.openTime(),lane.pending.size());return false;}
        lane.pending.add(task);LocalRuntimeDiagnostics.count(ADMITTED);
        if(detector)LocalRuntimeDiagnostics.count(DETECTOR_ADMITTED);
        return schedule(lane,key);
    }
    private boolean schedule(Lane lane,String key){
        if(stopped)return false;
        if(lane.active!=null)return true;
        Task task=lane.pending.pollFirst();if(task==null)return true;lane.active=task;
        try {executor.execute(()->{
            synchronized(lane){if(stopped || lane.active!=task)return;}
            long start=System.nanoTime();LocalRuntimeDiagnostics.count(ANALYSIS_ATTEMPTED);LocalRuntimeDiagnostics.analysis(key,Instant.now());
            String outcome="THREW_OR_UNCONFIRMED";
            try {outcome=worker.processShared(task.event,task.event.closeTime(),true);}
            catch(RuntimeException failure){log.error("[FIX-153][ANALYSIS_TASK_FAILED] lane={}, openUtc={}",key,task.event.openTime(),failure);}
            finally {
                LocalRuntimeDiagnostics.count(ANALYSIS_FINISHED);
                log.debug("[FIX-153][ANALYSIS_TASK_RETURNED] lane={}, openUtc={}, outcome={}, callbackMs={}; task return alone is not wallet commit evidence",key,task.event.openTime(),outcome,(System.nanoTime()-start)/1_000_000);
                synchronized(lane){lane.reserved.remove(task.event.openTime());lane.deferredLive.remove(task.event.openTime());lane.active=null;schedule(lane,key);}
            }
        });return true;}catch(RejectedExecutionException failure){
            // Release every queued reservation on rejection: local detector may reconsider gaps.
            LocalRuntimeDiagnostics.count(SUBMISSION_REJECTED);lane.active=null;lane.reserved.remove(task.event.openTime());
            lane.deferredLive.remove(task.event.openTime());
            for(Task queued:lane.pending){lane.reserved.remove(queued.event.openTime());lane.deferredLive.remove(queued.event.openTime());}lane.pending.clear();
            log.info("[FIX-153][EXECUTOR_REJECTED] lane={}, openUtc={}, ranOnIngestionThread=false",key,task.event.openTime());
            return false;
        }
    }
    public Map<String,Object> snapshot(){Map<String,Object> result=new TreeMap<>();lanes.forEach((k,l)->{synchronized(l){result.put(k,Map.of("active",l.active!=null,"pending",l.pending.size(),"reservations",l.reserved.size()));}});return result;}
    @PreDestroy public void stop(){stopped=true;lanes.values().forEach(l->{synchronized(l){l.pending.clear();l.reserved.clear();l.deferredLive.clear();}});}
    private static class Lane {final Deque<Task> pending=new ArrayDeque<>();final Set<Instant> reserved=new HashSet<>();final Map<Instant,CandleClosedEvent> deferredLive=new HashMap<>();Task active;}
    private record Task(CandleClosedEvent event,boolean detector) {}
}

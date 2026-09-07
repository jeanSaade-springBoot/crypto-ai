package com.crypto.execution.service;

import com.crypto.domain.TradeSignal;
import com.crypto.execution.service.ExecutionIntelligenceService.ExecutionDecision;
import com.crypto.execution.service.ExecutionIntelligenceService.SetupWakeupEvaluation;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import java.util.function.Supplier;

/** FIX-122: evaluation-local history selection. Spring singleton state is thread-bound
 * and restored in finally; open-position additions never enter this scope. Replay uses
 * its own executed stops, never Production history. No wallet lock or balance writes.
 */
@Service
@Slf4j
public class StopLossEvidenceService {
    private final StopLossEvidenceStore store;
    private final ExecutionReplayScope replay;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final Clock clock;
    private final ThreadLocal<Context> active = new ThreadLocal<>();

    @Autowired
    public StopLossEvidenceService(StopLossEvidenceStore store, ExecutionReplayScope replay,
                                  ObjectMapper mapper, @Value("${trading.fix122.enabled:true}") boolean enabled) {
        this(store,replay,mapper,enabled,Clock.systemUTC());
    }

    StopLossEvidenceService(StopLossEvidenceStore store, ExecutionReplayScope replay,
                           ObjectMapper mapper, boolean enabled, Clock clock) {
        this.store=store; this.replay=replay; this.mapper=mapper; this.enabled=enabled; this.clock=clock;
    }

    private boolean enabled() { return replay.active() ? replay.fix122Enabled() : enabled; }
    private Instant now() { return replay.active() ? replay.referenceInstant() : clock.instant(); }
    private StopLossEvidencePolicy.Boundary resolve(String symbol, Instant at) {
        return replay.active() ? replay.stopBoundary(symbol, at) : store.latest(symbol, at);
    }
    public boolean hasBoundary() { return active.get()!=null && active.get().boundary!=null; }

    public ExecutionDecision evaluate(TradeSignal signal, int allocation, Supplier<ExecutionDecision> action) {
        if (allocation>0 || signal==null || signal.getGeneratedAt()==null || !enabled()) return action.get();
        return scoped(signal, () -> new SetupWakeupEvaluation(signal, action.get())).decision();
    }

    public SetupWakeupEvaluation wakeup(TradeSignal trigger, int allocation, Supplier<SetupWakeupEvaluation> action) {
        if (allocation>0 || trigger==null || trigger.getGeneratedAt()==null || !enabled()) return action.get();
        return scoped(trigger, action);
    }

    private SetupWakeupEvaluation scoped(TradeSignal trigger, Supplier<SetupWakeupEvaluation> action) {
        Context previous=active.get();
        Context c=new Context(trigger, now(), replay.active()?replay.runId():null);
        active.set(c);
        try {
            try { c.boundary=resolve(trigger.getSymbol(), c.at); }
            catch (RuntimeException error) {
                log.warn("[FIX-122] BOUNDARY_UNAVAILABLE symbol={} signalId={}", trigger.getSymbol(), trigger.getId(), error);
                c.status="BOUNDARY_UNAVAILABLE";
                ExecutionDecision d=ExecutionDecision.reject("FIX122_BOUNDARY_UNAVAILABLE",
                        "Stop history could not be checked. Entry deferred to the next normal evaluation.");
                audit(c,d,"EVALUATION");
                return new SetupWakeupEvaluation(trigger,d);
            }
            SetupWakeupEvaluation result;
            if ("1m".equals(trigger.getInterval()) && !eligible(trigger,"CURRENT_SIGNAL")) {
                result=new SetupWakeupEvaluation(trigger, ExecutionDecision.reject("FIX122_CURRENT_CANDLE_STALE",
                        "Current entry candle is not eligible after the completed stop-loss."));
            } else {
                try { result=action.get(); }
                catch (StaleCurrent ignored) {
                    result=new SetupWakeupEvaluation(c.current,ExecutionDecision.reject("FIX122_CURRENT_CANDLE_STALE",
                            "Wake-up timing candle is not eligible after the completed stop-loss."));
                }
            }
            if (result!=null && result.decision()!=null) {
                result=new SetupWakeupEvaluation(result.executionSignal(), result.decision()
                        .withStopBoundary(new StopLossEvidencePolicy.Stamp(c.boundary)));
            }
            audit(c,result==null?null:result.decision(),"EVALUATION");
            return result==null?SetupWakeupEvaluation.none():result;
        } finally {
            if(previous==null) active.remove(); else active.set(previous);
        }
    }

    public void requireCurrent(TradeSignal signal) {
        if(active.get()!=null) active.get().current=signal;
        if(!eligible(signal,"CURRENT_SIGNAL")) throw new StaleCurrent();
    }

    public boolean eligible(TradeSignal signal, String purpose) {
        Context c=active.get();
        if(c==null || c.boundary==null) return true;
        String reason=signal==null?"LINEAGE_UNRESOLVED":StopLossEvidencePolicy.exclusion(
                signal.getInterval(),signal.getCandleOpenTime(),signal.getGeneratedAt(),c.at,c.boundary);
        if(reason==null) return true;
        Map<String,Object> item=new LinkedHashMap<>();
        item.put("purpose",purpose); item.put("reason",reason);
        if(signal!=null) {
            item.put("signalId",signal.getId()); item.put("interval",signal.getInterval());
            item.put("candleOpenTime",String.valueOf(signal.getCandleOpenTime()));
            item.put("decision",String.valueOf(signal.getDecision()));
        }
        if(!c.excluded.contains(item)) c.excluded.add(item);
        return false;
    }

    /** Read again at execution-price approval. A newly committed stop invalidates the
     * old approval. This is not a repair of the separate wallet transaction race.
     */
    public ExecutionDecision recheck(TradeSignal signal, ExecutionDecision approved) {
        if(approved==null || !approved.allowed() || approved.stopBoundary()==null || !enabled()) return approved;
        Context c=new Context(signal,now(),replay.active()?replay.runId():null);
        ExecutionDecision result=approved;
        try {
            c.boundary=resolve(signal.getSymbol(),c.at);
            if(!Objects.equals(approved.stopBoundary().boundary(),c.boundary)) {
                c.status="BOUNDARY_CHANGED";
                result=ExecutionDecision.reject("FIX122_BOUNDARY_CHANGED",
                        "A stop-loss boundary changed after qualification. Wait for a fresh evaluation.", approved.evidence());
            }
        } catch(RuntimeException error) {
            c.status="BOUNDARY_UNAVAILABLE";
            result=ExecutionDecision.reject("FIX122_BOUNDARY_UNAVAILABLE",
                    "Stop history unavailable at price revalidation. Entry deferred.",approved.evidence());
            log.warn("[FIX-122] RECHECK_UNAVAILABLE symbol={}",signal.getSymbol(),error);
        }
        audit(c,result,"PRICE_RECHECK");
        return result;
    }

    private void audit(Context c,ExecutionDecision d,String stage) {
        Map<String,Object> p=new LinkedHashMap<>();
        p.put("revision","FIX-122"); p.put("source",c.runId==null?"PRODUCTION":"REPLAY");
        p.put("status",c.status!=null?c.status:c.boundary==null?"NOT_APPLICABLE":
                c.excluded.isEmpty()?"APPLIED_NO_EXCLUSIONS":"APPLIED_EXCLUSIONS");
        p.put("signalId",c.current.getId()); p.put("candleOpenTime",String.valueOf(c.current.getCandleOpenTime()));
        p.put("triggerSignalId",c.trigger.getId());
        p.put("stopExecutionId",c.boundary==null?null:c.boundary.executionId());
        p.put("stopAt",c.boundary==null?null:c.boundary.executedAt().toString());
        p.put("excluded",c.excluded);
        p.put("allowed",d!=null&&d.allowed()); p.put("route",d==null?null:d.source());
        p.put("code",d==null?"NO_WAKEUP":d.code()); p.put("explanation",d==null?null:d.explanation());
        if(d!=null && d.evidence()!=null) {
            p.put("buyCount",d.evidence().buyCount()); p.put("watchCount",d.evidence().watchCount());
            p.put("evidenceScore",d.evidence().evidenceScore()); p.put("health",d.evidence().opportunityHealth());
            p.put("positionPercent",d.positionPercent());
        }
        try { store.append(c.runId,c.trigger.getSymbol(),c.current.getId(),c.at,stage,mapper.writeValueAsString(p)); }
        catch(Exception error) { log.error("[FIX-122] DIAGNOSTIC_PERSIST_FAILED symbol={} signalId={}",
                c.trigger.getSymbol(),c.current.getId(),error); }
        if(c.boundary!=null || c.status!=null) log.info(
                "[FIX-122] {} symbol={} signalId={} stop={} excluded={} code={} allowed={}",stage,
                c.trigger.getSymbol(),c.current.getId(),c.boundary,c.excluded.size(),p.get("code"),p.get("allowed"));
    }

    private static final class StaleCurrent extends RuntimeException {}
    private static final class Context {
        final TradeSignal trigger; TradeSignal current; final Instant at; final Long runId;
        StopLossEvidencePolicy.Boundary boundary; String status;
        final List<Map<String,Object>> excluded=new ArrayList<>();
        Context(TradeSignal trigger,Instant at,Long runId) { this.trigger=trigger;this.current=trigger;this.at=at;this.runId=runId; }
    }
}

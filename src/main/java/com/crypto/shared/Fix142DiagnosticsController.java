package com.crypto.shared;

import com.crypto.infrastructure.diagnostics.JdbcTimingDataSource;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.security.Principal;
import java.util.Map;

/** FIX-142: administrative evidence review, disabled by default; no automated fencing proof. */
@RestController
@RequestMapping("/api/fix142")
public class Fix142DiagnosticsController {
    @Value("${shared-market.mode:OFF}") private String mode="OFF";
    @org.springframework.beans.factory.annotation.Autowired private com.crypto.local.LocalCandleHealth localHealth;
    private final SharedMarketConsumer consumer;
    private final Fix142HealthService health;
    @Value("${shared-market.fix142.manual-resolution-enabled:false}") private boolean resolutionEnabled;
    public Fix142DiagnosticsController(SharedMarketConsumer consumer,Fix142HealthService health){this.consumer=consumer;this.health=health;}
    // FIX-146: remains callable when no symbol is approved; no business mutation.
    @GetMapping("/readiness") public Map<String,Object> readiness(){return "OFF".equals(mode)?localHealth.snapshot():consumer.tradingReadiness();}
    @GetMapping("/database-timing") public Map<String,Object> timing(){return JdbcTimingDataSource.statistics();}
    @GetMapping("/health") public Map<String,Object> health(){
        var snapshot=new java.util.LinkedHashMap<String,Object>(health.snapshot());
        if(!"OFF".equals(mode))snapshot.put("tradingReadiness",consumer.tradingReadiness());return snapshot;
    }
    public record MarkerResolution(String symbol,long eventId,String expectedOwner,String operation,
            String observerDisposition,String fenceProof,String evidence) {}
    public record HistoricalResolution(long eventId,String expectedOwner,long savedSignal,String fenceProof,String evidence) {}
    /** Exact attempt timestamp prevents resolving a replaced attempt using an old review. */
    public record ProtectionResolution(String symbol,long eventId,String expectedOwner,
            java.time.Instant expectedStartedAt,String disposition,String transactionOutcome,
            String fenceProof,String effectEvidence) {}
    @PostMapping(value="/resolve-protection",consumes="application/json")
    public Map<String,String> protection(@RequestBody ProtectionResolution request,Principal reviewer,
            @RequestHeader("X-FIX142-Operator-Review") String explicitReview) {
        if("OFF".equals(mode)||!resolutionEnabled||!"CONFIRMED".equals(explicitReview))
            throw new IllegalStateException("Manual reviewed resolution is disabled or unconfirmed");
        if(reviewer==null)throw new IllegalStateException("Authenticated operator required");
        consumer.resolveProtection(request.symbol(),request.eventId(),request.expectedOwner(),
                request.expectedStartedAt(),request.disposition(),request.transactionOutcome(),
                reviewer.getName(),request.fenceProof(),request.effectEvidence());
        return Map.of("result","COMMITTED","operation",request.disposition());
    }
    @PostMapping(value="/resolve-historical-analysis",consumes="application/json")
    public Map<String,String> historical(@RequestBody HistoricalResolution request,Principal reviewer,
            @RequestHeader("X-FIX142-Operator-Review") String explicitReview) {
        if("OFF".equals(mode)||!resolutionEnabled||!"CONFIRMED".equals(explicitReview))throw new IllegalStateException("Manual reviewed resolution is disabled or unconfirmed");
        consumer.resolveHistoricalAnalysisOnly(request.eventId(),request.expectedOwner(),request.savedSignal(),reviewer.getName(),request.fenceProof(),request.evidence());
        return Map.of("result","COMMITTED","operation","EXCEPTIONAL_ANALYSIS_ONLY");
    }
    @PostMapping(value="/resolve-marker",consumes="application/json")
    public Map<String,String> resolve(@RequestBody MarkerResolution request,Principal reviewer,
            @RequestHeader("X-FIX142-Operator-Review") String explicitReview) {
        if("OFF".equals(mode)||!resolutionEnabled)throw new IllegalStateException("Manual marker resolution is disabled");
        // Custom header + JSON cannot be submitted by a cross-origin HTML form; no permissive CORS is added.
        if(!"CONFIRMED".equals(explicitReview))throw new IllegalArgumentException("Explicit operator review required");
        consumer.resolveMarker(request.symbol(),request.eventId(),request.expectedOwner(),request.operation(),
            request.observerDisposition(),reviewer.getName(),request.fenceProof(),request.evidence());
        return Map.of("result","COMMITTED","operation",request.operation());
    }
}
